package rpc.jms;

import clock.VectorClock;
import com.fasterxml.jackson.databind.JsonNode;
import common.ConnectionException;
import jakarta.jms.*;
import jakarta.jms.Connection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ScheduledMessage;
import rpc.Codec;
import rpc.Dispatcher;
import rpc.JsonRpcMessage;
import rpc.RpcException;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transportschicht auf Client Seite (Ersetzung der TCP Verbindung)
 */
public class JmsConnection {
    private final String owner;
    private final VectorClock clock;
    private final Codec codec;
    private Connection connection;
    private Session sendSession;
    private Session receiveSession;
    private MessageProducer producer;
    private Queue requestQueue;
    private Queue replyQueue;
    private Queue eventQueue;
    private ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private AtomicLong nextDelay;
    private Object sendLock;
    private Dispatcher notificationDispatcher;
    private AtomicBoolean closed;
    private final AtomicLong nextId = new AtomicLong();

    public JmsConnection(String brokerUrl, String owner, VectorClock clock, Codec codec) throws JMSException {
        if (brokerUrl == null || brokerUrl.isBlank() || owner == null || owner.isBlank()
            || clock == null || codec == null) throw new IllegalArgumentException("One or more Parameters are null or blank.");
        this.owner = owner;
        this.clock = clock;
        this.codec = codec;

        //Connection erzeugen
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        connection = connectionFactory.createConnection();

        //Sessions anlegen
        sendSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        receiveSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        //Queues erzeugen
        requestQueue = sendSession.createQueue("request queue");
        replyQueue = sendSession.createQueue("reply queue");
        eventQueue = sendSession.createQueue("event queue");

        //Ziel wird später angegeben
        producer = sendSession.createProducer(null);

        MessageConsumer replyConsumer = sendSession.createConsumer(replyQueue);
        //replyConsumer.setMessageListener(this::onReply);

        try {
            connection.start();
        } catch (JMSException e) {
            connection.close();
            throw new ConnectionException("Connection failed.");
        }
    }

    public void setNotificationDispatcher(Dispatcher dispatcher) throws JMSException {
        if (dispatcher == null) throw new IllegalArgumentException("Dispatcher is null");
        notificationDispatcher = dispatcher;
        MessageConsumer receiveConsumer= receiveSession.createConsumer(eventQueue);
        //receiveConsumer.setMessageListener(this::onNotification);
    }

    /**
     * Asynchroner RPC-Aufruf über den Broker.
     * Die JSON-RPC-id ist ein Zähler pro Client und dient zugleich als JMSCorrelationID.
     */
    public <T> CompletableFuture<T> callAsync(String method, Map<String, Object> params, Class<T> resultType) {
        if (closed.get()) {
            throw new ConnectionException("Connection is closed");
        }
        if (method == null || method.isBlank() || resultType == null) {
            throw new IllegalArgumentException("method or resultType is null/blank");
        }

        long id = nextId.incrementAndGet();
        String corrId = String.valueOf(id);

        CompletableFuture<JsonNode> raw = new CompletableFuture<>();
        pending.put(corrId, raw);                         // VOR dem Senden eintragen

        try {
            // Map -> JsonNode über den Codec; null-Map -> kein params-Feld
            JsonNode paramsNode = (params == null) ? null : codec.toNode(params);
            JsonRpcMessage request = new JsonRpcMessage(method, paramsNode, null, null, id);
            String json = codec.encode(request);

            synchronized (sendLock) {
                Map<String, Integer> before = clock.snapshot();
                clock.tick();
                Map<String, Integer> after = clock.snapshot();

                TextMessage msg = sendSession.createTextMessage(json);
                msg.setJMSCorrelationID(corrId);
                msg.setJMSReplyTo(replyQueue);
                msg.setStringProperty("vc", codec.toNode(after).toString());   //z.B. {"alice":2,"server":1}
                msg.setStringProperty("sender", owner);

                long delay = nextDelay.getAndSet(0);
                if (delay > 0) {
                    msg.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, delay);
                }

                producer.send(requestQueue, msg);

                System.out.printf("[%s] SEND %s corr=%s VC before %s after %s%s%n",
                        owner, method, corrId, before, after,
                        delay > 0 ? " delay=" + delay + "ms" : "");
            }
        } catch (JMSException e) {
            pending.remove(corrId);
            throw new ConnectionException("Senden fehlgeschlagen: " + method, e);
        } catch (RuntimeException e) {
            pending.remove(corrId);
            throw e;
        }

        // JsonNode -> Zieltyp; fromNode behandelt Void und null schon selbst
        return raw.thenApply(node -> codec.fromNode(node, resultType));
    }

    public <T> T call(String method, Map<String, Object> params, Class<T> resultType) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<T> completableFuture = callAsync(method, params, resultType);
        T t = completableFuture.get(30, TimeUnit.MILLISECONDS);

        if (t instanceof ExecutionException) throw new ExecutionException("execution failed", ((ExecutionException) t).getCause());
        if (t instanceof RpcException) throw new RpcException(((RpcException) t).getCode(), ((RpcException) t).getMessage());
        if (t instanceof TimeoutException) {
            throw new ConnectionException("timeout");
        }
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Connection failed");
        }

        return t;
    }

    private void onReply(Message msg) {
        CompletableFuture<JsonNode> future = null;
        try {
            String corrId = msg.getJMSCorrelationID();
            future = pending.remove(corrId);
            if (future == null) {
                System.out.printf("[%s] RECV reply corr=%s -> unbekannt oder zu spät, verworfen%n", owner, corrId);
                return;
            }

            // Uhr VOR complete aktualisieren
            Map<String, Integer> before = clock.snapshot();
            clock.merge(codec.decodeClock(msg.getStringProperty("vc")));
            Map<String, Integer> after = clock.snapshot();
            System.out.printf("[%s] RECV reply corr=%s VC vorher %s nachher %s%n", owner, corrId, before, after);

            String body = ((TextMessage) msg).getText();
            JsonRpcMessage response = codec.decode(body);

            if (response.rpcError() != null) {
                future.completeExceptionally(
                        new RpcException(response.rpcError().code(), response.rpcError().message()));
            } else {
                future.complete(response.result());
            }
        } catch (Exception e) {
            System.err.printf("[%s] Fehler in onReply: %s%n", owner, e);
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }

    private void onNotification(Message msg) {
        try {
            Map<String, Integer> before = clock.snapshot();
            clock.merge(codec.decodeClock(msg.getStringProperty("vc")));
            Map<String, Integer> after = clock.snapshot();

            String body = ((TextMessage) msg).getText();
            JsonRpcMessage notification = codec.decode(body);

            System.out.printf("[%s] RECV notification %s VC vorher %s nachher %s%n",
                    owner, notification.method(), before, after);

            if (notificationDispatcher == null) {
                System.err.printf("[%s] Kein Dispatcher gesetzt, Notification verworfen%n", owner);
                return;
            }
            notificationDispatcher.dispatch(notification.method(), notification.params());
            // keine Antwort: Notification hat keine id
        } catch (Exception e) {
            System.err.printf("[%s] Fehler in onNotification: %s%n", owner, e);
        }
    }

    public void sendNotification(String destinationName, String method, Map<String, Object> params) {
        if (closed.get()) {
            throw new ConnectionException("Connection is closed");
        }
        if (destinationName == null || destinationName.isBlank() || method == null || method.isBlank()) {
            throw new IllegalArgumentException("destination or method is null/blank");
        }
        try {
            JsonNode paramsNode = (params == null) ? null : codec.toNode(params);
            JsonRpcMessage notification = new JsonRpcMessage(method, paramsNode, null, null, null); // id = null
            String json = codec.encode(notification);

            synchronized (sendLock) {
                Queue destination = sendSession.createQueue(destinationName);

                Map<String, Integer> before = clock.snapshot();
                clock.tick();
                Map<String, Integer> after = clock.snapshot();

                TextMessage msg = sendSession.createTextMessage(json);
                msg.setStringProperty("vc", codec.toNode(after).toString());
                msg.setStringProperty("sender", owner);

                long delay = nextDelay.getAndSet(0);
                if (delay > 0) {
                    msg.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, delay);
                }

                producer.send(destination, msg);

                System.out.printf("[%s] SEND notification %s -> %s VC vorher %s nachher %s%s%n",
                        owner, method, destinationName, before, after,
                        delay > 0 ? " delay=" + delay + "ms" : "");
            }
        } catch (JMSException e) {
            throw new ConnectionException("Notification fehlgeschlagen: " + method, e);
        }
    }

    public void setNextDelay(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        nextDelay.set(millis);
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ConnectionException closedEx = new ConnectionException("Connection closed");
        pending.values().forEach(f -> f.completeExceptionally(closedEx));
        pending.clear();
        try {
            connection.close();   // schließt auch Sessions, Producer, Consumer
        } catch (JMSException e) {
            System.err.printf("[%s] Fehler beim Schließen: %s%n", owner, e);
        }
    }
}

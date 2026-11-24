package rpc.jms;

import clock.VectorClock;
import com.fasterxml.jackson.databind.JsonNode;
import common.exceptions.ConnectionException;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ScheduledMessage;
import rpc.Codec;
import rpc.Dispatcher;
import rpc.JsonRpcMessage;
import rpc.RpcException;
import rpc.RpcTransport;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transportschicht ueber den Message Broker, ersetzt die TCP-Connection aus Praktikum 1.
 *
 * <p>Channels (Queues):
 * <ul>
 *   <li>{@value #REQUEST_QUEUE}: Requests aller Clients an den Server</li>
 *   <li>{@code game.reply.<owner>}: Antworten an genau diesen Client</li>
 *   <li>{@code game.events.<owner>}: Notifications (Callbacks) an genau diesen Client</li>
 * </ul>
 *
 * <p>Die JSON-RPC-id ist ein Zaehler pro Verbindung und dient zugleich als JMSCorrelationID.
 * Jede Nachricht traegt die Vektoruhr als String-Property {@code vc}: vor dem Senden tick,
 * beim Empfangen merge.
 *
 * <p>Threads: Eine JMS-Session ist nicht thread-sicher. Deshalb gibt es eine Session nur zum
 * Senden (geschuetzt durch {@code sendLock}) und eine nur fuer die Listener.
 */
public final class JmsConnection implements RpcTransport {

    public static final String REQUEST_QUEUE = "game.requests";
    public static final String REPLY_QUEUE_PREFIX = "game.reply.";
    public static final String EVENT_QUEUE_PREFIX = "game.events.";

    /** Damit eine verzoegerte Nachricht in der Demo kein Timeout ausloest. */
    private static final long CALL_TIMEOUT_MS = 30_000;

    private final String owner;
    private final VectorClock clock;
    private final Codec codec;

    private final Connection connection;
    private final Session sendSession;
    private final Session receiveSession;
    private final MessageProducer producer;
    private final Queue requestQueue;
    private final Queue replyQueue;
    private final Queue eventQueue;

    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicLong nextDelay = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object sendLock = new Object();

    private volatile Dispatcher notificationDispatcher;

    /**
     * Baut die Verbindung zum Broker auf und startet den Listener für Antworten.
     *
     * @param brokerUrl z.B. {@code tcp://localhost:61616}
     * @param owner     Kennung dieses Prozesses, z.B. {@code alice} oder {@code server};
     *                  bestimmt die Namen der eigenen Queues und den eigenen Eintrag der Uhr
     * @throws ConnectionException wenn der Broker nicht erreichbar ist
     */
    public JmsConnection(String brokerUrl, String owner, VectorClock clock, Codec codec) {
        if (brokerUrl == null || brokerUrl.isBlank() || owner == null || owner.isBlank()
                || clock == null || codec == null) {
            throw new IllegalArgumentException("One or more parameters are null or blank.");
        }
        this.owner = owner;
        this.clock = clock;
        this.codec = codec;

        Connection c = null;
        try {
            //1. Connection Factory
            c = new ActiveMQConnectionFactory(brokerUrl).createConnection();
            //2. Connection
            this.connection = c;

            //3. Sessions
            this.sendSession = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
            this.receiveSession = c.createSession(false, Session.AUTO_ACKNOWLEDGE);

            //4. Destination
            this.requestQueue = sendSession.createQueue(REQUEST_QUEUE);
            this.replyQueue = sendSession.createQueue(REPLY_QUEUE_PREFIX + owner);
            this.eventQueue = sendSession.createQueue(EVENT_QUEUE_PREFIX + owner);

            //5. Message Producer
            //Ziel wird erst beim Senden angegeben -> ein Producer fuer alle Queues
            this.producer = sendSession.createProducer(null);

            //5. Message Producer
            //Listener fuer Antworten auf der EMPFANGS-Session, VOR connection.start()
            MessageConsumer replyConsumer = receiveSession.createConsumer(replyQueue);
            replyConsumer.setMessageListener(this::onReply);

            c.start();
        } catch (JMSException e) {
            closeQuietly(c);
            throw new ConnectionException("Broker nicht erreichbar: " + brokerUrl + " (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Setzt den Empfaenger fuer Notifications (Client: GameListenerDispatcher) und startet
     * den Listener auf {@code game.events.<owner>}. VOR register aufrufen.
     */
    public void setNotificationDispatcher(Dispatcher dispatcher) {
        if (dispatcher == null) throw new IllegalArgumentException("Dispatcher is null");
        if (notificationDispatcher != null) throw new IllegalStateException("Dispatcher already set");
        notificationDispatcher = dispatcher;
        try {
            MessageConsumer eventConsumer = receiveSession.createConsumer(eventQueue);
            eventConsumer.setMessageListener(this::onNotification);
        } catch (JMSException e) {
            throw new ConnectionException("Event-Listener konnte nicht gestartet werden", e);
        }
    }

    //Senden

    /** Synchroner Aufruf: wartet auf die Antwort mit der passenden Correlation-ID. */
    @Override
    public <T> T call(String method, Map<String, Object> params, Class<T> resultType) {
        try {
            return callAsync(method, params, resultType).get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RpcException rpc) throw rpc;              //Fehler Antwort des Servers
            if (cause instanceof ConnectionException ce) throw ce;         //z.B. close() während des Wartens
            if (cause instanceof RuntimeException re) throw re;            //z.B. Codec Fehler beim Umwandeln
            throw new ConnectionException("Aufruf fehlgeschlagen: " + method, cause);
        } catch (TimeoutException e) {
            throw new ConnectionException("Timeout nach " + CALL_TIMEOUT_MS + " ms: " + method, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Unterbrochen: " + method, e);
        }
    }

    /**
     * Asynchroner Aufruf: sendet den Request und kehrt sofort zurueck.
     * Die Future wird von {@link #onReply(Message)} beendet.
     */
    @Override
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
        pending.put(corrId, raw);

        try {
            JsonNode paramsNode = (params == null) ? null : codec.toNode(params);
            String json = codec.encode(JsonRpcMessage.request(id, method, paramsNode));

            synchronized (sendLock) {
                Map<String, Integer> before = clock.snapshot();
                clock.tick();
                Map<String, Integer> after = clock.snapshot();

                TextMessage msg = sendSession.createTextMessage(json);
                msg.setJMSCorrelationID(corrId);
                msg.setJMSReplyTo(replyQueue);
                msg.setStringProperty("vc", codec.toNode(after).toString());   // z.B. {"alice":2,"server":1}
                msg.setStringProperty("sender", owner);

                long delay = nextDelay.getAndSet(0);
                if (delay > 0) {
                    msg.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, delay);
                }

                producer.send(requestQueue, msg);

                System.out.printf("[%s] SEND %s corr=%s VC vorher %s nachher %s%s%n",
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

        // JsonNode -> Zieltyp; fromNode behandelt Void und null selbst
        return raw.thenApply(node -> codec.fromNode(node, resultType));
    }

    /**
     * Sendet eine Notification (ohne id, ohne Antwort) an eine benannte Queue,
     * z.B. {@code game.events.alice}. Wird auf der Serverseite vom GameListenerProxy benutzt.
     */
    public void sendNotification(String destinationName, String method, Map<String, Object> params) {
        if (closed.get()) {
            throw new ConnectionException("Connection is closed");
        }
        if (destinationName == null || destinationName.isBlank() || method == null || method.isBlank()) {
            throw new IllegalArgumentException("destination or method is null/blank");
        }
        try {
            JsonNode paramsNode = (params == null) ? null : codec.toNode(params);
            String json = codec.encode(JsonRpcMessage.notification(method, paramsNode));

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

    /** Verzoegert genau die nächste gesendete Nachricht um {@code millis} (Broker-Scheduler). */
    public void setNextDelay(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        nextDelay.set(millis);
    }

    //Empfangen

    /** Listener auf game.reply.&lt;owner&gt;: ordnet die Antwort ueber die Correlation-ID zu. */
    private void onReply(Message msg) {
        CompletableFuture<JsonNode> future = null;
        try {
            String corrId = msg.getJMSCorrelationID();
            future = pending.remove(corrId);
            if (future == null) {
                System.out.printf("[%s] RECV reply corr=%s -> unbekannt oder zu spaet, verworfen%n", owner, corrId);
                return;
            }

            // Uhr VOR complete aktualisieren, damit die Logs in der richtigen Reihenfolge stehen
            Map<String, Integer> before = clock.snapshot();
            clock.merge(codec.decodeClock(msg.getStringProperty("vc")));
            Map<String, Integer> after = clock.snapshot();
            System.out.printf("[%s] RECV reply corr=%s VC vorher %s nachher %s%n", owner, corrId, before, after);

            JsonRpcMessage response = codec.decode(((TextMessage) msg).getText());
            if (response.rpcError() != null) {
                future.completeExceptionally(
                        new RpcException(response.rpcError().code(), response.rpcError().message()));
            } else {
                future.complete(response.result());
            }
        } catch (Exception e) {
            // Aus einem Listener darf nichts herausfliegen (sonst Redelivery durch ActiveMQ)
            System.err.printf("[%s] Fehler in onReply: %s%n", owner, e);
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }

    /** Listener auf game.events.&lt;owner&gt;: Server -&gt; Client Notifications. */
    private void onNotification(Message msg) {
        try {
            Map<String, Integer> before = clock.snapshot();
            clock.merge(codec.decodeClock(msg.getStringProperty("vc")));
            Map<String, Integer> after = clock.snapshot();

            JsonRpcMessage notification = codec.decode(((TextMessage) msg).getText());
            System.out.printf("[%s] RECV notification %s VC vorher %s nachher %s%n",
                    owner, notification.method(), before, after);

            Dispatcher d = notificationDispatcher;
            if (d == null) {
                System.err.printf("[%s] Kein Dispatcher gesetzt, Notification verworfen%n", owner);
                return;
            }
            d.dispatch(notification.method(), notification.params());
            //keine Antwort: Notification hat keine id
        } catch (Exception e) {
            System.err.printf("[%s] Fehler in onNotification: %s%n", owner, e);
        }
    }

    //Schliessen

    /**
     * Schliesst genau einmal und beendet alle wartenden Aufrufe. Bewusst NICHT im sendLock.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ConnectionException closedEx = new ConnectionException("Connection closed");
        pending.values().forEach(f -> f.completeExceptionally(closedEx));
        pending.clear();
        closeQuietly(connection);   // schliesst auch Sessions, Producer, Consumer
    }

    private void closeQuietly(Connection c) {
        if (c == null) return;
        try {
            c.close();
        } catch (JMSException e) {
            System.err.printf("[%s] Fehler beim Schliessen: %s%n", owner, e);
        }
    }
}

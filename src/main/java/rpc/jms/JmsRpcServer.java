package rpc.jms;

import clock.VectorClock;
import com.fasterxml.jackson.databind.JsonNode;
import common.ConnectionException;
import jakarta.jms.*;
import jakarta.jms.Connection;
import org.apache.activemq.ActiveMQConnectionFactory;
import rpc.*;

import java.util.Map;

/**
 * Server-seitige Transportschicht: hört auf game.requests, ruft den Dispatcher (Skeleton) auf
 * und schickt die Antwort an JMSReplyTo mit derselben Correlation-ID zurück.
 */
public final class JmsRpcServer {

    public static final String REQUEST_QUEUE = JmsConnection.REQUEST_QUEUE;
    public static final String EVENT_QUEUE_PREFIX = JmsConnection.EVENT_QUEUE_PREFIX;

    private final String brokerUrl;
    private final Dispatcher dispatcher;
    private final VectorClock clock;          //Gleiche Instanz wie in der Notification JmsConnection
    private final Codec codec;
    private final JmsConnection notifier;     //ownerId "server", für Notifications an die Clients

    private Connection connection;
    private Session session;                  //gehört dem Listener-Thread
    private MessageProducer replyProducer;

    public JmsRpcServer(String brokerUrl, Dispatcher dispatcher, VectorClock clock,
                        Codec codec, JmsConnection notifier) {
        this.brokerUrl = brokerUrl;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.codec = codec;
        this.notifier = notifier;
    }

    public void start() {
        try {
            connection = new ActiveMQConnectionFactory(brokerUrl).createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            replyProducer = session.createProducer(null);

            MessageConsumer consumer = session.createConsumer(session.createQueue(REQUEST_QUEUE));
            consumer.setMessageListener(this::onRequest);

            connection.start(); //erst nach dem Listener
            System.out.println("[server] lauscht auf " + REQUEST_QUEUE);
        } catch (JMSException e) {
            stop();
            throw new ConnectionException("Server-Start fehlgeschlagen", e);
        }
    }

    private void onRequest(Message msg) {
        try {
            String corrId = msg.getJMSCorrelationID();
            Destination replyTo = msg.getJMSReplyTo();
            String sender = msg.getStringProperty("sender");

            //1. Empfang: merge
            Map<String, Integer> receivedVc = codec.decodeClock(msg.getStringProperty("vc"));
            Map<String, Integer> before = clock.snapshot();
            clock.merge(receivedVc);
            Map<String, Integer> afterRecv = clock.snapshot();

            JsonRpcMessage request = codec.decode(((TextMessage) msg).getText());
            System.out.printf("[server] RECV %s von %s corr=%s VC vorher %s nachher %s%n",
                    request.method(), sender, corrId, before, afterRecv);

            //2. Fachlogik über den Skeleton
            JsonRpcMessage response;
            try {
                JsonNode result = dispatcher.dispatch(request.method(), request.params());
                response = new JsonRpcMessage(null, null, result, null, request.id());
            } catch (RpcException e) {
                response = new JsonRpcMessage(null, null, null,
                        new JsonRpcMessage.RpcError(e.getCode(), e.getMessage()), request.id());
            } catch (RuntimeException e) {
                response = new JsonRpcMessage(null, null, null,
                        new JsonRpcMessage.RpcError(RpcException.INTERNAL_ERROR, "Internal error: " + e.getMessage()), request.id());
            }

            if (replyTo == null) {
                return;                        // kein Antwortziel -> wie Notification behandeln
            }

            //3. und 4. Senden: tick, Antwort mit derselben Correlation-ID
            clock.tick();
            Map<String, Integer> afterSend = clock.snapshot();

            TextMessage reply = session.createTextMessage(codec.encode(response));
            reply.setJMSCorrelationID(corrId);
            reply.setStringProperty("vc", codec.toNode(afterSend).toString());
            reply.setStringProperty("sender", "server");
            replyProducer.send(replyTo, reply);

            System.out.printf("[server] SEND reply %s an %s corr=%s VC %s%n",
                    response.rpcError() == null ? "ok" : "error " + response.rpcError().code(),
                    sender, corrId, afterSend);
        } catch (Exception e) {
            System.err.println("[server] Fehler in onRequest: " + e);
        }
    }

    public void stop() {
        try {
            if (connection != null) connection.close();
        } catch (JMSException e) {
            System.err.println("[server] Fehler beim Schließen: " + e);
        }
    }
}
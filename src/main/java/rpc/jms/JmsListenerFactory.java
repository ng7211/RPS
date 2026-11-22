package rpc.jms;

// ===== KI-generiert (Claude), im KI-Log vermerken =====

import common.CallbackEndpoint;
import common.GameListener;
import common.ListenerFactory;
import rpc.GameListenerProxy;

/**
 * Macht aus einem CallbackEndpoint einen GameListenerProxy, der über den Broker
 * an die Event-Queue des Clients sendet. Ersetzt die TCP-Variante ConnectionListenerFactory.
 *
 * Erwarteter Pfad im Endpoint: "/queue/<queueName>", z.B. "/queue/game.events.alice".
 */
public final class JmsListenerFactory implements ListenerFactory {

    public static final String PREFIX = "/queue/";

    private final JmsConnection notifier;   // die Server-JmsConnection (ownerId "server")

    public JmsListenerFactory(JmsConnection notifier) {
        if (notifier == null) throw new IllegalArgumentException("notifier is null");
        this.notifier = notifier;
    }

    @Override
    public GameListener create(CallbackEndpoint callbackEndpoint) {
        if (callbackEndpoint == null || callbackEndpoint.path() == null
                || !callbackEndpoint.path().startsWith(PREFIX)) {
            throw new IllegalArgumentException("Ungültiger Endpoint: " + callbackEndpoint);
        }
        String queueName = callbackEndpoint.path().substring(PREFIX.length());
        if (queueName.isBlank()) {
            throw new IllegalArgumentException("Leerer Queue-Name: " + callbackEndpoint);
        }
        return new GameListenerProxy(new JmsNotificationTarget(notifier, queueName));
    }
}
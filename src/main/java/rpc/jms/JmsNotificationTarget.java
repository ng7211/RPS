package rpc.jms;

import rpc.NotificationSender;

import java.util.Map;

/**
 * Bindet eine JmsConnection an eine feste Ziel-Queue, z.B. game.events.alice.
 */
public final class JmsNotificationTarget implements NotificationSender {
    private final JmsConnection jms;
    private final String destinationName;

    public JmsNotificationTarget(JmsConnection jms, String destinationName) {
        this.jms = jms;
        this.destinationName = destinationName;
    }

    @Override
    public void sendNotification(String method, Map<String, Object> params) {
        jms.sendNotification(destinationName, method, params);
    }
}
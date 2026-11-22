package rpc;

import java.util.Map;

/** Etwas, an das man eine Notification schicken kann. Der GameListenerProxy kennt nur das. */
public interface NotificationSender {
    void sendNotification(String method, Map<String, Object> params);
}
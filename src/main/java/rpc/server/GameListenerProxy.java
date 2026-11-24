package rpc.server;

import common.dtos.GameState;
import common.dtos.Player;
import common.dtos.RoundResult;
import common.interfaces.GameListener;
import rpc.NotificationSender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-Stub für GameListener. Wird vom Server benutzt, um den Client zu
 * benachrichtigen. Sendet Notifications (kein Ergebnis, keine Bestätigung).
 *
 * Die Methodennamen und Parameternamen sind Teil des Protokolls und müssen
 * exakt zu GameListenerDispatcher passen.
 */
public final class GameListenerProxy implements GameListener {

    private final NotificationSender notificationSender;

    public GameListenerProxy(NotificationSender notificationSender) {
        if (notificationSender == null) {
            throw new IllegalArgumentException("NotificationSender is null");
        }
        this.notificationSender = notificationSender;
    }

    @Override
    public void onOpponentJoined(Player opponent) {
        notificationSender.sendNotification("onOpponentJoined", params("opponent", opponent));
    }

    @Override
    public void onRoundFinished(RoundResult result) {
        notificationSender.sendNotification("onRoundFinished", params("result", result));
    }

    @Override
    public void onGameFinished(GameState state) {
        notificationSender.sendNotification("onGameFinished", params("state", state));
    }

    /**
     * Baut benannte Parameter. Bewusst keine Map.of, weil die kein null erlaubt.
     * Der Rückgabetyp Map<String,Object> passt zu Connection.sendNotification;
     * der Codec wandelt die Map intern in einen JSON-Baum.
     */
    private static Map<String, Object> params(String name, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(name, value);
        return map;
    }
}
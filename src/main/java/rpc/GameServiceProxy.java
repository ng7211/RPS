package rpc;

import common.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-Stub für GameService. Sieht fuer GameClient aus wie der echte Dienst,
 * wandelt aber jeden Aufruf in eine JSON-RPC-Nachricht und zurück.
 *
 * Fehlerabbildung: RpcException des Servers wird ueber RpcException.toException
 * wieder in die passende Spiel-Exception verwandelt (Abnahmepunkt 7).
 */
public final class GameServiceProxy implements GameService {

    private final Connection connection;

    public GameServiceProxy(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection is null");
        }
        this.connection = connection;
    }

    @Override
    public GameState register(Player player, CallbackEndpoint callbackEndpoint) {
        return call("register",
                params("player", player, "endpoint", callbackEndpoint),
                GameState.class);
    }

    @Override
    public MoveStatus submitMove(Player player, Move move) {
        return call("submitMove",
                params("player", player, "move", move),
                MoveStatus.class);
    }

    @Override
    public GameState getState(Player player) {
        return call("getState",
                params("player", player),
                GameState.class);
    }

    @Override
    public void leave(Player player) {
        call("leave",
                params("player", player),
                Void.class);
    }

    /**
     * Führt den entfernten Aufruf aus und übersetzt Fehler-Antworten in die
     * passende Spiel-Exception. ConnectionException (Transport) wird NICHT
     * gefangen - Failure-Transparenz ist bewusst nicht erreicht.
     */
    private <T> T call(String method, Map<String, Object> params, Class<T> resultType) {
        try {
            return connection.call(method, params, resultType);
        } catch (RpcException e) {
            throw RpcException.toException(e.getCode(), e.getMessage());
        }
    }

    /**
     * Baut die benannten Parameter. Bewusst keine Map.of, weil die kein null erlaubt.
     * Ein fehlender Zug soll beim Server als null ankommen und dort dieselbe
     * InvalidMoveException auslösen wie im lokalen Betrieb.
     */
    private static Map<String, Object> params(Object... namesAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            map.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return map;
    }
}
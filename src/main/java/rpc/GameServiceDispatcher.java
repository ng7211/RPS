package rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import common.*;

/**
 * Server-Skeleton für GameService. Liest einen Aufruf, ruft den echten Dienst
 * und baut das Ergebnis. Verwandelt Spiel-Exceptions in RpcException.
 *
 * <p><b>Henne-Ei:</b> Der Dispatcher braucht die Connection (für remoteHost/
 * remotePort und fuer GameListenerProxy), die Connection braucht den Dispatcher.
 * Deshalb wird der Dispatcher ueber eine Factory erzeugt, die die Connection
 * als Argument bekommt (siehe RpcServer).
 */
public final class GameServiceDispatcher implements Dispatcher {

    private final GameService service;
    private final Connection connection;
    private final ConnectionListenerFactory factory;
    private final Codec codec;

    private volatile Player player;
    private volatile String connectionId;

    public GameServiceDispatcher(GameService service, Connection connection,
                                 ConnectionListenerFactory factory, Codec codec) {
        if (service == null || connection == null || factory == null || codec == null) {
            throw new IllegalArgumentException("At least one parameter is null");
        }
        this.service = service;
        this.connection = connection;
        this.factory = factory;
        this.codec = codec;
    }

    @Override
    public JsonNode dispatch(String method, JsonNode params) {
        try {
            return switch (method) {
                case "register" -> register(params);
                case "submitMove" -> codec.toNode(service.submitMove(
                        codec.getParam(params, "player", Player.class),
                        codec.getParam(params, "move", Move.class)));
                case "getState" -> codec.toNode(service.getState(
                        codec.getParam(params, "player", Player.class)));
                case "leave" -> {
                    service.leave(codec.getParam(params, "player", Player.class));
                    yield NullNode.getInstance();
                }
                default -> throw new RpcException(RpcException.METHOD_NOT_FOUND,
                        "Unknown method: " + method);
            };
        } catch (RpcException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RpcException(RpcException.codeFor(e), e.getMessage());
        }
    }

    /**
     * register: Der mitgesendete Endpoint ist ein Platzhalter. Der Server baut
     * aus der Connection einen echten Endpoint und merkt sich die Verbindung.
     */
    private JsonNode register(JsonNode params) {
        Player p = codec.getParam(params, "player", Player.class);

        // Vor service.register: factory braucht die Kennung für den Listener.
        String id = factory.attach(connection);
        CallbackEndpoint realEndpoint = new CallbackEndpoint(
                connection.remoteHost(),
                connection.remotePort(),
                "/conn/" + id);

        try {
            GameState state = service.register(p, realEndpoint);
            this.player = p;
            this.connectionId = id;
            return codec.toNode(state);
        } catch (RuntimeException e) {
            // Nur abmelden, wenn diese Verbindung sich noch nie erfolgreich
            // angemeldet hat. Sonst verliert der erste Spieler seinen Rückkanal.
            if (this.player == null) {
                factory.detach(id);
            }
            throw e;
        }
    }

    @Override
    public void onClose() {
        Player p = this.player;
        if (p != null) {
            try {
                service.leave(p);
            } catch (RuntimeException e) {
                System.err.println("leave beim Verbindungsende fehlgeschlagen: " + e);
            }
        }
        factory.detach(connectionId);
    }
}
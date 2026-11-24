package rpc.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import common.dtos.CallbackEndpoint;
import common.dtos.Player;
import common.enums.Move;
import common.interfaces.GameService;
import rpc.Codec;
import rpc.Dispatcher;
import rpc.RpcException;

/**
 * Server-Skeleton für GameService. Liest einen Aufruf, ruft den echten Dienst
 * und baut das Ergebnis. Verwandelt Spiel-Exceptions in RpcException.
 *
 * <p>Mit JMS gibt es EINEN Dispatcher für alle Clients (alle Requests kommen über
 * dieselbe Queue). Er hält deshalb keinen Zustand pro Client. Der Callback-Endpoint
 * wird vom Client mitgeschickt und unverändert an den Dienst weitergereicht.
 */
public final class GameServiceDispatcher implements Dispatcher {

    private final GameService service;
    private final Codec codec;

    public GameServiceDispatcher(GameService service, Codec codec) {
        if (service == null || codec == null) {
            throw new IllegalArgumentException("At least one parameter is null");
        }
        this.service = service;
        this.codec = codec;
    }

    @Override
    public JsonNode dispatch(String method, JsonNode params) {
        try {
            return switch (method) {
                case "register" -> codec.toNode(service.register(
                        codec.getParam(params, "player", Player.class),
                        codec.getParam(params, "endpoint", CallbackEndpoint.class)));
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
    // kein onClose(): Default aus Dispatcher (macht nichts)
}
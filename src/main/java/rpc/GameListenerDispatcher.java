package rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import common.*;

/**
 * Client-Skeleton: nimmt Rückruf-Notifications vom Server entgegen und ruft
 * den lokalen GameListener auf. Gegenstück zu GameListenerProxy.
 */
public final class GameListenerDispatcher implements Dispatcher {

    private final GameListener listener;
    private final Codec codec;

    public GameListenerDispatcher(GameListener listener, Codec codec) {
        if (listener == null || codec == null) {
            throw new IllegalArgumentException("Listener or Codec is null");
        }
        this.listener = listener;
        this.codec = codec;
    }

    @Override
    public JsonNode dispatch(String method, JsonNode params) {
        switch (method) {
            case "onOpponentJoined" -> listener.onOpponentJoined(
                    codec.getParam(params, "opponent", Player.class));
            case "onRoundFinished" -> listener.onRoundFinished(
                    codec.getParam(params, "result", RoundResult.class));
            case "onGameFinished" -> listener.onGameFinished(
                    codec.getParam(params, "state", GameState.class));
            default -> throw new RpcException(RpcException.METHOD_NOT_FOUND,
                    "Unknown callback: " + method);
        }
        return NullNode.getInstance();
    }
}
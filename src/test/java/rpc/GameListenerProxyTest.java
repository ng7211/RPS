// AI-GENERATED-START: GameListenerProxyTest ohne TCP (Claude), 23.11.2026
package rpc;

import common.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sichert ab, dass GameListenerProxy und GameListenerDispatcher die gleichen
 * Methoden- und Parameternamen benutzen. Der NotificationSender schickt jede
 * Notification als echten JSON-Text durch den Codec an den Dispatcher.
 */
class GameListenerProxyTest {

    private final Codec codec = new Codec();

    /** Verbindet Proxy und Dispatcher ueber das Drahtformat, ohne Broker. */
    private NotificationSender loopbackTo(GameListenerDispatcher dispatcher) {
        return (method, params) -> {
            String line = codec.encode(JsonRpcMessage.notification(method, codec.toNode(params)));
            JsonRpcMessage received = codec.decode(line);
            assertTrue(received.isNotification(), "Callback muss eine Notification (ohne id) sein");
            dispatcher.dispatch(received.method(), received.params());
        };
    }

    @Test
    void allCallbacksArriveWithCorrectValues() {
        List<Object> received = new ArrayList<>();
        GameListener listener = new GameListener() {
            public void onOpponentJoined(Player opponent) { received.add(opponent); }
            public void onRoundFinished(RoundResult result) { received.add(result); }
            public void onGameFinished(GameState state) { received.add(state); }
        };
        GameListenerProxy proxy = new GameListenerProxy(loopbackTo(new GameListenerDispatcher(listener, codec)));

        Player bob = new Player(2, "Bob");
        RoundResult round = new RoundResult(1,
                new Player(1, "A"), Move.ROCK,
                new Player(2, "B"), Move.SCISSORS,
                Outcome.WINNERPLAYER1);
        GameState end = new GameState(List.of(), 0, 0, 1, List.of(), 3,
                GamePhase.FINISHED, null, FinishReason.NONE);

        proxy.onOpponentJoined(bob);
        proxy.onRoundFinished(round);
        proxy.onGameFinished(end);

        assertEquals(List.of(bob, round, end), received);
    }

    @Test
    void proxySendetRichtigeMethodenUndParameterNamen() {
        List<String> calls = new ArrayList<>();
        NotificationSender recorder = (method, params) -> calls.add(method + params.keySet());
        GameListenerProxy proxy = new GameListenerProxy(recorder);

        proxy.onOpponentJoined(new Player(2, "Bob"));
        proxy.onRoundFinished(null);
        proxy.onGameFinished(null);

        assertEquals(List.of("onOpponentJoined[opponent]", "onRoundFinished[result]", "onGameFinished[state]"), calls);
    }

    @Test
    void nullSenderWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> new GameListenerProxy(null));
    }

    @Test
    void unknownCallbackIsMethodNotFound() {
        GameListenerDispatcher dispatcher = new GameListenerDispatcher(
                new GameListener() {
                    public void onOpponentJoined(Player p) {}
                    public void onRoundFinished(RoundResult r) {}
                    public void onGameFinished(GameState s) {}
                },
                codec);

        RpcException e = assertThrows(RpcException.class,
                () -> dispatcher.dispatch("onSomethingElse", codec.newParams()));
        assertEquals(RpcException.METHOD_NOT_FOUND, e.getCode());
    }
}
// AI-GENERATED-END

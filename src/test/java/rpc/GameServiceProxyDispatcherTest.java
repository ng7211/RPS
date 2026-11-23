// AI-GENERATED-START: GameServiceProxyDispatcherTest auf Loopback umgestellt (Claude), 23.11.2026
package rpc;

import common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameServiceProxy -> Codec (JSON-Text) -> GameServiceDispatcher -> Dienst und zurueck.
 * Ohne Broker: der Transport ist ein LoopbackTransport. Deckt Proxy, Dispatcher,
 * Drahtformat und Fehlerabbildung gleichzeitig ab.
 */
class GameServiceProxyDispatcherTest {

    private FakeService service;
    private GameServiceProxy proxy;

    static class FakeService implements GameService {
        Player lastRegistered;
        CallbackEndpoint lastEndpoint;
        Move lastMove;
        Player lastLeft;
        RuntimeException nextException;
        GameState lastState;

        public GameState register(Player p, CallbackEndpoint e) {
            if (nextException != null) { RuntimeException x = nextException; nextException = null; throw x; }
            lastRegistered = p; lastEndpoint = e;
            return new GameState(List.of(p), 0, 0, 1, List.of(), 3, GamePhase.WAITING, null, FinishReason.NONE);
        }
        public MoveStatus submitMove(Player p, Move m) {
            if (m == null) throw new InvalidMoveException("no move");
            lastMove = m;
            return MoveStatus.ROUND_COMPLETE;
        }
        public GameState getState(Player p) {
            if (lastState == null) throw new UnknownPlayerException("unknown");
            return lastState;
        }
        public void leave(Player p) { lastLeft = p; }
    }

    @BeforeEach
    void setUp() {
        service = new FakeService();
        proxy = new GameServiceProxy(new LoopbackTransport(new GameServiceDispatcher(service, new Codec())));
    }

    @Test
    void registerReichtEndpointUnveraendertWeiter() {
        Player p = new Player(1, "alice");
        CallbackEndpoint endpoint = new CallbackEndpoint("jms", 0, "/queue/game.events.alice");

        GameState state = proxy.register(p, endpoint);

        assertEquals(p, service.lastRegistered);
        assertEquals(endpoint, service.lastEndpoint);
        assertEquals(GamePhase.WAITING, state.gamePhase());
    }

    @Test
    void submitMoveReturnsMoveStatus() {
        MoveStatus s = proxy.submitMove(new Player(1, "a"), Move.ROCK);
        assertEquals(MoveStatus.ROUND_COMPLETE, s);
        assertEquals(Move.ROCK, service.lastMove);
    }

    @Test
    void nullMoveKommtAlsInvalidMoveExceptionZurueck() {
        assertThrows(InvalidMoveException.class, () -> proxy.submitMove(new Player(1, "a"), null));
    }

    @Test
    void getStatePropagatesUnknownPlayerException() {
        assertThrows(UnknownPlayerException.class,
                () -> proxy.getState(new Player(99, "ghost")));
    }

    @Test
    void nameTakenBecomesNameTakenException() {
        service.nextException = new NameTakenException("vergeben");

        NameTakenException e = assertThrows(NameTakenException.class,
                () -> proxy.register(new Player(1, "a"), new CallbackEndpoint("x", 0, "/queue/x")));
        assertEquals("vergeben", e.getMessage());
    }

    @Test
    void leaveSendsVoid() {
        Player p = new Player(1, "a");
        proxy.leave(p);
        assertEquals(p, service.lastLeft);
    }

    @Test
    void unbekannteMethodeIstMethodNotFound() {
        GameServiceDispatcher dispatcher = new GameServiceDispatcher(service, new Codec());
        RpcException e = assertThrows(RpcException.class,
                () -> dispatcher.dispatch("cheat", new Codec().newParams()));
        assertEquals(RpcException.METHOD_NOT_FOUND, e.getCode());
    }

    @Test
    void nullParameterImKonstruktorWerfen() {
        assertThrows(IllegalArgumentException.class, () -> new GameServiceDispatcher(null, new Codec()));
        assertThrows(IllegalArgumentException.class, () -> new GameServiceProxy(null));
    }
}
// AI-GENERATED-END

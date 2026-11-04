package rpc;

import common.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstest: GameServiceProxy <-> echter RpcServer <-> GameServiceDispatcher.
 * Deckt Proxy, Dispatcher und Fehlerabbildung gleichzeitig ab.
 */
class GameServiceProxyDispatcherTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private FakeService service;
    private ConnectionListenerFactory factory;
    private RpcServer server;
    private Connection clientConnection;
    private GameServiceProxy proxy;

    static class FakeService implements GameService {
        Player lastRegistered;
        CallbackEndpoint lastEndpoint;
        RuntimeException nextException;
        GameState lastState;

        public GameState register(Player p, CallbackEndpoint e) {
            if (nextException != null) { RuntimeException x = nextException; nextException = null; throw x; }
            lastRegistered = p; lastEndpoint = e;
            return new GameState(List.of(p), 0, 0, 1, List.of(), 3, GamePhase.WAITING, null, FinishReason.NONE);
        }
        public MoveStatus submitMove(Player p, Move m) { return MoveStatus.ROUND_COMPLETE; }
        public GameState getState(Player p) {
            if (lastState == null) throw new UnknownPlayerException("unknown");
            return lastState;
        }
        public void leave(Player p) {}
    }

    @BeforeEach
    void setUp() throws IOException {
        Codec codec = new Codec();
        service = new FakeService();
        factory = new ConnectionListenerFactory();
        server = new RpcServer(0, codec, TIMEOUT,
                connection -> new GameServiceDispatcher(service, connection, factory, codec));
        server.start();

        clientConnection = new Connection(
                new Socket("127.0.0.1", server.getPort()), codec, TIMEOUT);
        // Kein Dispatcher auf Client-Seite: Wir rufen nur den Proxy auf.
        clientConnection.start();
        proxy = new GameServiceProxy(clientConnection);
    }

    @AfterEach
    void tearDown() {
        if (clientConnection != null) clientConnection.close();
        if (server != null) server.stop();
    }

    @Test
    void registerSendsPlayerAndBautEchtenEndpoint() {
        Player p = new Player(1, "alice");
        // Der Platzhalter-Endpoint, den der Proxy schickt, wird vom Dispatcher ignoriert.
        CallbackEndpoint placeholder = new CallbackEndpoint("unused", 0, "/unused");

        GameState state = proxy.register(p, placeholder);

        assertEquals(p, service.lastRegistered);
        // Der Dispatcher hat aus der Connection einen echten Endpoint gebaut:
        assertEquals("/conn/1", service.lastEndpoint.path());
        assertTrue(service.lastEndpoint.port() > 0);
        assertEquals(GamePhase.WAITING, state.gamePhase());
    }

    @Test
    void submitMoveReturnsMoveStatus() {
        MoveStatus s = proxy.submitMove(new Player(1, "a"), Move.ROCK);
        assertEquals(MoveStatus.ROUND_COMPLETE, s);
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
                () -> proxy.register(new Player(1, "a"), new CallbackEndpoint("x", 0, "/x")));
        assertEquals("vergeben", e.getMessage());
    }

    @Test
    void leaveSendsVoid() {
        proxy.leave(new Player(1, "a")); // wirft nichts
    }
}
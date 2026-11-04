package rpc;

import common.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sichert ab, dass GameListenerProxy und GameListenerDispatcher die gleichen
 * Methoden- und Parameternamen benutzen.
 */
class GameListenerProxyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private Connection server;
    private Connection client;

    private void connect() throws IOException {
        ServerSocket listener = new ServerSocket(0);
        int port = listener.getLocalPort();
        Thread acceptor = new Thread(() -> {
            try {
                Socket s = listener.accept();
                server = new Connection(s, new Codec(), TIMEOUT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try { listener.close(); } catch (IOException ignored) {}
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        client = new Connection(new Socket("127.0.0.1", port), new Codec(), TIMEOUT);
        try { acceptor.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    @Test
    void allCallbacksArriveWithCorrectValues() throws Exception {
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch round  = new CountDownLatch(1);
        CountDownLatch ended  = new CountDownLatch(1);

        GameListener listener = new GameListener() {
            public void onOpponentJoined(Player opponent) {
                assertEquals("Bob", opponent.name());
                joined.countDown();
            }
            public void onRoundFinished(RoundResult result) {
                assertEquals(1, result.roundNr());
                assertEquals(Outcome.WINNERPLAYER1, result.outcome());
                round.countDown();
            }
            public void onGameFinished(GameState state) {
                assertNotNull(state);
                ended.countDown();
            }
        };

        connect();
        client.setDispatcher(new GameListenerDispatcher(listener, new Codec()));
        server.setDispatcher((m, p) -> null);
        client.start();
        server.start();

        GameListenerProxy proxy = new GameListenerProxy(server);

        proxy.onOpponentJoined(new Player(2, "Bob"));
        proxy.onRoundFinished(new RoundResult(1,
                new Player(1, "A"), Move.ROCK,
                new Player(2, "B"), Move.SCISSORS,
                Outcome.WINNERPLAYER1));
        proxy.onGameFinished(new GameState(
                java.util.List.of(), 0, 0, 1, java.util.List.of(), 3,
                GamePhase.FINISHED, null, FinishReason.NONE));

        assertTrue(joined.await(2, TimeUnit.SECONDS), "onOpponentJoined kam nicht an");
        assertTrue(round.await(2, TimeUnit.SECONDS),  "onRoundFinished kam nicht an");
        assertTrue(ended.await(2, TimeUnit.SECONDS),  "onGameFinished kam nicht an");
    }

    @Test
    void unknownCallbackIsMethodNotFound() {
        GameListenerDispatcher dispatcher = new GameListenerDispatcher(
                new GameListener() {
                    public void onOpponentJoined(Player p) {}
                    public void onRoundFinished(RoundResult r) {}
                    public void onGameFinished(GameState s) {}
                },
                new Codec());

        RpcException e = assertThrows(RpcException.class,
                () -> dispatcher.dispatch("onSomethingElse", new Codec().newParams()));
        assertEquals(RpcException.METHOD_NOT_FOUND, e.getCode());
    }
}
package rpc;

import com.fasterxml.jackson.databind.JsonNode;
import common.ConnectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final Codec CODEC = new Codec();

    private Connection server;
    private Connection client;

    /**
     * Verbindet zwei Connections direkt ueber einen Loopback-Socket.
     * Der ServerSocket wird VOR dem Start des Annahme-Threads geoeffnet,
     * damit der Port sicher belegt ist, bevor der Client verbindet.
     */
    private void connect(Dispatcher serverSideDispatcher) throws IOException {
        ServerSocket listener = new ServerSocket(0);
        int port = listener.getLocalPort();

        Thread acceptor = new Thread(() -> {
            try {
                Socket s = listener.accept();
                server = new Connection(s, CODEC, TIMEOUT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try { listener.close(); } catch (IOException ignored) {}
            }
        }, "test-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();

        Socket c = new Socket("127.0.0.1", port);
        client = new Connection(c, CODEC, TIMEOUT);

        try {
            acceptor.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (serverSideDispatcher != null) {
            server.setDispatcher(serverSideDispatcher);
        }
        server.start();
        client.start();
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    // ---------------------------------------------------------------- Grundlagen

    @Test
    void callWithResult() throws IOException {
        connect((method, params) -> CODEC.toNode("antwort"));

        String result = client.call("getState", null, String.class);

        assertEquals("antwort", result);
    }

    @Test
    void callWithVoidResult() throws IOException {
        connect((method, params) -> null);

        assertNull(client.call("leave", null, Void.class));
    }

    @Test
    void errorResponseBecomesRpcException() throws IOException {
        connect((method, params) -> {
            throw new RpcException(RpcException.NAME_TAKEN, "vergeben");
        });

        RpcException e = assertThrows(RpcException.class,
                () -> client.call("register", null, Void.class));

        assertEquals(RpcException.NAME_TAKEN, e.getCode());
        assertEquals("vergeben", e.getMessage());
    }

    // ---------------------------------------------------------------- Bidirektional

    @Test
    void notificationArrivesWithoutReply() throws IOException, InterruptedException {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> methodSeen = new AtomicReference<>();

        connect((method, params) -> {
            methodSeen.set(method);
            received.countDown();
            return null;
        });

        client.sendNotification("onOpponentJoined", null);

        assertTrue(received.await(2, TimeUnit.SECONDS), "Notification kam nicht an");
        assertEquals("onOpponentJoined", methodSeen.get());
    }

    // ---------------------------------------------------------------- Timeout / Verbindungsende

    @Test
    void timeoutThrowsConnectionException() throws IOException {
        // Server-Dispatcher antwortet nicht (blockiert laenger als TIMEOUT).
        connect((method, params) -> {
            try { Thread.sleep(5_000); } catch (InterruptedException ignored) {}
            return null;
        });

        ConnectionException e = assertThrows(ConnectionException.class,
                () -> client.call("haengt", null, Void.class));

        assertTrue(e.getMessage().contains("Timeout"), "Erwartet Timeout, war: " + e.getMessage());
    }

    @Test
    void peerCloseDuringCallAbortsWithConnectionException() throws IOException {
        // Server-Dispatcher schliesst die Serverseite, BEVOR er antwortet.
        // Der Client wartet auf eine Antwort, die nie kommt -> ConnectionException.
        connect((method, params) -> {
            server.close();
            try { Thread.sleep(5_000); } catch (InterruptedException ignored) {}
            return null;
        });

        assertThrows(ConnectionException.class,
                () -> client.call("x", null, Void.class));
    }

    @Test
    void callAfterCloseThrows() throws IOException {
        connect((method, params) -> null);
        client.close();

        assertThrows(ConnectionException.class,
                () -> client.call("x", null, Void.class));
    }

    // ---------------------------------------------------------------- Nebenlaeufigkeit

    @Test
    void concurrentCallsAreNotSwapped() throws Exception {
        // Server spiegelt den Parameter "x" unveraendert zurueck.
        connect((method, params) -> params.get("x"));

        int threads = 2;
        int calls = 50;
        Thread[] ts = new Thread[threads];
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int offset = t * calls;
            ts[t] = new Thread(() -> {
                for (int i = 0; i < calls; i++) {
                    int expected = offset + i;
                    JsonNode params = CODEC.newParams().put("x", expected);
                    int back = client.call("echo", params, Integer.class);
                    if (back != expected) errors.incrementAndGet();
                }
            });
            ts[t].start();
        }
        for (Thread th : ts) th.join();

        assertEquals(0, errors.get(), "Antworten wurden vertauscht");
    }

    // ---------------------------------------------------------------- close / onClose

    @Test
    void closeTwiceCallsOnCloseOnce() throws IOException {
        AtomicInteger closes = new AtomicInteger();

        // Dispatcher sitzt auf der CLIENT-Seite, damit onClose dort laeuft.
        ServerSocket listener = new ServerSocket(0);
        int port = listener.getLocalPort();
        Thread acceptor = new Thread(() -> {
            try {
                Socket s = listener.accept();
                server = new Connection(s, CODEC, TIMEOUT);
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try { listener.close(); } catch (IOException ignored) {}
            }
        }, "test-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();

        Socket c = new Socket("127.0.0.1", port);
        client = new Connection(c, CODEC, TIMEOUT);
        client.setDispatcher(new Dispatcher() {
            @Override
            public JsonNode dispatch(String method, JsonNode params) {
                return null;
            }

            @Override
            public void onClose() {
                closes.incrementAndGet();
            }
        });
        client.start();

        try {
            acceptor.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        client.close();
        client.close();

        assertEquals(1, closes.get(), "onClose muss genau einmal laufen");
    }
}
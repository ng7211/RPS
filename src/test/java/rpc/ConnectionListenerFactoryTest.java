package rpc;

import common.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer ConnectionListenerFactory (Bauanleitung Abschnitt 8).
 *
 * Gefordert:
 *   - attach zweimal mit derselben Connection -> dieselbe Kennung
 *   - zwei Connections -> zwei Kennungen
 *   - create mit gueltigem Pfad -> GameListenerProxy
 *   - create mit unbekanntem oder fremdem Pfad -> IllegalArgumentException
 *   - detach mit null ist erlaubt
 *   - create mit null -> IllegalArgumentException
 *
 * Die Factory hat keinen Codec mehr (Bauanleitung Abschnitt 10:
 * GameListenerProxy bekommt nur die Connection).
 */
class ConnectionListenerFactoryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Codec CODEC = new Codec();

    /**
     * Baut zwei verbundene Connections auf Loopback.
     * Der ServerSocket wird VOR dem Start des Annahme-Threads geoeffnet,
     * damit der Port sicher belegt ist.
     */
    private Connection[] connectPair() throws IOException {
        ServerSocket listener = new ServerSocket(0);
        int port = listener.getLocalPort();
        AtomicReference<Connection> serverRef = new AtomicReference<>();

        Thread acceptor = new Thread(() -> {
            try {
                Socket s = listener.accept();
                serverRef.set(new Connection(s, CODEC, TIMEOUT));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try { listener.close(); } catch (IOException ignored) {}
            }
        }, "test-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();

        Connection client = new Connection(new Socket("127.0.0.1", port), CODEC, TIMEOUT);
        try { acceptor.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return new Connection[] { client, serverRef.get() };
    }

    // ---------------------------------------------------------------- attach / detach

    @Test
    void attachIsIdempotent() throws IOException {
        Connection[] pair = connectPair();
        try {
            ConnectionListenerFactory f = new ConnectionListenerFactory();

            String id1 = f.attach(pair[0]);
            String id2 = f.attach(pair[0]);

            assertEquals(id1, id2, "attach zweimal muss dieselbe Kennung liefern");
        } finally {
            pair[0].close(); pair[1].close();
        }
    }

    @Test
    void twoConnectionsGetTwoIds() throws IOException {
        Connection[] a = connectPair();
        Connection[] b = connectPair();
        try {
            ConnectionListenerFactory f = new ConnectionListenerFactory();

            String idA = f.attach(a[0]);
            String idB = f.attach(b[0]);

            assertNotEquals(idA, idB, "zwei Connections brauchen zwei Kennungen");
        } finally {
            a[0].close(); a[1].close();
            b[0].close(); b[1].close();
        }
    }

    @Test
    void detachWithNullIsNoOp() {
        // Bauanleitung Abschnitt 8, "detach": id darf null sein, dann nichts tun.
        ConnectionListenerFactory f = new ConnectionListenerFactory();
        f.detach(null); // darf nicht werfen
    }

    // ---------------------------------------------------------------- create

    @Test
    void createReturnsProxyForKnownEndpoint() throws IOException {
        Connection[] pair = connectPair();
        try {
            ConnectionListenerFactory f = new ConnectionListenerFactory();
            String id = f.attach(pair[0]);

            CallbackEndpoint ep = new CallbackEndpoint("127.0.0.1", 0, "/conn/" + id);
            GameListener listener = f.create(ep);

            assertNotNull(listener);
            assertInstanceOf(GameListenerProxy.class, listener);
        } finally {
            pair[0].close(); pair[1].close();
        }
    }

    @Test
    void createRejectsForeignPath() {
        // Bauanleitung Abschnitt 8, Schritt 2: Pfad muss mit /conn/ beginnen.
        ConnectionListenerFactory f = new ConnectionListenerFactory();
        CallbackEndpoint ep = new CallbackEndpoint("x", 0, "/unused");

        assertThrows(IllegalArgumentException.class, () -> f.create(ep));
    }

    @Test
    void createRejectsUnknownId() {
        // Bauanleitung Abschnitt 8, Schritt 4: connections.get(id) == null -> IAE.
        ConnectionListenerFactory f = new ConnectionListenerFactory();
        CallbackEndpoint ep = new CallbackEndpoint("x", 0, "/conn/999");

        assertThrows(IllegalArgumentException.class, () -> f.create(ep));
    }

    @Test
    void createRejectsNullEndpoint() {
        // Bauanleitung Abschnitt 8, Schritt 1: endpoint == null -> IAE.
        ConnectionListenerFactory f = new ConnectionListenerFactory();
        assertThrows(IllegalArgumentException.class, () -> f.create(null));
    }
}
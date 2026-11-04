package rpc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RpcServerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void portZeroPicksFreePort() throws IOException {
        RpcServer server = new RpcServer(0, new Codec(), TIMEOUT,
                connection -> (method, params) -> null);
        server.start();
        try {
            assertTrue(server.getPort() > 0);
        } finally {
            server.stop();
        }
    }

    @Test
    void twoClientsConnectInParallel() throws IOException {
        RpcServer server = new RpcServer(0, new Codec(), TIMEOUT,
                connection -> (method, params) -> null);
        server.start();
        try (Socket a = new Socket("127.0.0.1", server.getPort());
             Socket b = new Socket("127.0.0.1", server.getPort())) {
            assertTrue(a.isConnected());
            assertTrue(b.isConnected());
        } finally {
            server.stop();
        }
    }

    @Test
    void stopClosesConnections() throws IOException {
        RpcServer server = new RpcServer(0, new Codec(), TIMEOUT,
                connection -> (method, params) -> null);
        server.start();
        int port = server.getPort();

        Socket clientSocket = new Socket("127.0.0.1", port);
        clientSocket.setSoTimeout(2000); // Lese-Timeout: nie wieder endlos warten

        Connection client = new Connection(clientSocket, new Codec(), TIMEOUT);
        client.start();

        // Synchronisation: erst wenn der Server geantwortet hat, ist die
        // Verbindung serverseitig sicher angenommen und in 'connections'.
        try {
            client.call("ping", null, Void.class);
        } catch (RuntimeException ok) {
            // Auch eine RpcException beweist: Server hat accept() durchlaufen.
        }

        server.stop();

        try {
            int b = clientSocket.getInputStream().read();
            assertEquals(-1, b, "Server hat die Verbindung nicht geschlossen");
        } catch (SocketTimeoutException e) {
            fail("Server hat die Verbindung nicht geschlossen (Timeout beim Lesen)");
        } catch (IOException e) {
            // Auch OK: geschlossene Verbindung kann IOException werfen.
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }
}
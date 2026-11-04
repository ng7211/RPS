package rpc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Nimmt Verbindungen an. Pro Verbindung eine Connection mit eigenem Dispatcher
 * und eigenem Lese-Thread. Zwei Spieler laufen parallel (Game ist synchronized).
 */
public final class RpcServer {

    private final int port;
    private final Codec codec;
    private final Duration timeout;
    private final Function<Connection, Dispatcher> dispatcherFactory;
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public RpcServer(int port, Codec codec, Duration timeout,
                     Function<Connection, Dispatcher> dispatcherFactory) {
        if (codec == null || timeout == null || dispatcherFactory == null) {
            throw new IllegalArgumentException("Argumente dürfen nicht null sein");
        }
        this.port = port;
        this.codec = codec;
        this.timeout = timeout;
        this.dispatcherFactory = dispatcherFactory;
    }

    /** Öffnet den Server. Port 0 wählt einen freien Port (für Tests). */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        // Bindet an ALLE Adressen (nicht nur localhost) - wichtig für zwei Rechner.
        serverSocket.bind(new InetSocketAddress(port));

        acceptThread = new Thread(this::acceptLoop, "rpc-acceptor");
        // NICHT Daemon: ServerMain läuft, solange der Server läuft.
        acceptThread.start();
    }

    public int getPort() { return serverSocket.getLocalPort(); }

    public void stop() {
        running.set(false);
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        connections.forEach(Connection::close);
        connections.clear();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket s = serverSocket.accept();
                Connection c = null;
                try {
                    c = new Connection(s, codec, timeout);
                    c.setDispatcher(dispatcherFactory.apply(c));
                    connections.add(c);
                    c.start();
                } catch (RuntimeException e) {
                    System.err.println("Verbindung abgelehnt: " + e);
                    if (c != null) c.close();
                    else try { s.close(); } catch (IOException ignored) {}
                }
            } catch (SocketException e) {
                // Beim stop() wird accept unterbrochen -> normal beenden.
                if (!running.get()) return;
                System.err.println("Socket-Fehler: " + e);
            } catch (IOException e) {
                if (!running.get()) return;
                System.err.println("Annehmen fehlgeschlagen: " + e);
            }
        }
    }
}
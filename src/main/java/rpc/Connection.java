package rpc;

import common.ConnectionException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bidirektionale JSON-RPC-Verbindung über einen Socket.
 * Transport/Middleware. Ordnet Antworten über die id den Anfragen zu und
 * führt eingehende Aufrufe ueber den Dispatcher aus.
 *
 * Vier Design-Entscheidungen (in der Abnahme erklären!):
 *   1. pending.put VOR send -> keine verlorene Antwort bei schnellem Server.
 *   2. Dispatch im Executor, nicht im Lese-Thread -> kein Deadlock.
 *   3. Ein Thread im Executor -> Rückrufe bleiben in Reihenfolge.
 *   4. writeLock beim Senden -> mehrere Threads schreiben auf einen Socket.
 */
public final class Connection implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Codec codec;
    private final Duration timeout;

    /** Wird von außen gesetzt (Henne-Ei-Problem mit GameServiceDispatcher). */
    private volatile Dispatcher dispatcher;

    private final Map<Long, CompletableFuture<JsonRpcMessage>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(0);
    private final Object writeLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rpc-dispatch");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public Connection(Socket socket, Codec codec, Duration timeout) throws IOException {
        if (socket == null || codec == null || timeout == null) {
            throw new IllegalArgumentException("Argumente dürfen nicht null sein");
        }
        this.socket = socket;
        this.codec = codec;
        this.timeout = timeout;
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        // Kleine Nachrichten sofort senden -> wichtig für die Zeitmessung.
        socket.setTcpNoDelay(true);
    }

    /** Setzt den Dispatcher. Muss vor start() passieren. */
    public void setDispatcher(Dispatcher d) {
        this.dispatcher = d;
    }

    /** Startet den Lese-Thread. Erst nach setDispatcher() aufrufen. */
    public void start() {
        Thread t = new Thread(this::readLoop, "rpc-reader-" + remoteHost() + ":" + remotePort());
        t.setDaemon(true);
        t.start();
    }



    /**
     * Sendet einen Request und blockiert bis zur Antwort.
     * RpcException: Fehlerantwort vom Server.
     * ConnectionException: Transportfehler/Timeout (Failure-Transparenz NICHT
     * erreicht - das ist der Beleg für die Abnahme).
     */
    public <T> T call(String method, Object params, Class<T> resultType) {
        if (closed.get()) throw new ConnectionException("Connection closed");

        long id = nextId.incrementAndGet();
        CompletableFuture<JsonRpcMessage> future = new CompletableFuture<>();
        // VOR dem Senden eintragen, sonst könnte die Antwort früher da sein.
        pending.put(id, future);

        try {
            send(JsonRpcMessage.request(id, method, codec.toNode(params)));
        } catch (RuntimeException e) {
            pending.remove(id);
            throw e;
        }

        JsonRpcMessage response;
        try {
            response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new ConnectionException("Timeout bei " + method);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(id);
            throw new ConnectionException("Unterbrochen bei " + method, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectionException ce) throw ce;
            throw new ConnectionException("Fehler bei " + method, cause);
        }

        if (response.rpcError() != null) {
            throw new RpcException(response.rpcError().code(), response.rpcError().message());
        }
        return codec.fromNode(response.result(), resultType);
    }

    /** Schickt eine Nachricht ohne Antwort (Rückruf). */
    public void sendNotification(String method, Object params) {
        if (closed.get()) throw new ConnectionException("Connection closed");
        send(JsonRpcMessage.notification(method, codec.toNode(params)));
    }


    private void readLoop() {
        try {
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                try {
                    handle(codec.decode(line));
                } catch (RpcException e) {
                    // Kaputte Zeile: Fehlerantwort mit id=null, weiterlesen.
                    trySendError(null, e.getCode(), e.getMessage());
                } catch (RuntimeException e) {
                    System.err.println("Fehler beim Verarbeiten einer Zeile: " + e);
                }
            }
        } catch (IOException e) {
            // Verbindungsende ist kein Fehler.
        } finally {
            close();
        }
    }

    private void handle(JsonRpcMessage m) {
        if (m.isResponse()) handleResponse(m);
        else                handleRequest(m);
    }

    private void handleResponse(JsonRpcMessage r) {
        if (r.id() == null) return; // ConcurrentHashMap verbietet null-Schlüssel
        CompletableFuture<JsonRpcMessage> future = pending.remove(r.id());
        if (future != null) future.complete(r);
    }

    private void handleRequest(JsonRpcMessage r) {
        try {
            executor.execute(() -> runDispatch(r));
        } catch (RejectedExecutionException ignored) {
            // Executor schon beendet -> Verbindung ist zu.
        }
    }

    private void runDispatch(JsonRpcMessage r) {
        try {
            if (dispatcher == null) {
                if (r.isRequest()) trySendError(r.id(), RpcException.METHOD_NOT_FOUND, "No dispatcher");
                return;
            }
            var result = dispatcher.dispatch(r.method(), r.params());
            if (r.isRequest()) send(JsonRpcMessage.success(r.id(), result));
        } catch (common.ConnectionException e) {
            // Verbindung weg, während der Dispatcher lief - nichts mehr zu tun.
        } catch (RpcException e) {
            if (r.isRequest()) trySendError(r.id(), e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Fehler im Dispatcher: " + e);
            if (r.isRequest()) trySendError(r.id(), RpcException.codeFor(e), e.getMessage());
        }
    }

    private void trySendError(Long id, int code, String message) {
        try {
            send(JsonRpcMessage.failure(id, code, message));
        } catch (ConnectionException ignored) {
            // Verbindung ist ohnehin zu.
        }
    }

    private void send(JsonRpcMessage m) {
        String line = codec.encode(m);
        ConnectionException toThrow = null;
        synchronized (writeLock) {
            if (closed.get()) throw new ConnectionException("Connection closed");
            try {
                writer.write(line);
                writer.write('\n');
                writer.flush();
            } catch (IOException e) {
                toThrow = new ConnectionException("Senden fehlgeschlagen", e);
            }
        }
        if (toThrow != null) {
            close();          // außerhalb der Sperre
            throw toThrow;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) {}
        executor.shutdownNow();
        pending.values().forEach(f ->
                f.completeExceptionally(new ConnectionException("Connection closed")));
        pending.clear();
        Dispatcher d = dispatcher;
        if (d != null) {
            try { d.onClose(); }
            catch (RuntimeException e) { System.err.println("Fehler in onClose: " + e); }
        }
    }

    public boolean isClosed() { return closed.get(); }
    public String remoteHost() { return socket.getInetAddress().getHostAddress(); }
    public int remotePort()    { return socket.getPort(); }
}
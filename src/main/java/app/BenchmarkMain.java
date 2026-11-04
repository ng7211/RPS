package app;

import common.*;
import rpc.*;
import server.*;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.List;

/**
 * Abnahmepunkt 6: Zeitvergleich lokaler vs. entfernter Aufruf.
 *
 * Misst getState(player) lokal (direkt auf GameServiceImpl) und entfernt
 * (ueber GameServiceProxy und Connection). Verwendet System.nanoTime().
 *
 * Aufruf: java app.BenchmarkMain
 */
public final class BenchmarkMain {

    private static final int WARMUP = 100;
    private static final int RUNS = 1000;
    private static final int REPEATS = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public static void main(String[] args) throws Exception {
        // ---------- Aufbau: lokales Spiel mit zwei Spielern ----------
        Codec codec = new Codec();

        // Lokaler Dienst (fuer den lokalen Vergleich)
        ListenerRegistry registry = new ListenerRegistry();
        Notifier notifier = new Notifier(registry);
        ClassicRules rules = new ClassicRules();
        Game game = new Game(3, rules);

        // Ein Dummy-Listener, damit register() durchlaeuft
        GameListener noop = new GameListener() {
            public void onOpponentJoined(Player p) {}
            public void onRoundFinished(RoundResult r) {}
            public void onGameFinished(GameState s) {}
        };
        LocalListenerFactory localFactory = new LocalListenerFactory();
        // Beide Spieler muessen bekannt sein, sonst wirft create
        Player alice = new Player(1, "alice");
        Player bob   = new Player(2, "bob");
        CallbackEndpoint dummyEp = new CallbackEndpoint("local", 0, "/local");
        localFactory.register(dummyEp, noop);

        GameServiceImpl localService = new GameServiceImpl(game, registry, notifier, localFactory);

        // Spieler anmelden (lokal, damit das Spiel laeuft)
        localService.register(alice, dummyEp);
        localService.register(bob, dummyEp);

        // ---------- Aufbau: RPC-Dienst ----------
        ConnectionListenerFactory factory = new ConnectionListenerFactory();
        GameServiceImpl remoteService = new GameServiceImpl(
                new Game(3, new ClassicRules()),
                new ListenerRegistry(),
                new Notifier(new ListenerRegistry()),
                factory);

        RpcServer server = new RpcServer(0, codec, TIMEOUT,
                connection -> new GameServiceDispatcher(remoteService, connection, factory, codec));
        server.start();

        Connection clientConnection = new Connection(
                new Socket("127.0.0.1", server.getPort()), codec, TIMEOUT);
        clientConnection.start();
        GameServiceProxy proxy = new GameServiceProxy(clientConnection);

        // Spieler auch auf dem RPC-Dienst anmelden (gleicher Name, gleiche ID)
        proxy.register(alice, new CallbackEndpoint("unused", 0, "/unused"));
        proxy.register(bob,   new CallbackEndpoint("unused", 0, "/unused"));

        try {
            // ---------- Warmup ----------
            for (int i = 0; i < WARMUP; i++) {
                localService.getState(alice);
                proxy.getState(alice);
            }

            // ---------- Lokal ----------
            long[] localNanos = new long[REPEATS];
            for (int r = 0; r < REPEATS; r++) {
                long start = System.nanoTime();
                for (int i = 0; i < RUNS; i++) {
                    localService.getState(alice);
                }
                localNanos[r] = System.nanoTime() - start;
            }

            // ---------- Entfernt ----------
            long[] remoteNanos = new long[REPEATS];
            for (int r = 0; r < REPEATS; r++) {
                long start = System.nanoTime();
                for (int i = 0; i < RUNS; i++) {
                    proxy.getState(alice);
                }
                remoteNanos[r] = System.nanoTime() - start;
            }

            // ---------- Auswertung ----------
            print("Lokal", localNanos);
            print("Entfernt", remoteNanos);

            long localMedian = median(localNanos);
            long remoteMedian = median(remoteNanos);
            double factor = (double) remoteMedian / localMedian;

            System.out.println();
            System.out.printf("Faktor entfernt/lokal: %.0fx%n", factor);
            System.out.printf("Lokal:   %.1f us/Aufruf%n", localMedian / (double) RUNS / 1000.0);
            System.out.printf("Entfernt: %.1f us/Aufruf%n", remoteMedian / (double) RUNS / 1000.0);
        } finally {
            clientConnection.close();
            server.stop();
        }
    }

    private static void print(String label, long[] nanos) {
        System.out.println(label + " (je " + RUNS + " Aufrufe):");
        for (int i = 0; i < nanos.length; i++) {
            System.out.printf("  Lauf %d: %d ns  (%.2f us/Aufruf)%n",
                    i + 1, nanos[i], nanos[i] / (double) RUNS / 1000.0);
        }
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
package app;

import rpc.Codec;
import rpc.ConnectionListenerFactory;
import rpc.GameServiceDispatcher;
import rpc.RpcServer;
import server.ClassicRules;
import server.Game;
import server.GameServiceImpl;
import server.ListenerRegistry;
import server.Notifier;

import java.io.IOException;
import java.time.Duration;

/**
 * Startet den Server. Baut die lokale Spiellogik unveraendert auf und haengt
 * sie ueber GameServiceDispatcher an den RpcServer.
 *
 * <p>Aufruf: java app.ServerMain [port]      (Standard: 5000)
 */
public final class ServerMain {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_PORT = 5001;

    public static void main(String[] args) {
        int port = parsePort(args);

        try {
            // Schritt 2: Spiellogik wie in der lokalen Version
            Codec codec = new Codec();
            ListenerRegistry registry = new ListenerRegistry();
            Notifier notifier = new Notifier(registry);
            ClassicRules rules = new ClassicRules();
            Game game = new Game(3, rules);

            // Schritt 3: Factory für den verteilten Betrieb und der Dienst
            ConnectionListenerFactory factory = new ConnectionListenerFactory();
            GameServiceImpl service = new GameServiceImpl(game, registry, notifier, factory);

            // Schritt 4: Server mit Dispatcher-Factory
            RpcServer server = new RpcServer(port, codec, TIMEOUT,
                    connection -> new GameServiceDispatcher(service, connection, factory, codec));
            server.start();

            // Schritt 5: Ausgabe
            System.out.println("Server listening on port " + server.getPort());
            // Der Accept-Thread ist kein Daemon -> main kehrt zurück, JVM läuft weiter.
        } catch (IOException e) {
            System.err.println("Cannot start server: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Port aus args[0], Standardwert 5001, ungueltige Zahl mit Meldung. */
    private static int parsePort(String[] args) {
        if (args.length == 0) return DEFAULT_PORT;
        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[0] + " (using " + DEFAULT_PORT + ")");
            return DEFAULT_PORT;
        }
    }
}
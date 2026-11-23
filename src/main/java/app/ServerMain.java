package app;

import clock.VectorClock;
import rpc.Codec;
import rpc.GameServiceDispatcher;
import rpc.jms.JmsConnection;
import rpc.jms.JmsListenerFactory;
import rpc.jms.JmsRpcServer;
import server.ClassicRules;
import server.Game;
import server.GameServiceImpl;
import server.ListenerRegistry;
import server.Notifier;

/**
 * Startet den Server hinter dem Message Broker. Die Spiellogik wird unverändert
 * wie in der lokalen Version aufgebaut; nur Factory und Transport sind JMS.
 *
 * <p>Aufruf: java app.ServerMain [brokerUrl]   (Standard: tcp://localhost:61616)
 */
public final class ServerMain {

    private static final String DEFAULT_BROKER = "tcp://localhost:61616";

    public static void main(String[] args) {
        String brokerUrl = args.length > 0 ? args[0] : DEFAULT_BROKER;

        // Schritt 1: Codec und EINE Server-Uhr
        Codec codec = new Codec();
        VectorClock serverClock = new VectorClock("server");

        // Schritt 2: Transport für Notifications an die Clients
        JmsConnection notifierConnection;
        try {
            notifierConnection = new JmsConnection(brokerUrl, "server", serverClock, codec);
        } catch (RuntimeException e) {
            System.err.println("Cannot reach broker at " + brokerUrl + " (" + e.getMessage() + ")");
            System.exit(1);
            return;
        }

        // Schritt 3: Spiellogik wie in der lokalen Version
        ListenerRegistry registry = new ListenerRegistry();
        Notifier notifier = new Notifier(registry);
        Game game = new Game(3, new ClassicRules());

        // Schritt 4: JMS-Factory statt ConnectionListenerFactory, Dienst unverändert
        JmsListenerFactory factory = new JmsListenerFactory(notifierConnection);
        GameServiceImpl service = new GameServiceImpl(game, registry, notifier, factory);

        // Schritt 5: Skeleton und Server
        GameServiceDispatcher dispatcher = new GameServiceDispatcher(service, codec);
        JmsRpcServer server = new JmsRpcServer(brokerUrl, dispatcher, serverClock, codec, notifierConnection);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            notifierConnection.close();
        }));

        server.start();
        System.out.println("Server connected to " + brokerUrl + ", listening on " + JmsRpcServer.REQUEST_QUEUE);
        // JMS-Threads halten die JVM am Leben.
    }
}
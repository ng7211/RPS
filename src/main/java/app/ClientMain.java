package app;

import client.ConsoleUI;
import client.GameClient;
import client.GameListenerImpl;
import clock.VectorClock;
import common.dtos.CallbackEndpoint;
import common.interfaces.GameListener;
import common.interfaces.GameService;
import common.dtos.Player;
import rpc.Codec;
import rpc.client.GameListenerDispatcher;
import rpc.client.GameServiceProxy;
import rpc.jms.JmsConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Map;

/**
 * Startet einen Client gegen den Server über den Message Broker.
 *
 * <p>Aufruf: java app.ClientMain &lt;name&gt; [id] [brokerUrl]
 * <p>Beispiel: java app.ClientMain alice 1 tcp://192.168.0.10:61616
 */
public final class ClientMain {

    private static final String DEFAULT_BROKER = "tcp://localhost:61616";

    public static void main(String[] args) {
        //Argumente prüfen
        if (args.length < 1) {
            System.err.println("Usage: ClientMain <name> [id] [brokerUrl]");
            System.exit(1);
            return;
        }
        String name = args[0];
        String key = name.toLowerCase(Locale.ROOT);

        int id = 1;
        if (args.length >= 2) {
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid id: " + args[1]);
                System.exit(1);
                return;
            }
        }
        String brokerUrl = args.length >= 3 ? args[2] : DEFAULT_BROKER;

        //Codec, Uhr und Verbindung zum Broker
        Codec codec = new Codec();
        VectorClock clock = new VectorClock(key);
        JmsConnection connection;
        try {
            connection = new JmsConnection(brokerUrl, key, clock, codec);
        } catch (RuntimeException e) {
            System.err.println("Cannot reach broker at " + brokerUrl + " (" + e.getMessage() + ")");
            System.exit(1);
            return;
        }

        //Listener auf Client-Seite als Dispatcher eintragen (VOR register)
        GameListener listener = new GameListenerImpl(name, System.out::println);
        connection.setNotificationDispatcher(new GameListenerDispatcher(listener, codec));

        //Proxy, Player, echter Endpoint = eigene Event-Queue, GameClient
        GameService service = new GameServiceProxy(connection);
        Player player = new Player(id, name);
        CallbackEndpoint endpoint = new CallbackEndpoint("jms", 0, "/queue/game.events." + key);
        GameClient gameClient = new GameClient(service, player, endpoint);

        //ConsoleUI mit genau einem Eintrag
        //Mit Verzoegerungs-Steuerung fuer die Demo (Befehl: <name> delay <ms> <zug>)
        ConsoleUI ui = new ConsoleUI(
                Map.of(key, gameClient),
                new BufferedReader(new InputStreamReader(System.in)),
                System.out::println,
                connection::setNextDelay);
        ui.run();

        //Aufräumen
        connection.close();
    }
}
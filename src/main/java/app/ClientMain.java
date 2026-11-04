package app;

import client.ConsoleUI;
import client.GameClient;
import client.GameListenerImpl;
import common.CallbackEndpoint;
import common.GameListener;
import common.GameService;
import common.Player;
import rpc.Codec;
import rpc.Connection;
import rpc.GameListenerDispatcher;
import rpc.GameServiceProxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Startet einen Client gegen einen Server.
 *
 * <p>Aufruf: java app.ClientMain &lt;host&gt; &lt;port&gt; &lt;name&gt; [id]
 * <p>Beispiel: java app.ClientMain 192.168.0.10 5000 alice
 */
public final class ClientMain {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public static void main(String[] args) {
        // Schritt 1: Argumente pruefen
        if (args.length < 3) {
            System.err.println("Usage: ClientMain <host> <port> <name> [id]");
            System.exit(1);
            return;
        }
        String host = args[0];

        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[1]);
            System.exit(1);
            return;
        }

        String name = args[2];

        int id;
        if (args.length >= 4) {
            try {
                id = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid id: " + args[3]);
                System.exit(1);
                return;
            }
        } else {
            id = 1;
        }

        // Schritt 2: Socket oeffnen (hier wird die Firewall sichtbar)
        Socket socket;
        try {
            socket = new Socket(host, port);
        } catch (IOException e) {
            System.err.println("Cannot reach server at " + host + ":" + port
                    + " (" + e.getMessage() + ")");
            System.exit(1);
            return;
        }

        // Schritt 3: Codec und Connection
        Codec codec = new Codec();
        Connection connection;
        try {
            connection = new Connection(socket, codec, TIMEOUT);
        } catch (IOException e) {
            System.err.println("Cannot create connection: " + e.getMessage());
            return;
        }

        // Schritt 4: Listener auf Client-Seite als Dispatcher eintragen, DANN starten
        GameListener listener = new GameListenerImpl(name, System.out::println);
        connection.setDispatcher(new GameListenerDispatcher(listener, codec));
        connection.start();

        // Schritt 5: Proxy, Player, Platzhalter-Endpoint, GameClient
        GameService service = new GameServiceProxy(connection);
        Player player = new Player(id, name);
        // Platzhalter: Der Server baut aus der Connection einen echten Endpoint.
        CallbackEndpoint placeholder = new CallbackEndpoint(host, port, "/unused");
        GameClient gameClient = new GameClient(service, player, placeholder);

        // Schritt 6: ConsoleUI mit genau einem Eintrag (Name in Kleinbuchstaben)
        ConsoleUI ui = new ConsoleUI(
                Map.of(name.toLowerCase(Locale.ROOT), gameClient),
                new BufferedReader(new InputStreamReader(System.in)),
                System.out::println);
        ui.run();

        // Schritt 7: Aufraeumen
        connection.close();
    }
}
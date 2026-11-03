package app;

import client.ConsoleUI;
import client.GameClient;
import client.GameListenerImpl;
import common.CallbackEndpoint;
import common.GameService;
import common.Player;
import server.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class LocalMain {

    static void main(String[] args) {
        Consumer<String> output = System.out::println;
        Map<String, GameClient> clients = createClients(3, output);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        new ConsoleUI(clients, input, output).run();
    }

    public static Map<String, GameClient> createClients(int pointsToWin, Consumer<String> output) {
        ListenerRegistry listenerRegistry = new ListenerRegistry();
        Notifier notifier = new Notifier(listenerRegistry);
        Game game = new Game(pointsToWin, new ClassicRules());
        LocalListenerFactory factory = new LocalListenerFactory();
        GameServiceImpl service = new GameServiceImpl(game, listenerRegistry, notifier, factory);

        Map<String, GameClient> clients = new LinkedHashMap<>();
        clients.put("alice", createClient(1, "Alice", service, factory, output));
        clients.put("bob", createClient(2, "Bob", service, factory, output));
        return clients;
    }

    private static GameClient createClient(int id, String name, GameService service,
                                           LocalListenerFactory factory, Consumer<String> output) {
        Player player = new Player(id, name);
        CallbackEndpoint endpoint = new CallbackEndpoint("local", id, "/" + name.toLowerCase(Locale.ROOT));
        GameListenerImpl listener = new GameListenerImpl(name, output);
        factory.register(endpoint, listener);
        return new GameClient(service, player, endpoint);
    }
}

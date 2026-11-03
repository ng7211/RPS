// AI-GENERATED-START: JUnit 5 Tests für ConsoleUI - 03.11.2026
package client;

import common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConsoleUITest {

    private Map<String, GameClient> clients;
    private List<String> ausgaben;

    // Ein Dummy-GameClient, um die Abhängigkeiten im Test so gering wie möglich zu halten
    private static class DummyGameClient extends GameClient {
        boolean throwExceptionOnJoin = false;

        public DummyGameClient(Player player) {
            // Nulls für Service und Endpoint rufen beim echten Client Fehler hervor,
            // daher mocken wir nur das absolut Nötigste für diesen Test durch Überschreiben
            super(new GameService() {
                @Override public GameState register(Player p, CallbackEndpoint e) { return null; }
                @Override public MoveStatus submitMove(Player p, Move m) { return null; }
                @Override public GameState getState(Player p) { return null; }
                @Override public void leave(Player p) {}
            }, player, new CallbackEndpoint("dummy", 0, ""));
        }

        @Override
        public GameState join() {
            if (throwExceptionOnJoin) {
                // Simuliert z.B. eine AlreadyMovedException oder GameFullException
                throw new GameFullException("Test error");
            }
            return new GameState(new ArrayList<>(), 0, 0, 1, new ArrayList<>(), 2, GamePhase.WAITING, null, FinishReason.NONE);
        }
    }

    @BeforeEach
    void setUp() {
        ausgaben = new ArrayList<>();
        clients = new HashMap<>();
    }

    @Test
    void testKonstruktorExceptions() {
        BufferedReader input = new BufferedReader(new StringReader(""));

        // Null Parameter
        assertThrows(IllegalArgumentException.class, () -> new ConsoleUI(null, input, ausgaben::add));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleUI(clients, null, ausgaben::add));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleUI(clients, input, null));

        // Leerer Name
        clients.put("", new DummyGameClient(new Player(1,"Empty")));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleUI(clients, input, ausgaben::add));
        clients.clear();

        // Name mit Leerzeichen
        clients.put("a lice", new DummyGameClient(new Player(2,"Alice")));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleUI(clients, input, ausgaben::add));
        clients.clear();

        // Doppelter Name (ignoring case)
        clients.put("alice", new DummyGameClient(new Player(3,"Alice1")));
        clients.put("ALICE", new DummyGameClient(new Player(4,"Alice2")));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleUI(clients, input, ausgaben::add));
    }

    @Test
    void testRunExitKehrtZurueck() {
        BufferedReader input = new BufferedReader(new StringReader("exit\n"));
        ConsoleUI ui = new ConsoleUI(clients, input, ausgaben::add);

        // Methode darf nicht blockieren (Schleife bricht ab)
        assertDoesNotThrow(() -> ui.run());

        // Die letzte Ausgabe muss exakt "Bye." sein
        assertEquals("Bye.", ausgaben.getLast());
    }

    @Test
    void testZweimalAliceJoinFehlerBehandlung() {
        String commands = "alice join\nalice join\nexit\n";
        BufferedReader input = new BufferedReader(new StringReader(commands));

        DummyGameClient aliceClient = new DummyGameClient(new Player(1,"Alice"));
        clients.put("alice", aliceClient);

        ConsoleUI ui = new ConsoleUI(clients, input, ausgaben::add);

        // Wir simulieren, dass das Spiel beim ZWEITEN Aufruf einen Fehler wirft
        // (Da wir die Eingaben hier in einem Rutsch abarbeiten, überschreiben wir 
        // das join im DummyClient kurz)
        aliceClient = new DummyGameClient(new Player(2,"Alice")) {
            int joinCount = 0;
            @Override
            public GameState join() {
                joinCount++;
                if (joinCount == 2) throw new GameFullException("Already in game");
                return super.join();
            }
        };
        clients.put("alice", aliceClient);
        ui = new ConsoleUI(clients, input, ausgaben::add);

        ui.run();

        // Wir filtern nur die relevanten Game-Ausgaben heraus (ohne Help/Start-Text)
        List<String> gameOutputs = ausgaben.stream()
                .filter(s -> s.contains("[Alice]") || s.contains("Game error:"))
                .toList();

        assertTrue(gameOutputs.get(0).contains("Joined"), "Erste Zeile muss 'Joined' enthalten");
        assertTrue(gameOutputs.get(1).startsWith("Game error:"), "Zweite Ausgabe muss mit 'Game error:' beginnen");
        assertEquals("Bye.", ausgaben.getLast(), "Danach muss exit durchlaufen");
    }

    @Test
    void testUnbekannterSpielerCarol() {
        String commands = "carol join\nexit\n";
        BufferedReader input = new BufferedReader(new StringReader(commands));

        clients.put("alice", new DummyGameClient(new Player(1,"Alice")));
        clients.put("bob", new DummyGameClient(new Player(2,"Bob")));

        ConsoleUI ui = new ConsoleUI(clients, input, ausgaben::add);
        ui.run();

        // Wir suchen die Meldung für den unbekannten Spieler
        String msg = ausgaben.stream()
                .filter(s -> s.startsWith("Unknown player"))
                .findFirst()
                .orElse("");

        assertTrue(msg.contains("carol"), "Meldung muss carol enthalten");
        assertTrue(msg.contains("alice, bob"), "Meldung muss die bekannten Namen auflisten");
    }
}
// AI-GENERATED-END
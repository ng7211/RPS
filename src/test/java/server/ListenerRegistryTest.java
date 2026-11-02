// AI-GENERATED-START: JUnit 5 Tests für ListenerRegistry (Dummy-Update) - 02.11.2026
package server;

import common.GameListener;
import common.GameState;
import common.Player;
import common.RoundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ListenerRegistryTest {

    private ListenerRegistry registry;
    private Player player;
    private GameListener listener1;
    private GameListener listener2;

    // Eine kleine Hilfsklasse für den Test, die das Interface leer implementiert
    private static class DummyListener implements GameListener {
        @Override public void onOpponentJoined(Player p) {}
        @Override public void onRoundFinished(RoundResult r) {}
        @Override public void onGameFinished(GameState g) {}
    }

    @BeforeEach
    void setUp() {
        registry = new ListenerRegistry();
        player = new Player(1,"TestPlayer");

        listener1 = new DummyListener();
        listener2 = new DummyListener();
    }

    @Test
    void testEintragenDannFinden() {
        registry.put(player, listener1);
        Optional<GameListener> result = registry.find(player);

        assertTrue(result.isPresent(), "Listener muss gefunden werden");
        assertEquals(listener1, result.get(), "Der gleiche Listener kommt zurück");
    }

    @Test
    void testNieEingetragenenSpielerFinden() {
        Optional<GameListener> result = registry.find(player);

        assertTrue(result.isEmpty(), "Muss ein leeres Optional zurückgeben");
    }

    @Test
    void testEntfernenDannFinden() {
        registry.put(player, listener1);
        registry.remove(player);

        Optional<GameListener> result = registry.find(player);

        assertTrue(result.isEmpty(), "Leeres Optional nach Entfernen");
    }

    @Test
    void testEntfernenEinesUnbekanntenSpielers() {
        assertDoesNotThrow(() -> registry.remove(player), "Keine Exception beim Entfernen unbekannter Spieler");
    }

    @Test
    void testPutFuerDenselbenSpielerZweimal() {
        registry.put(player, listener1);
        registry.put(player, listener2);

        Optional<GameListener> result = registry.find(player);

        assertTrue(result.isPresent());
        assertEquals(listener2, result.get(), "Der zweite Listener ersetzt den ersten");
    }
}
// AI-GENERATED-END
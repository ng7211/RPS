// AI-GENERATED-START: JUnit 5 Tests für LocalListenerFactory - 02.11.2026
package server;

import common.CallbackEndpoint;
import common.GameListener;
import common.GameState;
import common.Player;
import common.RoundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LocalListenerFactoryTest {

    private LocalListenerFactory factory;
    private CallbackEndpoint endpoint;
    private GameListener dummyListener;

    // Ein minimaler Dummy-Listener, damit wir keine echten Netzwerk-Klassen brauchen
    private static class DummyListener implements GameListener {
        @Override public void onOpponentJoined(Player player) {}
        @Override public void onRoundFinished(RoundResult roundResult) {}
        @Override public void onGameFinished(GameState gameState) {}
    }

    @BeforeEach
    void setUp() {
        factory = new LocalListenerFactory();
        endpoint = new CallbackEndpoint("localhost", 8080, "/testpfad");
        dummyListener = new DummyListener();
    }

    @Test
    void testCreateMitNullWirftException() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(null),
                "Ein Null-Endpunkt muss bei create() abgefangen werden");
    }

    @Test
    void testCreateMitUnbekanntemEndpunktWirftException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> factory.create(endpoint),
                "Ein noch nicht registrierter Endpunkt muss eine Exception werfen");

        // Optional: Wir prüfen, ob deine detaillierte Fehlermeldung greift
        assertTrue(exception.getMessage().contains("localhost/8080//testpfad"));
    }

    @Test
    void testRegisterMitNullWirftException() {
        assertThrows(IllegalArgumentException.class, () -> factory.register(null, dummyListener),
                "Null für Endpunkt darf nicht registriert werden");

        assertThrows(IllegalArgumentException.class, () -> factory.register(endpoint, null),
                "Null für Listener darf nicht registriert werden");

        assertThrows(IllegalArgumentException.class, () -> factory.register(null, null),
                "Wenn beides null ist, muss es abgefangen werden");
    }

    @Test
    void testRegisterUndCreateErfolgreich() {
        // Erst registrieren
        factory.register(endpoint, dummyListener);

        // Dann abrufen
        GameListener result = factory.create(endpoint);

        assertEquals(dummyListener, result, "Die Factory muss exakt den registrierten Listener zurückgeben");
    }

    @Test
    void testRegisterUeberschreibtBestehendenListener() {
        GameListener neuerListener = new DummyListener();

        // Erster Eintrag
        factory.register(endpoint, dummyListener);

        // Zweiter Eintrag auf denselben Endpunkt
        factory.register(endpoint, neuerListener);

        GameListener result = factory.create(endpoint);

        assertEquals(neuerListener, result, "Ein neuer Listener für denselben Endpunkt muss den alten ersetzen");
    }
}
// AI-GENERATED-END
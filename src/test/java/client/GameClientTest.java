// AI-GENERATED-START: JUnit 5 Tests für GameClient - 03.11.2026
package client;

import common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class GameClientTest {

    // --- Ersatz-Service (Spy) ---
    private static class SpyGameService implements GameService {
        Player registeredPlayer;
        CallbackEndpoint registeredEndpoint;

        Player submittedMovePlayer;
        Move submittedMove;

        Player getStatePlayer;
        Player leavePlayer;

        GameState nextGameState;
        MoveStatus nextMoveStatus;
        RuntimeException exceptionToThrow;

        @Override
        public GameState register(Player player, CallbackEndpoint endpoint) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.registeredPlayer = player;
            this.registeredEndpoint = endpoint;
            return nextGameState;
        }

        @Override
        public MoveStatus submitMove(Player player, Move move) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.submittedMovePlayer = player;
            this.submittedMove = move;
            return nextMoveStatus;
        }

        @Override
        public GameState getState(Player player) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.getStatePlayer = player;
            return nextGameState;
        }

        @Override
        public void leave(Player player) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.leavePlayer = player;
        }
    }

    // --- Testaufbau ---

    private SpyGameService spyService;
    private Player testPlayer;
    private CallbackEndpoint testEndpoint;
    private GameClient client;
    private GameState dummyState;

    @BeforeEach
    void setUp() {
        spyService = new SpyGameService();
        testPlayer = new Player(1,"ClientPlayer");
        testEndpoint = new CallbackEndpoint("localhost", 1234, "/test");
        client = new GameClient(spyService, testPlayer, testEndpoint);

        // Ein Dummy-GameState für die Rückgaben des Ersatz-Services
        dummyState = new GameState(new ArrayList<>(), 0, 0, 1, new ArrayList<>(), 3, GamePhase.WAITING, null, FinishReason.NONE);
    }

    // --- Tests ---

    @Test
    void testKonstruktorNullJeParameter() {
        assertThrows(IllegalArgumentException.class, () -> new GameClient(null, testPlayer, testEndpoint), "Service null muss Exception werfen");
        assertThrows(IllegalArgumentException.class, () -> new GameClient(spyService, null, testEndpoint), "Player null muss Exception werfen");
        assertThrows(IllegalArgumentException.class, () -> new GameClient(spyService, testPlayer, null), "Endpoint null muss Exception werfen");
    }

    @Test
    void testJoinGibtRueckgabeUnveraendertDurch() {
        spyService.nextGameState = dummyState;

        GameState result = client.join();

        assertEquals(dummyState, result, "Die Rückgabe muss exakt durchgereicht werden");
        assertEquals(testPlayer, spyService.registeredPlayer, "Der Service muss mit dem eigenen Spieler aufgerufen werden");
        assertEquals(testEndpoint, spyService.registeredEndpoint, "Der Service muss mit dem eigenen Endpunkt aufgerufen werden");
    }

    @Test
    void testPlayGibtStatusUnveraendertDurch() {
        spyService.nextMoveStatus = MoveStatus.ROUND_COMPLETE; // Dummy-Antwort

        MoveStatus result = client.play(Move.ROCK);

        assertEquals(MoveStatus.ROUND_COMPLETE, result, "Die Rückgabe muss exakt durchgereicht werden");
        assertEquals(testPlayer, spyService.submittedMovePlayer, "Der Service muss mit dem eigenen Spieler aufgerufen werden");
        assertEquals(Move.ROCK, spyService.submittedMove, "Der übergebene Zug muss an den Service weitergereicht werden");
    }

    @Test
    void testRefreshHoltZustandFuerEigenenSpieler() {
        spyService.nextGameState = dummyState;

        GameState result = client.refresh();

        assertEquals(dummyState, result, "Die Rückgabe muss exakt durchgereicht werden");
        assertEquals(testPlayer, spyService.getStatePlayer, "Der Service muss mit dem eigenen Spieler aufgerufen werden");
    }

    @Test
    void testQuitRuftLeaveFuerEigenenSpielerAuf() {
        client.quit();

        assertEquals(testPlayer, spyService.leavePlayer, "Der Service muss mit dem eigenen Spieler aufgerufen werden");
    }

    @Test
    void testServiceWirftSpielExceptionWirdDurchgereicht() {
        // Wir simulieren eine beliebige Spiel-Exception, die der Server werfen könnte
        RuntimeException gameException = new RuntimeException("Test Exception vom Server");
        spyService.exceptionToThrow = gameException;

        // Prüfen, ob exakt diese Exception (ungefangen und unverpackt) beim Aufrufer landet
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> client.join());
        assertSame(gameException, thrown, "Dieselbe Exception muss beim Aufrufer ankommen");
    }

    @Test
    void testGetPlayer() {
        assertEquals(testPlayer, client.getPlayer(), "Muss den übergebenen Spieler zurückgeben");
    }
}
// AI-GENERATED-END
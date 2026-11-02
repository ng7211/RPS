// AI-GENERATED-START: JUnit 5 Tests für Notifier (7 Erwartungen) - 02.11.2026
package server;

import common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NotifierTest {

    private ListenerRegistry registry;
    private Notifier notifier;
    private Player p1;
    private Player p2;
    private SpyListener spy1;
    private SpyListener spy2;

    // Ein "Spion", der mitlauscht, ob und wie oft er aufgerufen wird
    private static class SpyListener implements GameListener {
        int opponentJoinedCount = 0;
        Player joinedOpponent = null;

        int roundFinishedCount = 0;
        RoundResult receivedResult = null;

        int gameFinishedCount = 0;
        GameState receivedState = null;

        boolean throwException = false; // Schalter für Fehler-Tests

        @Override
        public void onOpponentJoined(Player player) {
            if (throwException) throw new RuntimeException("Test Exception");
            opponentJoinedCount++;
            joinedOpponent = player;
        }

        @Override
        public void onRoundFinished(RoundResult roundResult) {
            if (throwException) throw new RuntimeException("Test Exception");
            roundFinishedCount++;
            receivedResult = roundResult;
        }

        @Override
        public void onGameFinished(GameState gameState) {
            if (throwException) throw new RuntimeException("Test Exception");
            gameFinishedCount++;
            receivedState = gameState;
        }
    }

    @BeforeEach
    void setUp() {
        registry = new ListenerRegistry();
        notifier = new Notifier(registry);

        p1 = new Player(1,"Alice");
        p2 = new Player(2,"Bob");

        spy1 = new SpyListener();
        spy2 = new SpyListener();

        registry.put(p1, spy1);
        registry.put(p2, spy2);
    }

    @Test
    void testOpponentJoinedNurEmpfaenger() {
        // P1 ist der Empfänger, P2 tritt bei
        notifier.opponentJoined(p1, p2);

        assertEquals(1, spy1.opponentJoinedCount, "Empfänger (P1) bekommt den Rückruf genau einmal");
        assertEquals(p2, spy1.joinedOpponent, "Empfänger bekommt den richtigen Gegner übermittelt");
        assertEquals(0, spy2.opponentJoinedCount, "Der Gegner (P2) selbst darf keinen Rückruf bekommen");
    }

    @Test
    void testRoundFinishedBeideGleichesErgebnis() {
        // Erzeuge ein Dummy-Runden-Ergebnis (Werte sind hier nur Platzhalter)
        RoundResult result = new RoundResult(1, p1, Move.ROCK, p2, Move.SCISSORS, Outcome.WINNERPLAYER1);

        notifier.roundFinished(result);

        assertEquals(1, spy1.roundFinishedCount, "P1 bekommt Rückruf genau einmal");
        assertEquals(1, spy2.roundFinishedCount, "P2 bekommt Rückruf genau einmal");
        assertEquals(result, spy1.receivedResult, "P1 bekommt das exakte Ergebnis");
        assertEquals(result, spy2.receivedResult, "P2 bekommt das exakte Ergebnis");
    }

    @Test
    void testGameFinishedAlleSpielerBekommenZustand() {
        // Erzeuge ein Dummy-GameState (Werte nach Game.java Signatur)
        List<Player> players = List.of(p1, p2);
        GameState state = new GameState(players, 1, 0, 2, new ArrayList<>(), 1, GamePhase.FINISHED, p1, FinishReason.SCORE);

        notifier.gameFinished(state);

        assertEquals(1, spy1.gameFinishedCount, "P1 bekommt den Endzustand genau einmal");
        assertEquals(1, spy2.gameFinishedCount, "P2 bekommt den Endzustand genau einmal");
        assertEquals(state, spy1.receivedState, "P1 bekommt den richtigen State");
        assertEquals(state, spy2.receivedState, "P2 bekommt den richtigen State");
    }

    @Test
    void testSpielerOhneListenerKeineException() {
        // Wir entfernen den Listener von P1 (er ist quasi offline)
        registry.remove(p1);

        RoundResult result = new RoundResult(1, p1, Move.ROCK, p2, Move.SCISSORS, Outcome.WINNERPLAYER1);

        // Soll keine Exception werfen
        assertDoesNotThrow(() -> notifier.roundFinished(result), "Fehlender Listener darf nicht zum Absturz führen");

        // P2 muss trotzdem benachrichtigt worden sein
        assertEquals(1, spy2.roundFinishedCount, "Der andere Spieler (P2) muss trotzdem benachrichtigt werden");
    }

    @Test
    void testListenerWirftExceptionKeinAbsturz() {
        // Listener von P1 wirft eine RuntimeException
        spy1.throwException = true;
        RoundResult result = new RoundResult(1, p1, Move.ROCK, p2, Move.SCISSORS, Outcome.WINNERPLAYER1);

        assertDoesNotThrow(() -> notifier.roundFinished(result), "Exception des Listeners darf den Aufrufer nicht crashen");

        // P2 muss trotzdem benachrichtigt worden sein
        assertEquals(1, spy2.roundFinishedCount, "Der andere Listener (P2) bekommt seinen Rückruf trotz Fehlers bei P1");
    }

    @Test
    void testNachFehlerAusRegistryEntfernt() {
        // Listener von P1 ist fehlerhaft
        spy1.throwException = true;
        RoundResult result = new RoundResult(1, p1, Move.ROCK, p2, Move.SCISSORS, Outcome.WINNERPLAYER1);

        notifier.roundFinished(result);

        // Der fehlerhafte Client muss aus der Registry geflogen sein (Toter Client)
        assertTrue(registry.find(p1).isEmpty(), "Der fehlerhafte Listener muss aus der Registry entfernt worden sein");
    }

    @Test
    void testNullKonstruktorWirftException() {
        assertThrows(IllegalArgumentException.class, () -> new Notifier(null), "Null im Konstruktor muss IllegalArgumentException werfen");
    }
}
// AI-GENERATED-END
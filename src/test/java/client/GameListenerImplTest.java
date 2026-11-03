// AI-GENERATED-START: JUnit 5 Tests für GameListenerImpl - 03.11.2026
package client;

import common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameListenerImplTest {

    private List<String> ausgaben;
    private GameListenerImpl listener;
    private Player me;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        ausgaben = new ArrayList<>();
        listener = new GameListenerImpl("TestPlayer", text -> ausgaben.add(text));

        me = new Player(1,"TestPlayer");
        alice = new Player(2,"Alice");
        bob = new Player(3, "Bob");
    }

    @Test
    void testKonstruktorNullUndLeer() {
        assertThrows(IllegalArgumentException.class, () -> new GameListenerImpl(null, text -> {}));
        assertThrows(IllegalArgumentException.class, () -> new GameListenerImpl("", text -> {}));
        assertThrows(IllegalArgumentException.class, () -> new GameListenerImpl("   ", text -> {}));
        assertThrows(IllegalArgumentException.class, () -> new GameListenerImpl("Name", null));
    }

    @Test
    void testOnOpponentJoined() {
        listener.onOpponentJoined(alice);

        assertEquals(1, ausgaben.size(), "Genau ein accept Aufruf");
        String text = ausgaben.getFirst();
        assertTrue(text.startsWith("[TestPlayer]"), "Muss mit dem Prefix beginnen");
        assertTrue(text.contains("Alice"), "Muss den Gegner nennen");
    }

    @Test
    void testOnRoundFinishedSiegP1() {
        RoundResult result = new RoundResult(1, alice, Move.ROCK, bob, Move.SCISSORS, Outcome.WINNERPLAYER1);
        listener.onRoundFinished(result);

        assertEquals(1, ausgaben.size(), "Genau ein accept Aufruf");
        String text = ausgaben.getFirst();
        assertTrue(text.contains("Alice plays ROCK"), "Alice plays ROCK fehlt");
        assertTrue(text.contains("Bob plays SCISSORS"), "Bob plays SCISSORS fehlt");
        assertTrue(text.contains("Round 1"), "Rundennummer fehlt");
        assertTrue(text.contains("Alice won this round"), "Sieger P1 fehlt");
    }

    @Test
    void testOnRoundFinishedSiegP2() {
        RoundResult result = new RoundResult(2, alice, Move.ROCK, bob, Move.PAPER, Outcome.WINNERPLAYER2);
        listener.onRoundFinished(result);

        assertEquals(1, ausgaben.size(), "Genau ein accept Aufruf");
        assertTrue(ausgaben.getFirst().contains("Bob won this round"), "Sieger P2 fehlt");
    }

    @Test
    void testOnRoundFinishedDraw() {
        RoundResult result = new RoundResult(3, alice, Move.ROCK, bob, Move.ROCK, Outcome.DRAW);
        listener.onRoundFinished(result);

        assertEquals(1, ausgaben.size(), "Genau ein accept Aufruf");
        assertTrue(ausgaben.getFirst().contains("Draw"), "Zweig DRAW fehlt");
    }

    @Test
    void testOnGameFinishedScore() {
        List<Player> players = List.of(alice, bob);
        // Konstruktor passend zu den abgefragten Methoden (players, score1, score2, round, history, pointsToWin, phase, winner, reason)
        GameState state = new GameState(players, 3, 1, 4, new ArrayList<>(), 3, GamePhase.FINISHED, alice, FinishReason.SCORE);

        listener.onGameFinished(state);

        assertEquals(1, ausgaben.size(), "Genau ein accept Aufruf");
        String text = ausgaben.getFirst();
        assertTrue(text.contains("reached 3 points"), "Text für SCORE fehlt");
        assertTrue(text.contains("Alice: 3 points"), "Punkte Spieler 1 fehlen");
        assertTrue(text.contains("Bob: 1 points"), "Punkte Spieler 2 fehlen");
    }

    @Test
    void testOnGameFinishedForfeit() {
        List<Player> players = List.of(alice, bob);
        GameState state = new GameState(players, 1, 0, 2, new ArrayList<>(), 3, GamePhase.FINISHED, alice, FinishReason.FORFEIT);

        listener.onGameFinished(state);

        assertEquals(1, ausgaben.size(), "Genau ein accept Aufruf");
        assertTrue(ausgaben.getFirst().contains("left the game"), "Zweig FORFEIT fehlt");
    }

    @Test
    void testOnGameFinishedEinSpielerWirftNicht() {
        // Simuliert den Fall, dass das Spiel in WAITING abgebrochen wird oder nur 1 Spieler drin ist
        List<Player> players = List.of(alice);
        GameState state = new GameState(players, 0, 0, 1, new ArrayList<>(), 3, GamePhase.FINISHED, alice, FinishReason.FORFEIT);

        assertDoesNotThrow(() -> listener.onGameFinished(state), "Darf bei Schleife mit nur einem Spieler nicht crashen");
    }

    @Test
    void testOnGameFinishedOhneSiegerEndgrundNone() {
        GameState state = new GameState(List.of(alice), 0, 0, 1, new ArrayList<>(), 3,
                GamePhase.FINISHED, null, FinishReason.NONE);

        assertDoesNotThrow(() -> listener.onGameFinished(state), "Darf ohne Sieger nicht crashen");
        assertEquals(1, ausgaben.size(), "Genau eine Ausgabe erwartet");
        assertTrue(ausgaben.getFirst().contains("Game has ended"), "Text muss das Spielende nennen");
    }

    @Test
    void testOnGameFinishedOhneSiegerScoreNutztFallback() {
        GameState state = new GameState(List.of(alice), 0, 0, 1, new ArrayList<>(), 3,
                GamePhase.FINISHED, null, FinishReason.SCORE);

        assertDoesNotThrow(() -> listener.onGameFinished(state), "Darf bei winner == null nicht crashen");
        assertEquals(1, ausgaben.size(), "Genau eine Ausgabe erwartet");
        assertTrue(ausgaben.getFirst().contains("nobody"), "Sollte 'nobody' als Ersatzname nutzen");
    }

    @Test
    void testOnGameFinishedOhneSiegerForfeitNutztFallback() {
        GameState state = new GameState(List.of(alice), 0, 0, 1, new ArrayList<>(), 3,
                GamePhase.FINISHED, null, FinishReason.FORFEIT);

        assertDoesNotThrow(() -> listener.onGameFinished(state), "Darf bei winner == null nicht crashen");
        assertEquals(1, ausgaben.size(), "Genau eine Ausgabe erwartet");
        assertTrue(ausgaben.getFirst().contains("nobody"), "Sollte 'nobody' als Ersatzname nutzen");
    }
}
// AI-GENERATED-END
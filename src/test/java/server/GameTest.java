// AI-GENERATED-START: JUnit 5 Tests für Game.java (Alle 20 Kriterien) - 01.11.2026
package server;

import common.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GameTest {

    private Game game;
    private RoundEvaluator evaluator;
    private Player p1;
    private Player p2;
    private Player p3;

    @BeforeEach
    void setUp() {
        // Wir nutzen die ClassicRules (oder einen simplen Dummy) als Evaluator
        evaluator = new ClassicRules();
        // 2 Punkte zum Sieg für die meisten Tests
        game = new Game(2, evaluator);

        p1 = new Player(1,"Alice");
        p2 = new Player(2,"Bob");
        p3 = new Player(3,"Charlie");
    }

    @Test
    void test01_neuesGame() {
        GameState snapshot = game.snapshot();
        assertEquals(GamePhase.WAITING, snapshot.gamePhase(), "Spiel muss in WAITING starten");
        assertTrue(snapshot.players().isEmpty(), "Noch keine Spieler");
        assertTrue(snapshot.roundHistory().isEmpty(), "Leere Historie");
        assertEquals(1, snapshot.currentRound(), "Startet in Runde 1");
    }

    @Test
    void test02_einSpielerTrittBei() {
        game.addPlayer(p1);
        assertEquals(GamePhase.WAITING, game.snapshot().gamePhase(), "Bleibt WAITING nach 1. Spieler");
    }

    @Test
    void test03_zweiterSpielerTrittBei() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        assertEquals(GamePhase.RUNNING, game.snapshot().gamePhase(), "Wechselt auf RUNNING bei 2 Spielern");
    }

    @Test
    void test04_dritterSpieler() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        assertThrows(GameFullException.class, () -> game.addPlayer(p3), "Dritter Spieler wirft GameFullException");
    }

    @Test
    void test05_zweiterSpielerMitGleichemNamen() {
        game.addPlayer(p1);
        Player doppelt = new Player(1,"Alice");
        assertThrows(NameTakenException.class, () -> game.addPlayer(doppelt), "Gleicher Name wirft NameTakenException");
    }

    @Test
    void test06_zugEinesUnbekanntenSpielers() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        assertThrows(UnknownPlayerException.class, () -> game.submitMove(p3, Move.ROCK), "Unbekannter Spieler darf nicht ziehen");
    }

    @Test
    void test07_zugInWaiting() {
        game.addPlayer(p1);
        assertThrows(InvalidMoveException.class, () -> game.submitMove(p1, Move.ROCK), "Zug in WAITING ist verboten");
    }

    @Test
    void test08_zugNull() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        assertThrows(InvalidMoveException.class, () -> game.submitMove(p1, null), "Zug 'null' wirft Exception");
    }

    @Test
    void test09_einZugKeinGegenzug() {
        game.addPlayer(p1);
        game.addPlayer(p2);

        Optional<RoundResult> result = game.submitMove(p1, Move.ROCK);

        assertTrue(result.isEmpty(), "Ergebnis leer, da Gegenzug fehlt");
        assertEquals(1, game.snapshot().currentRound(), "Zustand unverändert, bleibt Runde 1");
        // Nimmt an, dass Record-Parameter 2 "score1" oder ähnlich heißt. (Anpassen falls nötig)
        assertEquals(0, game.snapshot().player1Score(), "Punkte bleiben 0");
    }

    @Test
    void test10_derselbeSpielerZiehtZweimal() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.submitMove(p1, Move.ROCK);
        assertThrows(AlreadyMovedException.class, () -> game.submitMove(p1, Move.PAPER), "Zweimal ziehen verboten");
    }

    @Test
    void test11_beideZiehenSpieler1Gewinnt() {
        game.addPlayer(p1);
        game.addPlayer(p2);

        game.submitMove(p1, Move.ROCK);
        Optional<RoundResult> result = game.submitMove(p2, Move.SCISSORS);

        assertTrue(result.isPresent());
        assertEquals(Outcome.WINNERPLAYER1, result.get().outcome(), "Spieler 1 gewinnt");
        assertEquals(2, game.snapshot().currentRound(), "Runde 2 gestartet");
        assertEquals(1, game.snapshot().roundHistory().size(), "Eintrag in der Historie");
    }

    @Test
    void test12_beideZiehenUnentschieden() {
        game.addPlayer(p1);
        game.addPlayer(p2);

        game.submitMove(p1, Move.ROCK);
        Optional<RoundResult> result = game.submitMove(p2, Move.ROCK);

        assertTrue(result.isPresent());
        assertEquals(Outcome.DRAW, result.get().outcome());
        assertEquals(2, game.snapshot().currentRound());
        assertEquals(1, game.snapshot().roundHistory().size());
        assertEquals(0, game.snapshot().player1Score(), "Keine Punkte bei Unentschieden");
    }

    @Test
    void test13_spielerErreichtPointsToWin() {
        Game shortGame = new Game(1, evaluator); // 1 Punkt zum Sieg
        shortGame.addPlayer(p1);
        shortGame.addPlayer(p2);

        shortGame.submitMove(p1, Move.ROCK);
        shortGame.submitMove(p2, Move.SCISSORS);

        GameState snap = shortGame.snapshot();
        assertEquals(GamePhase.FINISHED, snap.gamePhase(), "Spiel FINISHED");
        assertEquals(p1, snap.winner(), "Sieger gesetzt");
        assertEquals(FinishReason.SCORE, snap.reasonForEnd(), "Grund ist SCORE");
    }

    @Test
    void test14_zugNachFinished() {
        Game shortGame = new Game(1, evaluator);
        shortGame.addPlayer(p1);
        shortGame.addPlayer(p2);
        shortGame.submitMove(p1, Move.ROCK);
        shortGame.submitMove(p2, Move.SCISSORS); // P1 gewinnt und Spiel ist beendet

        assertThrows(InvalidMoveException.class, () -> shortGame.submitMove(p1, Move.PAPER), "Zug nach FINISHED verboten");
    }

    @Test
    void test15_removePlayerInWaiting() {
        game.addPlayer(p1);
        game.removePlayer(p1);

        assertTrue(game.snapshot().players().isEmpty(), "Spieler weg");
        assertNull(game.snapshot().winner(), "Kein Sieger");
    }

    @Test
    void test16_removePlayerInRunning() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.submitMove(p1, Move.ROCK); // Offener Zug

        game.removePlayer(p1);

        GameState snap = game.snapshot();
        assertEquals(GamePhase.FINISHED, snap.gamePhase(), "Spiel vorbei");
        assertEquals(p2, snap.winner(), "Gegner ist Sieger");
        assertEquals(FinishReason.FORFEIT, snap.reasonForEnd(), "Grund FORFEIT");
        // Testet ob pendingMoves geleert wurden (Indirekt, da submitting now InvalidMove wirft, 
        // oder wir prüfen, ob history size 0 ist, offene Züge wurden verworfen)
        assertTrue(snap.roundHistory().isEmpty(), "Offene Züge verworfen");
    }

    @Test
    void test17_removePlayerNachFinished() {
        Game shortGame = new Game(1, evaluator);
        shortGame.addPlayer(p1);
        shortGame.addPlayer(p2);
        shortGame.submitMove(p1, Move.ROCK);
        shortGame.submitMove(p2, Move.SCISSORS); // FINISHED

        shortGame.removePlayer(p1); // Sollte ignoriert werden

        assertEquals(p1, shortGame.snapshot().winner(), "Ergebnis bleibt, P1 ist Sieger");
    }

    @Test
    void test18_snapshotAendernVerandertGameNicht() {
        game.addPlayer(p1);
        GameState snap1 = game.snapshot();

        // Versuche den Snapshot böswillig zu ändern
        snap1.players().clear();

        // Das eigentliche Game darf davon nicht betroffen sein
        GameState snap2 = game.snapshot();
        assertEquals(1, snap2.players().size(), "Änderung am Snapshot verändert Game nicht");
    }

    @Test
    void test19_snapshotBeiOffenemZug() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.submitMove(p1, Move.ROCK);

        GameState snap = game.snapshot();
        assertTrue(snap.roundHistory().isEmpty(), "Zug nirgends in der Historie sichtbar");
        // GameState hat in deinem Snapshot keine "pendingMoves", der Zug ist somit unsichtbar.
    }

    @Test
    void test20_ganzesSpielMitUnentschieden() {
        // Zielkriterium
        assertEquals(GamePhase.WAITING, game.snapshot().gamePhase());
        game.addPlayer(p1);
        game.addPlayer(p2);
        assertEquals(GamePhase.RUNNING, game.snapshot().gamePhase());

        // Runde 1: Unentschieden
        game.submitMove(p1, Move.ROCK);
        game.submitMove(p2, Move.ROCK);
        assertEquals(0, game.snapshot().player1Score());
        assertEquals(2, game.snapshot().currentRound());

        // Runde 2: P1 gewinnt
        game.submitMove(p1, Move.PAPER);
        game.submitMove(p2, Move.ROCK);

        // Runde 3: P1 gewinnt noch mal und erreicht 2 Punkte
        game.submitMove(p1, Move.ROCK);
        game.submitMove(p2, Move.SCISSORS);

        GameState finalSnap = game.snapshot();
        assertEquals(GamePhase.FINISHED, finalSnap.gamePhase());
        assertEquals(p1, finalSnap.winner());
        assertEquals(3, finalSnap.roundHistory().size());
    }
}
// AI-GENERATED-END
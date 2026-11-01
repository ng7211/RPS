// AI-GENERATED-START: JUnit 5 Tests für ClassicRules (9 Varianten & Null-Check) - 01.11.2026
package server;
import common.Move;
import common.Outcome;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

public class ClassicRulesTest {

    // Die zu testende Klasse wird einmal instanziiert
    private final ClassicRules rules = new ClassicRules();

    @Test
    public void testPlayer1Gewinnt() {
        // Stein schlägt Schere, Schere schlägt Papier, Papier schlägt Stein
        assertEquals(Outcome.WINNERPLAYER1, rules.evaluate(Move.ROCK, Move.SCISSORS), "Stein sollte Schere schlagen");
        assertEquals(Outcome.WINNERPLAYER1, rules.evaluate(Move.SCISSORS, Move.PAPER), "Schere sollte Papier schlagen");
        assertEquals(Outcome.WINNERPLAYER1, rules.evaluate(Move.PAPER, Move.ROCK), "Papier sollte Stein schlagen");
    }

    @Test
    public void testPlayer2Gewinnt() {
        // Jede Umkehrung ergibt den anderen Sieger
        assertEquals(Outcome.WINNERPLAYER2, rules.evaluate(Move.SCISSORS, Move.ROCK), "Umkehrung: Stein schlägt Schere");
        assertEquals(Outcome.WINNERPLAYER2, rules.evaluate(Move.PAPER, Move.SCISSORS), "Umkehrung: Schere schlägt Papier");
        assertEquals(Outcome.WINNERPLAYER2, rules.evaluate(Move.ROCK, Move.PAPER), "Umkehrung: Papier schlägt Stein");
    }

    @Test
    public void testUnentschieden() {
        // Gleiche Züge ergeben Unentschieden
        assertEquals(Outcome.DRAW, rules.evaluate(Move.ROCK, Move.ROCK), "Stein vs Stein ist Unentschieden");
        assertEquals(Outcome.DRAW, rules.evaluate(Move.PAPER, Move.PAPER), "Papier vs Papier ist Unentschieden");
        assertEquals(Outcome.DRAW, rules.evaluate(Move.SCISSORS, Move.SCISSORS), "Schere vs Schere ist Unentschieden");
    }

    @Test
    public void testNullWerteWerfenException() {
        // Testet die Exception-Behandlung in deiner evaluate-Methode
        assertThrows(IllegalStateException.class, () -> rules.evaluate(null, Move.ROCK), "Null für Spieler 1 sollte Exception werfen");
        assertThrows(IllegalStateException.class, () -> rules.evaluate(Move.ROCK, null), "Null für Spieler 2 sollte Exception werfen");
        assertThrows(IllegalStateException.class, () -> rules.evaluate(null, null), "Null für beide Spieler sollte Exception werfen");
    }
}
// AI-GENERATED-END
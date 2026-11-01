package server;

import common.Move;
import common.Outcome;

import java.util.*;

public class ClassicRules implements RoundEvaluator {

    private final Map<Move, Set<Move>> beats;

    //Regeln für das Spiel festlegen
    public ClassicRules() {
        EnumMap<Move, Set<Move>> table = new EnumMap<>(Move.class);
        table.put(Move.ROCK, EnumSet.of(Move.SCISSORS));
        table.put(Move.PAPER, EnumSet.of(Move.ROCK));
        table.put(Move.SCISSORS, EnumSet.of(Move.PAPER));
        this.beats = Collections.unmodifiableMap(table);
    }

    /**
     * Die Methode evaluate evaluiert nach jeder Runde wer gewonnen hat oder ob ein Unentschieden vorliegt.
     * @param first Der Zug von Player 1 (R/P/S)
     * @param second Der zug von Player 2 (R/P/S)
     * @return Outcome der Runde entweder Unentschieden/Spieler1 gewinnt/Spieler2 gewinnt
     */
    @Override
    public Outcome evaluate(Move first, Move second) {
        if (first == null || second == null) throw new IllegalStateException("Moves can't be null");
        if (first == second) return Outcome.DRAW;
        if (beats.get(first).contains(second)) {
            return Outcome.WINNERPLAYER1;
        } else {
            return Outcome.WINNERPLAYER2;
        }
    }
}

package common.dtos;

import common.enums.Move;
import common.enums.Outcome;

public record RoundResult(int roundNr, Player player1, Move player1Move, Player player2, Move player2Move, Outcome outcome) {
}

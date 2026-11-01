package common;

import java.util.List;

public record GameState(List<Player> players, int player1Score, int player2Score, int currentRound, List<RoundResult> roundHistory, int pointsToWin, GamePhase gamePhase, Player winner, FinishReason reasonForEnd) {
}

package client;

import common.*;

import java.util.List;
import java.util.function.Consumer;

public class GameListenerImpl implements GameListener {

    private final String playerName;
    private final Consumer<String> output;
    public GameListenerImpl(String playerName, Consumer<String> output) {
        if (playerName == null || playerName.isBlank() || output == null) throw new IllegalArgumentException("Player name or output is null");
        this.playerName = playerName;
        this.output = output;
    }

    /**
     * Zeigt Gegner beitritt auf der Konsole
     * @param opponent Gegner
     */
    @Override
    public void onOpponentJoined(Player opponent) {
        String text = "opponent " + opponent.name() + " has joined the game. The Game starts now.";
        say(text);
    }

    @Override
    public void onRoundFinished(RoundResult roundResult) {
        String text = "Round " + roundResult.roundNr() + ": " + roundResult.player1().name() + " plays " + roundResult.player1Move().name() + ", " + roundResult.player2().name() + " plays " + roundResult.player2Move().name() + ". " + describeOutcome(roundResult);
        say(text);
    }

    @Override
    public void onGameFinished(GameState gameState) {
        String winnerName = (gameState.winner() == null) ? "nobody" : gameState.winner().name();

        String text;
        if (gameState.reasonForEnd() == FinishReason.SCORE) {
            text = "Game has ended: " + winnerName + " has reached " + gameState.pointsToWin() + " points.";
        } else if (gameState.reasonForEnd() == FinishReason.FORFEIT) {
            text = "The opponent has left the game. " + winnerName + " wins.";
        } else {
            text = "Game has ended.";
        }

        List<Player> players = gameState.players();
        StringBuilder score = new StringBuilder();
        for (int i = 0; i < players.size() && i < 2; i++) {
            int points = (i == 0) ? gameState.player1Score() : gameState.player2Score();
            score.append(i == 0 ? " Final score: " : ", ")
                    .append(players.get(i).name()).append(": ").append(points).append(" points");
        }
        if (!score.isEmpty()) score.append(".");

        say(text + score);
    }

    /**
     * Fügt einen Präfix zum Text hinzu.
     * @param text ein übergebener Text.
     */
    private void say(String text) {
        text = "[" + playerName + "] " + text;

        output.accept(text);
    }

    private String moveName(Move move) {
        switch (move) {
            case ROCK, PAPER, SCISSORS -> {
                return move.name();
            }
        }
        return "";
    }

    private String describeOutcome(RoundResult result) {
        switch (result.outcome()) {
            case WINNERPLAYER1 -> {
                return result.player1().name() + " won this round.";
            }

            case WINNERPLAYER2 -> {
                return result.player2().name() + " won this round.";
            }

            case DRAW -> {
                return "Draw, no points to give.";
            }
        }
        return "";
    }
}

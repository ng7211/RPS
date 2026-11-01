package common;

public interface GameListener {

    void onOpponentJoined(Player player);
    void onRoundFinished(RoundResult roundResult);
    void onGameFinished(GameState gameState);
}

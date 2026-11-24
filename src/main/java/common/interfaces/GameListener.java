package common.interfaces;

import common.dtos.GameState;
import common.dtos.Player;
import common.dtos.RoundResult;

public interface GameListener {

    void onOpponentJoined(Player player);
    void onRoundFinished(RoundResult roundResult);
    void onGameFinished(GameState gameState);
}

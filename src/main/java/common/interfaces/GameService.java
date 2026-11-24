package common.interfaces;

import common.dtos.CallbackEndpoint;
import common.dtos.GameState;
import common.dtos.Player;
import common.enums.Move;
import common.enums.MoveStatus;

public interface GameService {
    GameState register(Player player, CallbackEndpoint callbackEndpoint);
    MoveStatus submitMove(Player player, Move move);
    GameState getState(Player player);
    void leave(Player player);
}

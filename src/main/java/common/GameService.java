package common;

public interface GameService {
    GameState register(Player player, CallbackEndpoint callbackEndpoint);
    MoveStatus submitMove(Player player, Move move);
    GameState getState(Player player);
    void leave(Player player);
}

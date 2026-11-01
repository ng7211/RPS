package common;

public interface GameService {

    GameState register(Player player, GameListener gameListener);
    Move submitMove(Player player, Move move);
    GameState getState(Player player);
    void leave(Player player);
}

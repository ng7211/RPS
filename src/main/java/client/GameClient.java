package client;

import common.*;

//Thin-Client (Keine Logik)
public class GameClient {

    private GameService gameService;
    private Player player;
    private CallbackEndpoint callbackEndpoint;

    public GameClient(GameService gameService, Player player, CallbackEndpoint callbackEndpoint) {
        if (gameService == null || callbackEndpoint == null || player == null) throw new IllegalArgumentException("GameService, CallbackEndpoint or Player is null");
        this.gameService = gameService;
        this.callbackEndpoint = callbackEndpoint;
        this.player = player;
    }

    public GameState join() {
        return gameService.register(player, callbackEndpoint);
    }

    public MoveStatus play(Move move) {
        return gameService.submitMove(player, move);
    }

    public GameState refresh() {
        return gameService.getState(player);
    }

    public void quit() {
        gameService.leave(player);
    }

    public Player getPlayer() {
        return player;
    }
}

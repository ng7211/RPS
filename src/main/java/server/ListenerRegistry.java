package server;

import common.GameListener;
import common.Player;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spieler und Listener in einer Map merken.
 */
public class ListenerRegistry {
    //Rückrufe und Anmeldung laufen parallel
    private ConcurrentHashMap<Player, GameListener> map = new ConcurrentHashMap<>();
    public void put(Player player, GameListener gameListener) {
        if (player == null || gameListener == null) throw new IllegalArgumentException();
        map.put(player, gameListener);
    }

    public Optional<GameListener> find(Player player) {
        if (player == null) throw new IllegalArgumentException();
        return Optional.ofNullable(map.get(player));
    }

    public void remove(Player player) {
        if (player == null) throw new IllegalArgumentException();
        map.remove(player);
    }
}

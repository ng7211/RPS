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

    /**
     * Spieler wird eingetragen
     * @param player der einzutragene Spieler
     * @param gameListener Listener, der die Rückrufe für diesen Endpunkt entgegennimmt
     */
    public void put(Player player, GameListener gameListener) {
        if (player == null || gameListener == null) throw new IllegalArgumentException("Player oder gamelistener ist null");
        map.put(player, gameListener);
    }

    /**
     * Diese Methode findet einen Spieler
     * @param player Spieler der gefunden werden soll
     * @return der GameListener vom Spieler
     */
    public Optional<GameListener> find(Player player) {
        if (player == null) throw new IllegalArgumentException("Player is null");
        return Optional.ofNullable(map.get(player));
    }

    /**
     * Diese Methode entfernt einen Spieler
     * @param player der Spieler der entfernt wird.
     */
    public void remove(Player player) {
        if (player == null) throw new IllegalArgumentException("Player is null");
        map.remove(player);
    }
}

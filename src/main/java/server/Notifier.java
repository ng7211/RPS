package server;

import common.GameListener;
import common.GameState;
import common.Player;
import common.RoundResult;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Rückrufe an die Spieler verteilen
 */
public class Notifier {
    private final ListenerRegistry listenerRegistry;

    public Notifier(ListenerRegistry listenerRegistry) {
        if (listenerRegistry == null) throw new IllegalArgumentException("ListenerRegistry is null");
        this.listenerRegistry = listenerRegistry;
    }

    /**
     * Recipient wird benachrichtigt, dass Gegner dem Spiel beigetreten ist
     * @param recipient der Spieler der im Spiel ist
     * @param opponent der Spieler der dem Spiel beitritt
     */
    public void opponentJoined(Player recipient, Player opponent) {
        notifyOne(recipient, "opponentJoined", listener -> listener.onOpponentJoined(opponent));
    }

    /**
     * Beide spieler werden benachrichtigt, dass die Runde vorbei ist
     * @param result Resultat der Runde
     */
    public void roundFinished(RoundResult result) {
        notifyOne(result.player1(), "roundFinished", listener -> listener.onRoundFinished(result));
        notifyOne(result.player2(), "roundFinished", listener -> listener.onRoundFinished(result));
    }

    /**
     * Beide Spieler werden benachrichtigt, wenn das Spiel vorbei ist
     * @param state Zustand des Spiels
     */
    public void gameFinished(GameState state) {
        for (Player player : state.players()) notifyOne(player, "gameFinished", listener -> listener.onGameFinished(state));
    }
    /**
     * Einem einzigen Spieler einen Rückruf zustellen, wenn das schiefläuft Meldung ausgeben
     * @param player An wen der Rückruf gehen soll
     * @param what Name für Meldung, um zu sehen, welcher Rückruf scheitert
     * @param action Anweisung was mit dem Listener getan wird
     */
    private void notifyOne(Player player, String what, Consumer<GameListener> action) {
        Optional<GameListener> gameListener = listenerRegistry.find(player);
        if (gameListener.isEmpty()) return;
        try {
            action.accept(gameListener.get());
        } catch (RuntimeException e) {
            System.err.println("Rückruf " + what + " an " + player.name() +" fehlgeschlagen: " + e.getMessage());
            //Toter client kostet nicht jedes Mal Zeit
            listenerRegistry.remove(player);
        }
    }
}

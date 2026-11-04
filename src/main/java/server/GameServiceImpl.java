package server;

import common.*;
import common.UnknownPlayerException;

import java.util.Optional;

public class GameServiceImpl implements GameService {

    private final Game game;
    private final ListenerRegistry registry;
    private final Notifier notifier;
    private final ListenerFactory factory;

    public GameServiceImpl(Game game, ListenerRegistry registry, Notifier notifier, ListenerFactory factory) {
        if (game == null || registry == null || notifier == null || factory == null) throw new IllegalArgumentException("At least one parameter is null in GameServiceImpl");
        this.game = game;
        this.registry = registry;
        this.notifier = notifier;
        this.factory = factory;
    }

    /**
     * Player in das Spiel registrieren und den Gegner informieren
     * @param player Spieler der registriert wird
     * @param endpoint Adresse, unter der der Client die Rückrufe empfängt
     * @return Der Spielzustand
     */
    @Override
    public GameState register(Player player, CallbackEndpoint endpoint) {
        if (player == null || endpoint == null) throw new IllegalArgumentException("Player or endpoint is null");

        GameListener listener = factory.create(endpoint);
        game.addPlayer(player);
        //Listener eintragen
        registry.put(player, listener);
        GameState state = game.snapshot();

        //Gegner über beitritt informieren
        if(state.gamePhase() == GamePhase.RUNNING) {
            Player firstPlayer = game.opponentOf(player);
            notifier.opponentJoined(firstPlayer, player);
        }

        return state;
    }

    /**
     * Diese Methode setzt den Zugstatus entweder man wartet auf den Zug des Gegners, nachdem ein
     * Player einen Zug ausgeführt hat oder die Runde ist beendet, da beide Spieler ihre Züge ausgeführt haben.
     * @param player Der Spieler der seinen Zug ausführt
     * @param move (Rock/Paper/Scissors)
     * @return Status über die Züge (WAITING_FOR_OPPONENT/ROUND_COMPLETE)
     */
    @Override
    public MoveStatus submitMove(Player player, Move move) {
        Optional<RoundResult> roundResult = game.submitMove(player, move);

        if (roundResult.isEmpty()) {
            return MoveStatus.WAITING_FOR_OPPONENT;
        }

        notifier.roundFinished(roundResult.get());

        if (game.isFinished()) {
            notifier.gameFinished(game.snapshot());
        }

        return MoveStatus.ROUND_COMPLETE;
    }

    /**
     * Diese Methode gibt den aktuellen Spielzustand zurück
     * @param player Mit player wird überprüft ob der Spieler im aktuellen Spiel ist
     * @return Der Spielzustand
     */
    @Override
    public GameState getState(Player player) {
        if (player == null) throw new IllegalArgumentException("Player is null");
        GameState state = game.snapshot();

        if (! state.players().contains(player)) throw new UnknownPlayerException("This player is not in current game");
        return state;
    }

    /**
     * Spieler verlässt Spiel, dabei wird geprüft, ob das Spiel schon zu Ende war, wenn nicht
     * gewinnt der Gegner. Gilt nur, wenn Spiel lief
     * @param player Spieler der das Spiel verlässt
     */
    @Override
    public void leave(Player player) {
        if (player == null) throw new IllegalArgumentException("Player is null");

        boolean wasFinished = game.isFinished();
        game.removePlayer(player);
        registry.remove(player);

        if (! wasFinished  && game.isFinished()) {
            notifier.gameFinished(game.snapshot());
        }
    }
}

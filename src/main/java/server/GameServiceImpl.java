package server;

import clock.VectorClock;
import common.*;
import common.UnknownPlayerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameServiceImpl implements GameService {

    private final Game game;
    private final ListenerRegistry registry;
    private final Notifier notifier;
    private final ListenerFactory factory;

    //angenommener Zug und der Zustand vor ihm
    private record MoveEvent(Player player, Move move, Map<String, Integer> vc, Game.Memento before) {}
    //alle angenommene Züge in der Reihenfolge, in der sie angewendet wurden
    private final List<MoveEvent> log = new ArrayList<>();

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
        Map<String, Integer> vc = CausalContext.current();
        if (vc == null) {
            return applyAndNotify(player, move); //lokaler Betrieb: kein Zurückspulen
        }
        synchronized (game) { // Prüfen + Zurückspulen als eine Einheit
            int insertAt = firstEventAfter(vc);
            if (insertAt < 0) {
                Game.Memento before = game.save();
                MoveStatus status = applyAndNotify(player, move);   // wirft bei ungueltigem Zug -> kein Log-Eintrag
                log.add(new MoveEvent(player, move, vc, before));
                return status;
            }
            return rollbackAndReplay(new MoveEvent(player, move, vc, null), insertAt);
        }
    }

    /**
     * Sucht das früheste bereits angewendete Ereignis, das kausal nach dem neuen Zug liegt
     * (neuer Zeitstempel BEFORE altem). Dann kam der neue Zug verspaetet an.
     *
     * @return Index im Log oder -1, wenn der Zug nicht verspaetet ist
     */
    private int firstEventAfter(Map<String, Integer> vc) {
        for (int i = 0; i < log.size(); i++) {
            if (VectorClock.compare(vc, log.get(i).vc()) == VectorClock.Order.BEFORE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Spult auf den Zustand vor dem ersten kausal späteren Ereignis zurück, wendet den
     * verspäteten Zug an seiner richtigen Stelle an und spielt die späteren Züge neu ab.
     * Züge, die in der richtigen Reihenfolge ungültig sind, werden verworfen.
     * Danach erhalten beide Spieler die korrigierten Rundenergebnisse.
     */
    private MoveStatus rollbackAndReplay(MoveEvent late, int insertAt) {
        MoveEvent first = log.get(insertAt);
        System.out.printf("[server] ROLLBACK: %s %s mit VC %s kam verspätet (kausal vor %s %s mit VC %s)%n",
                late.player().name(), late.move(), late.vc(), first.player().name(), first.move(), first.vc());
        System.out.printf("[server] ROLLBACK: spule zurück auf Runde %d%n", first.before().currentRound());

        List<MoveEvent> replay = new ArrayList<>();
        replay.add(late);
        replay.addAll(log.subList(insertAt, log.size()));
        log.subList(insertAt, log.size()).clear();

        int roundsBefore = first.before().currentRound();
        game.restore(first.before());

        List<RoundResult> corrected = new ArrayList<>();
        RuntimeException lateError = null;
        int lateRound = -1;

        for (MoveEvent e : replay) {
            Game.Memento before = game.save();
            int roundOfMove = game.snapshot().currentRound();
            try {
                game.submitMove(e.player(), e.move()).ifPresent(corrected::add);
                log.add(new MoveEvent(e.player(), e.move(), e.vc(), before));
                System.out.printf("[server] REPLAY  %s %s (Runde %d) angewendet%n", e.player().name(), e.move(), roundOfMove);
                if (e == late) lateRound = roundOfMove;
            } catch (RuntimeException ex) {
                System.out.printf("[server] REPLAY  %s %s verworfen: %s%n", e.player().name(), e.move(), ex.getMessage());
                if (e == late) lateError = ex;
            }
        }

        //Korrigierte Ergebnisse ab der zurückgespulten Runde an beide Spieler
        for (RoundResult r : corrected) {
            if (r.roundNr() >= roundsBefore) notifier.roundFinished(r);
        }
        GameState state = game.snapshot();
        System.out.printf("[server] ROLLBACK fertig: Stand %d:%d, Runde %d, Phase %s%n",
                state.player1Score(), state.player2Score(), state.currentRound(), state.gamePhase());
        if (game.isFinished()) {
            notifier.gameFinished(state);
        }

        if (lateError != null) throw lateError; // verspäteter Zug selbst ungültig
        boolean roundDone = game.isFinished() || state.currentRound() > lateRound;
        return roundDone ? MoveStatus.ROUND_COMPLETE : MoveStatus.WAITING_FOR_OPPONENT;
    }

    private void clearLog() {
        synchronized (game) {
            log.clear();
        }
    }

    /**
     *  Zug anwenden und bei Rundenende die Spieler benachrichtigen.
     */
    private MoveStatus applyAndNotify(Player player, Move move) {
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

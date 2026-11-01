package server;

import common.*;

import java.util.*;

public class Game {
    private final int pointsToWin;
    private final RoundEvaluator evaluator;
    private final List<Player> players = new ArrayList<>(2);
    private final Map<Player, Move> pendingMoves = new HashMap<>();
    private final Map<Player, Integer> scores = new HashMap<>();
    private final List<RoundResult> history = new ArrayList<>();
    private GamePhase phase = GamePhase.WAITING;
    private int currentRound = 1;
    private Player winner = null;
    private FinishReason finishReason = FinishReason.NONE;



    public Game(int pointsToWin, RoundEvaluator evaluator) {
        if (pointsToWin < 1 || evaluator == null) throw new IllegalArgumentException("Points to win is too small or evaluator is null");
        this.pointsToWin = pointsToWin;
        this.evaluator = evaluator;
    }

    /**
     * Die Methode ermöglicht es einer Person dem Spiel beizutreten
     * @param player Person die dem Spiel beitreten will
     */
    public synchronized void addPlayer(Player player) {
        if (player == null) throw new IllegalArgumentException("Player is null");
        if (players.size() >= 2) throw new GameFullException("Game is already full, you can't join!");
        if (players.stream().anyMatch(p -> p.name().equals(player.name()))) throw new NameTakenException("Name is already taken!");

        players.add(player);
        scores.put(player, 0);

        if (players.size() == 2) phase = GamePhase.RUNNING;
    }

    /**
     * Diese Methode nimmt die Züge der Spieler an und evaluiert die Züge anschließend.
     * @param player Person die gerade ein Zug ausführt
     * @param move der Zug R/P/S
     * @return Entweder wird nichts zurückgegeben oder ein Rundenresultat
     */
    public synchronized Optional<RoundResult> submitMove(Player player, Move move) {
        requireKnown(player);
        if (phase != GamePhase.RUNNING) throw new InvalidMoveException("Move is invalid because the game is not running!");
        if (move == null) throw new InvalidMoveException("The move is invalid!");
        if (pendingMoves.containsKey(player)) throw new AlreadyMovedException("You have already made your move!");

        pendingMoves.put(player, move);

        if (pendingMoves.size() < 2) {
            return Optional.empty();
        } else {
            return Optional.of(evaluateRound());
        }
    }

    /**
     * Diese Methode löscht einen Spieler aus dem Spiel
     * @param player der Spieler der das Spiel verlassen will
     */
    public synchronized void removePlayer(Player player) {
        if (phase == GamePhase.FINISHED) return;

        requireKnown(player);

        if (phase == GamePhase.WAITING){
            players.remove(player);
            scores.remove(player);
        }

        if (phase == GamePhase.RUNNING) {
            Player otherPlayer = opponentOf(player);
            //Other Player is the winner because player left game
            finish(otherPlayer, FinishReason.FORFEIT);
        }
    }

    private int scoreAt(int position) {
        if (position >= players.size()) return 0;
        return scores.get(players.get(position));
    }

    public synchronized GameState snapshot() {
        List<Player> playersCopy = new ArrayList<>(players);
        List<RoundResult> historyCopy = new ArrayList<>(history);
        return new GameState(playersCopy, scoreAt(0), scoreAt(1), currentRound,
                historyCopy, pointsToWin, phase, winner, finishReason);
    }

    public synchronized Player opponentOf(Player player) {
        if (players.size() != 2) throw new IllegalStateException();
        requireKnown(player);

        int indexOfGivenPlayer = players.indexOf(player);
        int indexOfOpponent = (indexOfGivenPlayer == 0) ? 1 : 0;

        return players.get(indexOfOpponent);
    }

    public synchronized boolean isFinished() {
        return phase == GamePhase.FINISHED;
    }

    private void requireKnown(Player player) {
        if (! players.contains(player)) {
            throw new UnknownPlayerException("Player does not exist!");
        }
    }

    private RoundResult evaluateRound() {
        Player player1 = players.getFirst();
        Player player2 = players.getLast();
        Move movePlayer1 = pendingMoves.get(player1);
        Move movePlayer2 = pendingMoves.get(player2);

        Outcome outcome = evaluator.evaluate(movePlayer1, movePlayer2);
        Player playerThatWonRound = null;
        if (outcome == Outcome.WINNERPLAYER1) playerThatWonRound = player1;
        if (outcome == Outcome.WINNERPLAYER2) playerThatWonRound = player2;

        if (outcome != Outcome.DRAW) {
            scores.replace(playerThatWonRound, scores.get(playerThatWonRound) + 1);
        }

        RoundResult roundResult = new RoundResult(currentRound, player1, movePlayer1, player2, movePlayer2, outcome);
        history.add(roundResult);
        pendingMoves.clear();

        if (playerThatWonRound != null) {
            if (scores.get(playerThatWonRound) >= pointsToWin) {
                finish(playerThatWonRound, FinishReason.SCORE);
            }
        }
        if (phase != GamePhase.FINISHED) {
            currentRound++;
        }

        return roundResult;
    }

    private void finish(Player winner, FinishReason finishReason) {
        if (winner == null || finishReason == FinishReason.NONE) throw new IllegalArgumentException();
        if (phase == GamePhase.FINISHED) return;
        this.winner = winner;
        this.finishReason = finishReason;
        phase = GamePhase.FINISHED;
        pendingMoves.clear();
    }
}
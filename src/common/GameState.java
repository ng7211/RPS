package common;

import java.util.List;

public class GameState {

    public enum Gamephase {
        WAITING,
        RUNNING,
        FINISHED
    }
    private Player player;
    private int scores;
    private int roundNr;
    private List<RoundResult> roundHistory;
    private int bestOfN;
}

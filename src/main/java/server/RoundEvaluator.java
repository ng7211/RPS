package server;

import common.Move;
import common.Outcome;

public interface RoundEvaluator {
    Outcome evaluate(Move first, Move second);
}

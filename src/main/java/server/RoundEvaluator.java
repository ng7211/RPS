package server;

import common.enums.Move;
import common.enums.Outcome;

public interface RoundEvaluator {
    Outcome evaluate(Move first, Move second);
}

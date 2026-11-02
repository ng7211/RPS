package server;

import common.CallbackEndpoint;
import common.GameListener;

public interface ListenerFactory {
    GameListener create(CallbackEndpoint callbackEndpoint);
}

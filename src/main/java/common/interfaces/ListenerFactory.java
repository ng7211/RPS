package common.interfaces;

import common.dtos.CallbackEndpoint;

public interface ListenerFactory {
    GameListener create(CallbackEndpoint callbackEndpoint);
}

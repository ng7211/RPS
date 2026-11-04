package common;

public interface ListenerFactory {
    GameListener create(CallbackEndpoint callbackEndpoint);
}

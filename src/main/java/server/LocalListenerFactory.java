package server;

import common.CallbackEndpoint;
import common.GameListener;
import common.ListenerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class LocalListenerFactory implements ListenerFactory {
    //Falls Registrierung und Aufruf parallel laufen (Threadsicher)
    private final ConcurrentHashMap<CallbackEndpoint, GameListener> map = new ConcurrentHashMap<>();

    /**
     * Liefert vorbereiteten Listener
     * @param callbackEndpoint Adresse, unter der der Client die Rückrufe empfängt
     * @return einen GameListener
     */
    @Override
    public GameListener create(CallbackEndpoint callbackEndpoint) {
        if (callbackEndpoint == null) throw new IllegalArgumentException("CallbackEndpoint is null");

        GameListener listener = map.get(callbackEndpoint);
        if (listener == null) throw new IllegalArgumentException("Unknown endpoint: " + callbackEndpoint.host() + "/" + callbackEndpoint.port() + "/" + callbackEndpoint.path());
        return listener;
    }

    /**
     * Merkt sich den Listener für einen Endpunkt
     * @param callbackEndpoint Adresse, unter der der Client die Rückrufe empfängt
     * @param listener Listener, der die Rückrufe für diesen Endpunkt entgegennimmt
     */
    public void register(CallbackEndpoint callbackEndpoint, GameListener listener) {
        if (callbackEndpoint == null || listener == null)  throw new IllegalArgumentException("Endpoint or listener is null");
        map.put(callbackEndpoint, listener);
    }
}

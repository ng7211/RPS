package rpc;

import common.CallbackEndpoint;
import common.GameListener;
import common.ListenerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Merkt sich Verbindungen unter einer Kennung und Fabrik die aus CallbackEndpoint einen GameListenerProxy macht.
 */

public class ConnectionListenerFactory implements ListenerFactory {

    private ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    private AtomicLong counter = new AtomicLong(); //Long zahl threadsicher und atomar bearbeiten
    @Override
    public GameListener create(CallbackEndpoint callbackEndpoint) {
        if (callbackEndpoint == null) throw new IllegalArgumentException();

        if (callbackEndpoint.path() == null || !callbackEndpoint.path().startsWith("/conn/")) throw new IllegalArgumentException();

        //Kennung aus Pfad extrahieren
        String id = callbackEndpoint.path().substring("/conn/".length());
        if (connections.get(id) == null) {
            throw new IllegalArgumentException("Unknown endpoint: " + callbackEndpoint);
        }

        return new GameListenerProxy(connections.get(id));
    }

    public synchronized String attach(Connection c) {
        for (Map.Entry<String, Connection> m : connections.entrySet()) {
            if (m.getValue() == c) return m.getKey();
        }

        String id = String.valueOf(counter.incrementAndGet());
        connections.put(id, c);
        return id;
    }

    public void detach(String id) {
        if (id == null) return;
        connections.remove(id);
    }
}

package rpc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Nahtstelle zwischen Transport (Connection) und Fachlogik.
 * Fachbegriff: Server-Stub (Skeleton).
 */
public interface Dispatcher {

    JsonNode dispatch(String method, JsonNode params);

    default void onClose() {
    }
}
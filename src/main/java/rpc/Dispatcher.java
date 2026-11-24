package rpc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Nahtstelle zwischen Transport (z.B. JmsConnection, JmsRpcServer) und Fachlogik.
 * Skeleton
 */
public interface Dispatcher {

    JsonNode dispatch(String method, JsonNode params);

    default void onClose() {
    }
}
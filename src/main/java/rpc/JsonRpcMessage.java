package rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Eine JSON-RPC-2.0-Nachricht in allen drei Formen:
 *
 * <pre>
 * Request:      method != null, id != null
 * Notification: method != null, id == null
 * Response:     method == null
 * </pre>
 *
 * Das Feld jsonrpc wird nicht gespeichert: Es ist immer "2.0". Der Codec
 * setzt es beim Senden und prueft es beim Empfangen.
 */
public record JsonRpcMessage(
        String method,
        JsonNode params,
        JsonNode result,
        RpcError rpcError,
        Long id) {

    public record RpcError(int code, String message) {}

    public static JsonRpcMessage request(long id, String method, JsonNode params) {
        return new JsonRpcMessage(method, params, null, null, id);
    }

    public static JsonRpcMessage notification(String method, JsonNode params) {
        return new JsonRpcMessage(method, params, null, null, null);
    }

    public static JsonRpcMessage success(Long id, JsonNode result) {
        return new JsonRpcMessage(null, null,
                result == null ? NullNode.getInstance() : result, null, id);
    }

    public static JsonRpcMessage failure(Long id, int code, String message) {
        return new JsonRpcMessage(null, null, null, new RpcError(code, message), id);
    }

    public boolean isRequest()      { return method != null && id != null; }
    public boolean isNotification() { return method != null && id == null; }
    public boolean isResponse()     { return method == null; }
}
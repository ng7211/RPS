package rpc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Test-Transport ohne Netzwerk und ohne Broker: schickt jeden Aufruf als echten
 * JSON-Text durch den Codec an einen Dispatcher und die Antwort wieder zurueck.
 * So werden Proxy, Codec (Drahtformat) und Dispatcher zusammen getestet.
 */
final class LoopbackTransport implements RpcTransport {

    private final Dispatcher dispatcher;
    private final Codec codec = new Codec();
    private long nextId = 0;

    LoopbackTransport(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public <T> T call(String method, Map<String, Object> params, Class<T> resultType) {
        // Client -> "Leitung" -> Server
        String requestLine = codec.encode(JsonRpcMessage.request(++nextId, method, codec.toNode(params)));
        JsonRpcMessage request = codec.decode(requestLine);

        JsonRpcMessage response;
        try {
            JsonNode result = dispatcher.dispatch(request.method(), request.params());
            response = JsonRpcMessage.success(request.id(), result);
        } catch (RpcException e) {
            response = JsonRpcMessage.failure(request.id(), e.getCode(), e.getMessage());
        }

        // Server -> "Leitung" -> Client
        JsonRpcMessage received = codec.decode(codec.encode(response));
        if (received.rpcError() != null) {
            throw new RpcException(received.rpcError().code(), received.rpcError().message());
        }
        return codec.fromNode(received.result(), resultType);
    }

    @Override
    public <T> CompletableFuture<T> callAsync(String method, Map<String, Object> params, Class<T> resultType) {
        try {
            return CompletableFuture.completedFuture(call(method, params, resultType));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

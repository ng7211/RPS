package rpc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Transport fuer Client-Stubs. Der Proxy kennt nur dieses Interface,
 * nicht die konkrete Technik (JMS, frueher TCP).
 */
public interface RpcTransport {

    /** Synchroner Aufruf. Fehler-Antworten kommen als RpcException, Transportfehler als ConnectionException. */
    <T> T call(String method, Map<String, Object> params, Class<T> resultType);

    /** Asynchroner Aufruf. Die Future endet bei einer Fehler-Antwort mit RpcException. */
    <T> CompletableFuture<T> callAsync(String method, Map<String, Object> params, Class<T> resultType);
}

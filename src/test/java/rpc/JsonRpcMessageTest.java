package rpc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer die Fabrikmethoden und Praedikate von JsonRpcMessage.
 *
 * Zweck: Die Klasse ist das Nachrichtenformat des Protokolls. Die Fabriken
 * legen fest, welche Felder gesetzt sind und welche null bleiben. Ein Fehler
 * hier (z.B. success(1, null) liefert null statt NullNode) faellt sonst erst
 * tief in Codec oder Connection auf.
 */
class JsonRpcMessageTest {

    private final Codec codec = new Codec();

    // ---------------------------------------------------------------- request

    @Test
    void requestSetsMethodParamsAndId() {
        JsonNode params = codec.newParams().put("x", 1);

        JsonRpcMessage m = JsonRpcMessage.request(7, "register", params);

        assertEquals("register", m.method());
        assertSame(params, m.params());
        assertEquals(7L, m.id());
        assertNull(m.result());
        assertNull(m.rpcError());
    }

    @Test
    void requestIsRequestNotNotificationNotResponse() {
        JsonRpcMessage m = JsonRpcMessage.request(1, "x", null);

        assertTrue(m.isRequest());
        assertFalse(m.isNotification());
        assertFalse(m.isResponse());
    }

    // ---------------------------------------------------------------- notification

    @Test
    void notificationHasNoId() {
        JsonRpcMessage m = JsonRpcMessage.notification("onRoundFinished", null);

        assertEquals("onRoundFinished", m.method());
        assertNull(m.id());
        assertNull(m.params());
        assertNull(m.result());
        assertNull(m.rpcError());
    }

    @Test
    void notificationIsNotRequest() {
        // Wichtig: isRequest() ist true, weil ein Methodenname da ist.
        // Die Unterscheidung Request/Notification macht isNotification() über die id.
        JsonRpcMessage m = JsonRpcMessage.notification("x", null);

        assertFalse(m.isRequest());
        assertTrue(m.isNotification());
        assertFalse(m.isResponse());
    }

    // ---------------------------------------------------------------- success

    @Test
    void successWithResult() {
        JsonNode result = codec.toNode("ok");

        JsonRpcMessage m = JsonRpcMessage.success(3L, result);

        assertNull(m.method());
        assertNull(m.params());
        assertSame(result, m.result());
        assertNull(m.rpcError());
        assertEquals(3L, m.id());
    }

    @Test
    void successWithNullResultUsesNullNode() {
        // JSON-RPC verlangt bei Erfolg das Feld result. "Nichts" heisst
        // JSON-null, nicht "Feld fehlt". Deshalb muss NullNode drinstehen,
        // nicht Java-null.
        JsonRpcMessage m = JsonRpcMessage.success(1L, null);

        assertNotNull(m.result(), "result darf nicht Java-null sein");
        assertTrue(m.result().isNull(), "result muss NullNode sein");
        assertNull(m.rpcError());
        assertEquals(1L, m.id());
    }

    @Test
    void successIsResponse() {
        JsonRpcMessage m = JsonRpcMessage.success(1L, null);

        assertTrue(m.isResponse());
        assertFalse(m.isRequest());
        assertFalse(m.isNotification());
    }

    // ---------------------------------------------------------------- failure

    @Test
    void failureSetsErrorAndId() {
        JsonRpcMessage m = JsonRpcMessage.failure(4L, RpcException.NAME_TAKEN, "vergeben");

        assertNull(m.method());
        assertNull(m.params());
        assertNull(m.result());
        assertNotNull(m.rpcError());
        assertEquals(RpcException.NAME_TAKEN, m.rpcError().code());
        assertEquals("vergeben", m.rpcError().message());
        assertEquals(4L, m.id());
    }

    @Test
    void failureAllowsNullId() {
        // Antwort auf einen kaputten Request: id = null ist erlaubt.
        JsonRpcMessage m = JsonRpcMessage.failure(null, RpcException.PARSE_ERROR, "kaputt");

        assertNull(m.id());
        assertNotNull(m.rpcError());
        assertEquals(RpcException.PARSE_ERROR, m.rpcError().code());
    }

    @Test
    void failureIsResponse() {
        JsonRpcMessage m = JsonRpcMessage.failure(1L, RpcException.INVALID_REQUEST, "x");

        assertTrue(m.isResponse());
        assertFalse(m.isRequest());
        assertFalse(m.isNotification());
    }

    // ---------------------------------------------------------------- Record-Verhalten

    @Test
    void recordEqualityUsesAllFields() {
        // Records vergleichen alle Komponenten. Zwei Requests mit gleichen
        // Werten sind gleich; unterscheiden sie sich in einem Feld, nicht.
        assertEquals(
                JsonRpcMessage.request(1, "x", null),
                JsonRpcMessage.request(1, "x", null));

        assertNotEquals(
                JsonRpcMessage.request(1, "x", null),
                JsonRpcMessage.request(2, "x", null));
    }
}
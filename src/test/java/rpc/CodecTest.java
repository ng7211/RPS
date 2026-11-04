package rpc;

import com.fasterxml.jackson.databind.JsonNode;
import common.Move;
import common.Player;
import common.RoundResult;
import common.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodecTest {

    private final Codec codec = new Codec();

    // ---------- Nachrichten-Roundtrips ----------

    @Test
    void requestRoundtrip() {
        JsonNode params = codec.newParams().put("x", 1);
        JsonRpcMessage original = JsonRpcMessage.request(7, "register", params);

        JsonRpcMessage decoded = codec.decode(codec.encode(original));

        assertEquals(original, decoded);
        assertEquals(7L, decoded.id());
        assertTrue(decoded.isRequest());
    }

    @Test
    void notificationHasNoIdField() {
        String json = codec.encode(JsonRpcMessage.notification("onRoundFinished", null));

        assertFalse(json.contains("\"id\""), "Notification darf kein id-Feld haben: " + json);
        assertTrue(codec.decode(json).isNotification());
    }

    @Test
    void successWithNullResultUsesJsonNull() {
        String json = codec.encode(JsonRpcMessage.success(1L, null));

        assertTrue(json.contains("\"result\":null"), "Erfolg muss result:null senden: " + json);
        assertFalse(json.contains("\"error\""));
        JsonRpcMessage decoded = codec.decode(json);
        assertTrue(decoded.result().isNull());
    }

    @Test
    void failureWithNullIdWritesIdNull() {
        String json = codec.encode(JsonRpcMessage.failure(null, RpcException.PARSE_ERROR, "kaputt"));

        assertTrue(json.contains("\"id\":null"), "Fehlerantwort muss id:null schreiben: " + json);
        JsonRpcMessage decoded = codec.decode(json);
        assertNull(decoded.id());
        assertEquals(RpcException.PARSE_ERROR, decoded.rpcError().code());
        assertEquals("kaputt", decoded.rpcError().message());
    }

    // ---------- Fehlerfaelle beim Dekodieren ----------

    @Test
    void garbageIsParseError() {
        RpcException e = assertThrows(RpcException.class, () -> codec.decode("abc"));
        assertEquals(RpcException.PARSE_ERROR, e.getCode());
    }

    @Test
    void emptyObjectIsInvalidRequest() {
        RpcException e = assertThrows(RpcException.class, () -> codec.decode("{}"));
        assertEquals(RpcException.INVALID_REQUEST, e.getCode());
    }

    @Test
    void missingVersionIsInvalidRequest() {
        RpcException e = assertThrows(RpcException.class,
                () -> codec.decode("{\"method\":\"x\",\"id\":1}"));
        assertEquals(RpcException.INVALID_REQUEST, e.getCode());
    }

    @Test
    void methodAsNumberIsInvalidRequest() {
        RpcException e = assertThrows(RpcException.class,
                () -> codec.decode("{\"jsonrpc\":\"2.0\",\"method\":5,\"id\":1}"));
        assertEquals(RpcException.INVALID_REQUEST, e.getCode());
    }

    @Test
    void responseWithBothResultAndErrorIsInvalid() {
        RpcException e = assertThrows(RpcException.class,
                () -> codec.decode("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":1,\"error\":{\"code\":1,\"message\":\"x\"}}"));
        assertEquals(RpcException.INVALID_REQUEST, e.getCode());
    }

    // ---------- DTO <-> JsonNode ----------

    @Test
    void playerRoundtrip() {
        Player p = new Player(42, "Anna");

        JsonNode node = codec.toNode(p);
        Player back = codec.fromNode(node, Player.class);

        assertEquals(p, back);
    }

    @Test
    void recordWithEnumAndListRoundtrip() {
        RoundResult r = new RoundResult(3,
                new Player(1, "A"), Move.ROCK,
                new Player(2, "B"), Move.SCISSORS,
                Outcome.WINNERPLAYER1);

        RoundResult back = codec.fromNode(codec.toNode(r), RoundResult.class);

        assertEquals(r, back);
    }

    @Test
    void nullValueBecomesNullNode() {
        assertTrue(codec.toNode(null).isNull());
        assertNull(codec.fromNode(null, Move.class));
        assertNull(codec.fromNode(codec.toNode(null), Move.class));
    }

    @Test
    void unknownEnumValueIsInvalidParams() {
        JsonNode node = codec.toNode("ROK"); // gueltiger String, unbekannter Enum-Name
        RpcException e = assertThrows(RpcException.class, () -> codec.fromNode(node, Move.class));
        assertEquals(RpcException.INVALID_PARAMS, e.getCode());
    }

    @Test
    void wrongTypeIsInvalidParams() {
        JsonNode node = codec.toNode(123); // Text/Objekt erwartet, Zahl bekommen
        RpcException e = assertThrows(RpcException.class, () -> codec.fromNode(node, Player.class));
        assertEquals(RpcException.INVALID_PARAMS, e.getCode());
    }

    // ---------- getParam ----------

    @Test
    void getParamReturnsNullWhenMissing() {
        JsonNode params = codec.newParams().put("x", 1);
        assertNull(codec.getParam(params, "player", Player.class));
    }

    @Test
    void getParamRejectsNonObjectParams() {
        JsonNode params = codec.toNode(List.of(1, 2));
        RpcException e = assertThrows(RpcException.class,
                () -> codec.getParam(params, "player", Player.class));
        assertEquals(RpcException.INVALID_PARAMS, e.getCode());
    }

    // ---------- Grenzfall: Zeilenumbruch und Umlaute ----------

    @Test
    void lineBreaksAndUmlautsSurviveOneLine() {
        Player p = new Player(1, "Jürgen\nMüller");
        JsonRpcMessage original = JsonRpcMessage.request(1, "register",
                codec.newParams().set("player", codec.toNode(p)));

        String json = codec.encode(original);
        assertFalse(json.contains("\n"), "Nachricht muss eine Zeile sein");

        JsonRpcMessage decoded = codec.decode(json);
        Player back = codec.getParam(decoded.params(), "player", Player.class);
        assertEquals(p, back);
    }
}
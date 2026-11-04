package rpc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Marshalling/Unmarshalling. Einzige Klasse mit Jackson-Details.
 *
 * Wandelt JsonRpcMessage <-> Textzeile und DTO <-> JsonNode.
 * Jackson >= 2.12 vorausgesetzt (Records wie Player, RoundResult, GameState).
 * Enums (Move, Outcome, GamePhase, FinishReason) werden als String übertragen.
 */
public final class Codec {

    private final ObjectMapper mapper;

    public Codec() {
        this.mapper = new ObjectMapper()
                // Unbekannte Felder beim Wandeln ignorieren (Vorwärtskompatibilität).
                // Fehlende Pflichtfelder fallen weiterhin auf.
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ---------------------------------------------------------------- Nachricht <-> Text

    /**
     * Baut die JSON-Zeile von Hand, damit genau kontrollierbar ist, welche Felder
     * fehlen und welche JSON-null sind.
     */
    public String encode(JsonRpcMessage m) {
        ObjectNode root = mapper.createObjectNode();
        root.put("jsonrpc", "2.0");

        if (m.method() != null) {
            // Request oder Notification
            root.put("method", m.method());
            if (m.params() != null) {
                root.set("params", m.params());
            }
            // id nur schreiben, wenn vorhanden -> Notification hat kein id-Feld
            if (m.id() != null) {
                root.put("id", m.id());
            }
        } else {
            // Response: id IMMER schreiben (auch null, bei kaputtem Request)
            if (m.id() == null) {
                root.putNull("id");
            } else {
                root.put("id", m.id());
            }
            if (m.rpcError() != null) {
                ObjectNode err = mapper.createObjectNode();
                err.put("code", m.rpcError().code());
                err.put("message", m.rpcError().message());
                root.set("error", err);
            } else {
                // result NIEMALS weglassen. null -> JSON-null.
                root.set("result", m.result() == null ? NullNode.getInstance() : m.result());
            }
        }

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Kann Nachricht nicht serialisieren", e);
        }
    }

    /**
     * Parst eine Textzeile. Kaputtes JSON und falsche Struktur werden zu
     * RpcException mit PARSE_ERROR bzw. INVALID_REQUEST. Die Verbindung bleibt
     * am Leben, weil Connection die Exception fängt und antwortet.
     */
    public JsonRpcMessage decode(String line) {
        if (line == null || line.isBlank()) {
            throw new RpcException(RpcException.INVALID_REQUEST, "Leere Nachricht");
        }

        JsonNode root;
        try {
            root = mapper.readTree(line);
        } catch (Exception e) {
            throw new RpcException(RpcException.PARSE_ERROR, "Ungültiges JSON: " + e.getMessage());
        }

        if (root == null || !root.isObject()) {
            throw new RpcException(RpcException.INVALID_REQUEST, "Nachricht ist kein Objekt");
        }

        JsonNode version = root.get("jsonrpc");
        if (version == null || !version.isTextual() || !"2.0".equals(version.asText())) {
            throw new RpcException(RpcException.INVALID_REQUEST, "jsonrpc fehlt oder ist nicht 2.0");
        }

        // --- Request oder Notification ---
        if (root.has("method")) {
            JsonNode methodNode = root.get("method");
            if (!methodNode.isTextual()) {
                throw new RpcException(RpcException.INVALID_REQUEST, "method muss Text sein");
            }
            JsonNode params = root.get("params"); // darf fehlen -> null

            Long id = null;
            if (root.has("id")) {
                JsonNode idNode = root.get("id");
                if (!idNode.isNull()) { // id==null bei kaputtem Request erlaubt
                    if (!idNode.isIntegralNumber()) {
                        throw new RpcException(RpcException.INVALID_REQUEST, "id muss ganzzahlig sein");
                    }
                    id = idNode.asLong();
                }
            }
            return new JsonRpcMessage(methodNode.asText(), params, null, null, id);
        }

        // --- Response ---
        boolean hasResult = root.has("result");
        boolean hasError  = root.has("error");
        if (hasResult == hasError) {
            throw new RpcException(RpcException.INVALID_REQUEST,
                    "Response braucht genau eines von result oder error");
        }

        Long id = null;
        if (root.has("id") && !root.get("id").isNull()) {
            if (!root.get("id").isIntegralNumber()) {
                throw new RpcException(RpcException.INVALID_REQUEST, "id muss ganzzahlig sein");
            }
            id = root.get("id").asLong();
        }

        if (hasError) {
            JsonNode err = root.get("error");
            if (err == null || !err.isObject()
                    || !err.has("code") || !err.get("code").isIntegralNumber()
                    || !err.has("message") || !err.get("message").isTextual()) {
                throw new RpcException(RpcException.INVALID_REQUEST, "error ist unvollständig");
            }
            return new JsonRpcMessage(null, null, null,
                    new JsonRpcMessage.RpcError(err.get("code").asInt(), err.get("message").asText()),
                    id);
        }

        // result kann JSON-null sein -> NullNode, nicht Java-null
        return new JsonRpcMessage(null, null, root.get("result"), null, id);
    }

    // ---------------------------------------------------------------- DTO <-> JsonNode

    /** DTO/Enum -> JSON-Baum. null wird zu NullNode. */
    public JsonNode toNode(Object value) {
        if (value == null) return NullNode.getInstance();
        return mapper.valueToTree(value);
    }

    /**
     * JSON-Baum -> Objekt. null/NullNode ergibt null. Das ist die Stelle, an der
     * ein null-Zug als null ankommt und die Fachlogik dieselbe InvalidMoveException
     * wirft wie lokal (Transparenz-Beleg).
     */
    public <T> T fromNode(JsonNode node, Class<T> type) {
        if (type == Void.class) return null;
        if (node == null || node.isNull()) return null;
        try {
            return mapper.treeToValue(node, type);
        } catch (Exception e) {
            // Deckt JsonProcessingException (Typfehler, unbekannter Enum-Wert wie "ROK")
            // und IllegalArgumentException ab.
            throw new RpcException(RpcException.INVALID_PARAMS,
                    "Kann " + node + " nicht in " + type.getSimpleName() + " wandeln: " + e.getMessage());
        }
    }

    /** Liest einen benannten Parameter. Fehlt er -> null. */
    public <T> T getParam(JsonNode params, String name, Class<T> type) {
        if (params == null || params.isNull()) return null;
        if (!params.isObject()) {
            throw new RpcException(RpcException.INVALID_PARAMS, "params muss ein Objekt sein");
        }
        return fromNode(params.get(name), type);
    }

    /** Baut ein leeres params-Objekt. Praktisch fuer die Stubs. */
    public ObjectNode newParams() {
        return mapper.createObjectNode();
    }
}
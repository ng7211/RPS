package rpc;

import common.*;

/**
 * Trägt Fehlercode mit Text durch den Aufrufweg und Fehlerabbildung: Exception zu Code und Code zu Exception.
 */
public class RpcException extends RuntimeException {

    private final int code;
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int NAME_TAKEN = 1001;
    public static final int GAME_FULL = 1002;
    public static final int INVALID_MOVE = 1003;
    public static final int ALREADY_MOVED = 1004;
    public static final int UNKNOWN_PLAYER = 1005;

    public RpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * Gibt den Fehlercode für eine spezifische Exception zurück.
     * @param runtimeException
     * @return Abbildung von der Exception zu Code
     */
    public static int codeFor(RuntimeException runtimeException) {
        return switch (runtimeException) {
            case RpcException re -> re.getCode();
            case NameTakenException e -> NAME_TAKEN;
            case GameFullException e -> GAME_FULL;
            case InvalidMoveException e -> INVALID_MOVE;
            case AlreadyMovedException e -> ALREADY_MOVED;
            case UnknownPlayerException e -> UNKNOWN_PLAYER;
            case IllegalArgumentException e -> INVALID_PARAMS;
            case null, default -> INTERNAL_ERROR;
        };
    }

    /**
     * Gibt die Exception für einen spezifischen Fehlercode zurück.
     * @param code Fehlercode einer Exception
     * @param message Fehlernachricht
     * @return Abbildung von Code zu Exception
     */
    public static RuntimeException toException(int code, String message) {
        return switch (code) {
            case NAME_TAKEN -> new NameTakenException(message);
            case GAME_FULL -> new GameFullException(message);
            case INVALID_MOVE -> new InvalidMoveException(message);
            case ALREADY_MOVED -> new AlreadyMovedException(message);
            case UNKNOWN_PLAYER -> new UnknownPlayerException(message);
            case INVALID_PARAMS -> new IllegalArgumentException(message);
            default -> new RpcException(code, message);
        };
    }
}

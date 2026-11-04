// AI-GENERATED-START: JUnit 5 Tests für RpcException (Code & Exception Mapping) - 04.11.2026
package rpc;

import common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RpcExceptionTest {

    // Quelle für die 5 Spiel-Exceptions und ihre zugehörigen RPC-Codes
    static Stream<Arguments> provideSpielExceptions() {
        return Stream.of(
                Arguments.of(new NameTakenException("Name vergeben"), RpcException.NAME_TAKEN, NameTakenException.class),
                Arguments.of(new GameFullException("Spiel voll"), RpcException.GAME_FULL, GameFullException.class),
                Arguments.of(new InvalidMoveException("Ungültiger Zug"), RpcException.INVALID_MOVE, InvalidMoveException.class),
                Arguments.of(new AlreadyMovedException("Bereits gezogen"), RpcException.ALREADY_MOVED, AlreadyMovedException.class),
                Arguments.of(new UnknownPlayerException("Unbekannter Spieler"), RpcException.UNKNOWN_PLAYER, UnknownPlayerException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("provideSpielExceptions")
    void testSpielExceptionsHinUndZurueck(RuntimeException originalEx, int expectedCode, Class<? extends RuntimeException> expectedClass) {
        String testMessage = originalEx.getMessage();

        // 1. Hin: Exception zu Code
        int code = RpcException.codeFor(originalEx);
        assertEquals(expectedCode, code, "Code-Zuordnung für " + expectedClass.getSimpleName() + " fehlgeschlagen");

        // 2. Zurück: Code zu Exception
        RuntimeException convertedEx = RpcException.toException(code, testMessage);

        assertEquals(expectedClass, convertedEx.getClass(), "Klasse nach Rückumwandlung entspricht nicht dem Original");
        assertEquals(testMessage, convertedEx.getMessage(), "Meldung muss nach Rückumwandlung identisch sein");
    }

    @Test
    void testIllegalArgumentExceptionHinUndZurueck() {
        String msg = "Ungültige Parameter übergeben";
        IllegalArgumentException original = new IllegalArgumentException(msg);

        // Hin
        int code = RpcException.codeFor(original);
        assertEquals(RpcException.INVALID_PARAMS, code, "IllegalArgumentException muss INVALID_PARAMS (-32602) liefern");

        // Zurück
        RuntimeException converted = RpcException.toException(code, msg);
        assertEquals(IllegalArgumentException.class, converted.getClass());
        assertEquals(msg, converted.getMessage());
    }

    @Test
    void testIllegalStateExceptionErgibtInternalError() {
        IllegalStateException ex = new IllegalStateException("Falscher interner Zustand");

        int code = RpcException.codeFor(ex);

        assertEquals(RpcException.INTERNAL_ERROR, code, "IllegalStateException muss INTERNAL_ERROR (-32603) ergeben");
    }

    @Test
    void testUnbekannterCodeErgibtRpcExceptionMitGleichemCode() {
        int customCode = -9999;
        String msg = "Benutzerdefinierter Fehler";

        // Zurück
        RuntimeException converted = RpcException.toException(customCode, msg);

        assertInstanceOf(RpcException.class, converted, "Unbekannter Code muss eine RpcException erzeugen");
        RpcException rpcEx = (RpcException) converted;
        assertEquals(customCode, rpcEx.getCode(), "Fehlercode in RpcException muss erhalten bleiben");
        assertEquals(msg, rpcEx.getMessage(), "Fehlernachricht muss erhalten bleiben");

        // Hin (Prüfen ob codeFor auch RpcException wieder korrekten Code entnimmt)
        assertEquals(customCode, RpcException.codeFor(rpcEx));
    }
}
// AI-GENERATED-END
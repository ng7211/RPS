package common;

/**
 * Meldet, dass der Transport gescheitert ist. Z.B. Verbindung weg/fehlgeschlagen. Failure Tranzparenz
 * wird damit nicht erreicht.
 */
public class ConnectionException extends RuntimeException {
    public ConnectionException(String message) {super(message);}
    public ConnectionException(String message, Throwable cause) { super(message, cause);}

}

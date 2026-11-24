package common.dtos;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Spiellogik braucht Zeitstempel, den die Kommunikationsschicht kennt, deshalb
 * wird diese Klasse als Brücke implementiert.
 */
public final class CausalContext {
    //Jeder Thread hat seinen eigenen Wert
    private static final ThreadLocal<Map<String, Integer>> CURRENT = new ThreadLocal<>();

    //Keine Objekte sollen außerhalb dieser Klasse erzeugt werden können
    private CausalContext() {

    }

    /**
     * Speichert eine Kopie als TreeMap, damit die Logs sortiert sind. Bei null wird
     * null gespeichert.
     * @param senderClock
     */
    public static void set(Map<String, Integer> senderClock) {
        CURRENT.set(senderClock == null ? null : Collections.unmodifiableMap(new TreeMap<>(senderClock)));
    }

    public static Map<String, Integer> current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

}

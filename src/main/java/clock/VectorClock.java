package clock;


import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class VectorClock {

    public enum Order {
        EQUAL,
        BEFORE,
        AFTER,
        CONCURRENT
    }

    private final String owner;
    private final Map<String, Integer> v = new TreeMap<>();

    /**
     * Legt Uhr für einen Prozess an und setzt den Eintrag in die Map auf 0.
     * @param owner Name des Spielers
     */
    public VectorClock(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Owner ID is null or blank");
        this.owner = owner;
        v.put(owner, 0);
    }

    /**
     * Erhöht Eintrag um 1, vor jedem Senden oder lokalem Ereignis.
     */
    public synchronized void tick() {
        v.merge(owner, 1, Integer::sum);
    }

    /**
     * Nimmt komponentenweise das Maximum und ruft dann tick() auf.
     * Aufruf bei jedem Empfangen.
     * @param received Einträge der Spieler mit den Vektoruhren
     */
    public synchronized void merge(Map<String,Integer> received) {
        //Nachricht ohne VC zählt trotzdem als Empfangsereignis
        if (received == null) {
            tick();
            return;
        }
        received.forEach((k, x) -> v.merge(k, x, Math::max));
        tick();
    }

    /**
     * Gibt eine Kopie der Map zurück.
     * @return
     */
    public synchronized Map<String, Integer> snapshot() {
        return new TreeMap<>(v);
    }

    /**
     * Vergleicht zwei Vektoruhren, um die kausale Reihenfolge zwischen zwei Zeitstempeln zu bestimmen.
     * @param a
     * @param b
     * @return
     */
    public static Order compare(Map<String, Integer> a, Map<String, Integer> b) {
        boolean aGreater = false;
        boolean aSmaller = false;

        TreeSet<String> union = new TreeSet<>(a.keySet());
        union.addAll(b.keySet());

        for (String key : union) {
            int valueA = a.getOrDefault(key, 0);
            int valueB = b.getOrDefault(key, 0);

            if (valueA < valueB) {
                aSmaller = true;
            } else if (valueA > valueB) {
                aGreater = true;
            }
        }

        if (aSmaller && aGreater) return Order.CONCURRENT;
        if (aSmaller) return Order.BEFORE;
        if (aGreater) return Order.AFTER;
        return Order.EQUAL;
    }

    public synchronized String toString() {
        return owner + v;
    }
}

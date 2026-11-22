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

    private final String ownerID;
    private final Map<String, Integer> v = new TreeMap<>();

    /**
     * Jeder Spieler kriegt eine Vektoruhr
     * @param ownerID ID des Spielers
     */
    public VectorClock(String ownerID) {
        if (ownerID == null || ownerID.isBlank()) throw new IllegalArgumentException("Owner ID is null or blank");
        this.ownerID = ownerID;
        v.put(ownerID, 0);
    }

    public synchronized void tick() {
        v.merge(ownerID, 1, Integer::sum);
    }

    public synchronized void merge(Map<String,Integer> received) {
        //Nachricht ohne VC zählt trotzdem als Empfangsereignis
        if (received == null) {
            tick();
            return;
        }
        received.forEach((k, x) -> v.merge(k, x, Math::max));
        tick();
    }

    public synchronized Map<String, Integer> snapshot() {
        return new TreeMap<>(v);
    }

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
        return ownerID + v;
    }
}

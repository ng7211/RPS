// AI-GENERATED-START: JUnit 5 Tests für VectorClock - 22.11.2026
package clock;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class VectorClockTest {

    @Test
    void test01_tickErhoehtEigenenWert() {
        // 1. Eine neue Uhr steht bei {owner=0}. Nach tick() steht sie bei {owner=1}.
        VectorClock clock = new VectorClock("owner");
        Map<String, Integer> snapBefore = clock.snapshot();
        assertEquals(0, snapBefore.get("owner"), "Startet bei 0");

        clock.tick();
        Map<String, Integer> snapAfter = clock.snapshot();
        assertEquals(1, snapAfter.get("owner"), "Nach tick() bei 1");
    }

    @Test
    void test02_mergeUnbekannterProzess() {
        // 2. Merge mit einem unbekannten Prozess: Der Eintrag wird übernommen, und der eigene Wert steigt um 1.
        VectorClock clock = new VectorClock("alice");
        // Eigener Start: {alice=0}

        clock.merge(Map.of("bob", 5));

        Map<String, Integer> snap = clock.snapshot();
        assertEquals(5, snap.get("bob"), "Unbekannter Prozess-Eintrag wird übernommen");
        assertEquals(1, snap.get("alice"), "Eigener Wert steigt durch das implizite tick() um 1");
    }

    @Test
    void test03_mergeMitKleinerenWerten() {
        // 3. Merge mit kleineren Werten: Die eigenen Werte bleiben, nur der eigene Eintrag steigt um 1.
        VectorClock clock = new VectorClock("alice");
        clock.tick(); // alice = 1
        clock.merge(Map.of("bob", 2)); // alice = 2, bob = 2

        // Jetzt merge mit kleineren oder gleichen Werten
        clock.merge(Map.of("alice", 1, "bob", 1));

        Map<String, Integer> snap = clock.snapshot();
        assertEquals(2, snap.get("bob"), "Höherer eigener bekannter Wert bleibt erhalten");
        assertEquals(3, snap.get("alice"), "Eigener Wert steigt um 1 (von 2 auf 3)");
    }

    @Test
    void test04_compareFaelleUndBeispiele() {
        // 4. Die vier compare-Fälle, darunter unterschiedliche Schlüsselmengen

        // A) {a=1} vs {a=1,b=1} ergibt BEFORE (wie in Vorgabe)
        Map<String, Integer> map1 = Map.of("a", 1);
        Map<String, Integer> map2 = Map.of("a", 1, "b", 1);
        assertEquals(VectorClock.Order.BEFORE, VectorClock.compare(map1, map2));
        assertEquals(VectorClock.Order.AFTER, VectorClock.compare(map2, map1));

        // B) {a=1} vs {a=2} ergibt BEFORE
        Map<String, Integer> mapA = Map.of("a", 1);
        Map<String, Integer> mapB = Map.of("a", 2);
        assertEquals(VectorClock.Order.BEFORE, VectorClock.compare(mapA, mapB));
        assertEquals(VectorClock.Order.AFTER, VectorClock.compare(mapB, mapA));

        // C) {a=1,b=0} vs {a=0,b=1} ergibt CONCURRENT
        Map<String, Integer> mapConc1 = Map.of("a", 1, "b", 0);
        Map<String, Integer> mapConc2 = Map.of("a", 0, "b", 1);
        assertEquals(VectorClock.Order.CONCURRENT, VectorClock.compare(mapConc1, mapConc2));

        // D) EQUAL Fall
        Map<String, Integer> mapEq1 = Map.of("a", 2, "b", 3);
        Map<String, Integer> mapEq2 = Map.of("a", 2, "b", 3);
        assertEquals(VectorClock.Order.EQUAL, VectorClock.compare(mapEq1, mapEq2));
    }

    @Test
    void test03_b_mergeMitBeispielB() {
        // Nach merge({a=3}) steht b bei {a=3, b=1} (Angenommen der Owner heißt "b")
        VectorClock clock = new VectorClock("b"); // Start: {b=0}
        clock.merge(Map.of("a", 3)); // merget a=3 und tickt b hoch -> b=1

        Map<String, Integer> snap = clock.snapshot();
        assertEquals(3, snap.get("a"));
        assertEquals(1, snap.get("b"), "Owner 'b' wurde durch tick() inkrementiert");
    }

    @Test
    void test05_snapshotIsolation() {
        // 5. Snapshot-Isolation: Nach einem Snapshot tick() aufrufen. Der Snapshot darf sich nicht verändert haben.
        VectorClock clock = new VectorClock("alice");

        Map<String, Integer> snapshotBefore = clock.snapshot();
        assertEquals(0, snapshotBefore.get("alice"));

        // Verändere die Uhr nachträglich
        clock.tick();
        clock.tick();

        Map<String, Integer> snapshotAfter = clock.snapshot();

        assertEquals(0, snapshotBefore.get("alice"), "Alter Snapshot darf sich nicht verändern");
        assertEquals(2, snapshotAfter.get("alice"), "Aktuelle Uhr hat sich weiterentwickelt");
    }
}
// AI-GENERATED-END
// AI-GENERATED-START: Konsolidierte Integrationstests & Edge Cases für LocalMain - 03.11.2026
package app;

import client.ConsoleUI;
import client.GameClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LocalMainIntegrationTest {

    private List<String> ausgaben;

    @BeforeEach
    void setUp() {
        ausgaben = new ArrayList<>();
    }

    private void runScript(String commands, int pointsToWin) {
        Map<String, GameClient> clients = LocalMain.createClients(pointsToWin, ausgaben::add);
        ConsoleUI ui = new ConsoleUI(clients, new BufferedReader(new StringReader(commands)), ausgaben::add);
        ui.run();
    }

    // ---------------------------------------------------------
    // 1. Reguläre Haupt-Drehbücher (Abnahmepunkt 1)
    // ---------------------------------------------------------

    @Test
    void testDrehbuch1_VollesSpielMitFehlernUndEndstand() {
        String commands = """
            alice join
            bob join
            alice rock
            alice rock
            bob scissors
            alice paper
            bob rock
            exit
            """;

        runScript(commands, 2);

        assertTrue(ausgaben.stream().anyMatch(s -> s.contains("[Alice] Round 1:")), "Rückruf Runde 1 für Alice fehlt");
        assertTrue(ausgaben.stream().anyMatch(s -> s.contains("[Bob] Round 1:")), "Rückruf Runde 1 für Bob fehlt");
        assertTrue(ausgaben.stream().anyMatch(s -> s.contains("[Alice] Game has ended: Alice has reached 2 points.")), "Endstand fehlt");
        assertTrue(ausgaben.stream().anyMatch(s -> s.startsWith("Game error:")), "Spielfehler-Zeile wegen doppeltem Zug fehlt");
    }

    @Test
    void testDrehbuch2_AufgabeNurEinSpielerBekommtEndstand() {
        String commands = """
            alice join
            bob join
            alice rock
            bob quit
            exit
            """;

        runScript(commands, 2);

        assertTrue(ausgaben.stream().anyMatch(s -> s.contains("[Alice] The opponent has left the game.")), "Alice muss Aufgabe-Event bekommen");
        assertFalse(ausgaben.stream().anyMatch(s -> s.contains("[Bob] The opponent has left the game.")), "Bob darf kein Event bekommen");
    }

    // ---------------------------------------------------------
    // 2. Integration Edge Cases (Falsche Züge & Eingabefehler)
    // ---------------------------------------------------------

    @Test
    void testEdgeCase_FalscherZugBefehlInKonsole() {
        // Spieler tippt einen nicht existierenden Befehl/Zug
        String commands = """
            alice join
            alice banana
            exit
            """;

        runScript(commands, 2);

        assertTrue(ausgaben.stream().anyMatch(s -> s.contains("[Alice] Unknown command 'banana'. Type 'help'.")),
                "Ungültiger Befehl muss eine lesbare Warnung ausgeben");
    }

    @Test
    void testEdgeCase_ZugVorDemJoinen() {
        // Spieler versucht zu ziehen, bevor er dem Spiel beigetreten ist
        String commands = """
            alice rock
            exit
            """;

        runScript(commands, 2);

        assertTrue(ausgaben.stream().anyMatch(s -> s.startsWith("Game error:")),
                "Zug vor 'join' muss als Game error (UnknownPlayerException) ausgegeben werden");
    }

    @Test
    void testEdgeCase_UnbekannterSpielernameInKonsole() {
        // Ein nicht registrierter Client-Name wird eingegeben
        String commands = """
            carol join
            exit
            """;

        runScript(commands, 2);

        assertTrue(ausgaben.stream().anyMatch(s -> s.contains("Unknown player 'carol'. Known players: alice, bob")),
                "Unbekannter Name muss bekannte Spieler auflisten");
    }

    @Test
    void testEdgeCase_ZuVieleUndZuWenigeArgumente() {
        String commands = """
            alice
            alice rock paper
            exit
            """;

        runScript(commands, 2);

        assertTrue(ausgaben.stream().anyMatch(s -> s.equals("Usage: <name> <command>. Type 'help'.")), "Zu wenige Argumente abgefangen");
        assertTrue(ausgaben.stream().anyMatch(s -> s.equals("Too many arguments. Type 'help'.")), "Zu viele Argumente abgefangen");
    }

    // ---------------------------------------------------------
    // 3. Timing & Reihenfolge
    // ---------------------------------------------------------

    @Test
    void testReihenfolge_RueckrufeVorStatuszeile() {
        String commands = """
            alice join
            bob join
            alice rock
            bob scissors
            exit
            """;

        runScript(commands, 2);

        int aliceRoundCallbackIdx = -1;
        int bobRoundCallbackIdx = -1;
        int bobStatusZeileIdx = -1;

        for (int i = 0; i < ausgaben.size(); i++) {
            String line = ausgaben.get(i);
            if (line.contains("[Alice] Round 1:")) aliceRoundCallbackIdx = i;
            if (line.contains("[Bob] Round 1:")) bobRoundCallbackIdx = i;
            if (line.contains("[Bob] Move accepted. The round is complete.")) bobStatusZeileIdx = i;
        }

        assertTrue(bobStatusZeileIdx != -1 && aliceRoundCallbackIdx != -1 && bobRoundCallbackIdx != -1);
        assertTrue(aliceRoundCallbackIdx < bobStatusZeileIdx, "Alices Rückruf muss VOR Bobs Statuszeile erfolgen");
        assertTrue(bobRoundCallbackIdx < bobStatusZeileIdx, "Bobs Rückruf muss VOR seiner eigenen Statuszeile erfolgen");
    }
}
// AI-GENERATED-END
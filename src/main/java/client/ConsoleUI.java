// AI-GENERATED-START: ConsoleUI (Konsolen-Frontend, ein Terminal fuer mehrere Spieler), 03.11.2026
package client;

import common.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Konsolen-Frontend für die lokale Version. Ein einziges Terminal bedient mehrere Spieler:
 * Jede Eingabezeile beginnt mit dem Spielernamen, danach folgt der Befehl,
 * zum Beispiel {@code alice join} oder {@code bob rock}.
 *
 * <p>Die Klasse enthält <b>keine Spiellogik</b>. Sie zerlegt Eingaben, ruft die passende
 * Methode des {@link GameClient} auf und gibt das Ergebnis als Text aus. Alle Fehler
 * werden in {@link #run()} an einer einzigen Stelle behandelt.
 *
 * <p>Die Ausgabe geschieht über einen {@link Consumer}. Er wird auch von den
 * {@link GameListenerImpl}-Objekten benutzt und muss deshalb thread-sicher sein
 * (jede Meldung wird als <b>ein</b> String übergeben).
 */
public class ConsoleUI {

    /** Spielername in Kleinbuchstaben zum zugehörigen Client, alphabetisch sortiert. */
    private final Map<String, GameClient> clients;

    /** Quelle der Eingabezeilen (in Tests ein {@code StringReader}). */
    private final BufferedReader input;

    /** Ziel aller Ausgaben. */
    private final Consumer<String> output;

    /**
     * Erzeugt die Konsolenoberfläche.
     *
     * @param clients Spielername zum Client. Die Namen werden in Kleinbuchstaben übernommen
     *                und dürfen weder leer sein noch Leerzeichen enthalten
     * @param input   Quelle der Eingabezeilen
     * @param output  Ziel aller Ausgaben, muss thread-sicher sein
     * @throws IllegalArgumentException wenn ein Parameter {@code null} ist, ein Name leer ist
     *                                  oder Leerraum enthält oder zwei Namen sich nur in der
     *                                  Groß- und Kleinschreibung unterscheiden
     */
    public ConsoleUI(Map<String, GameClient> clients, BufferedReader input, Consumer<String> output) {
        if (clients == null || input == null || output == null) {
            throw new IllegalArgumentException("Clients, input or output is null");
        }
        this.clients = new TreeMap<>();
        for (Map.Entry<String, GameClient> entry : clients.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Player name or client is null");
            }
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || key.matches(".*\\s.*")) {
                throw new IllegalArgumentException("Player name must not be empty or contain spaces: '" + entry.getKey() + "'");
            }
            if (this.clients.put(key, entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate player name (ignoring case): " + key);
            }
        }
        this.input = input;
        this.output = output;
    }

    /**
     * Hauptschleife: liest Zeilen, bis {@code exit} eingegeben wird oder die Eingabe endet.
     *
     * <p>Leere Zeilen werden übersprungen, {@code exit} und {@code help} werden hier behandelt,
     * alles andere übernimmt {@link #handle(String)}. Fehler aus {@code handle} werden hier
     * gefangen und als Text ausgegeben, das Programm läuft danach weiter.
     * Die Methode wirft selbst nichts.
     */
    public void run() {
        say("Rock Paper Scissors (local mode). Type 'help' for commands.");
        printHelp();

        while (true) {
            String line;
            try {
                line = input.readLine();
            } catch (IOException e) {
                say("Input error: " + e.getMessage());
                break;
            }

            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("exit")) break;
            if (line.equalsIgnoreCase("help")) {
                printHelp();
                continue;
            }

            try {
                handle(line);
            } catch (NameTakenException | GameFullException | InvalidMoveException
                     | AlreadyMovedException | UnknownPlayerException e) {
                say("Game error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                say("Invalid input: " + e.getMessage());
            } catch (RuntimeException e) {
                say("Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        say("Bye.");
    }

    /**
     * Zerlegt eine Eingabezeile in Spielername und Befehl und führt ihn aus.
     * Die Methode fängt <b>keine</b> Fehler, das erledigt {@link #run()}.
     *
     * @param line bereinigte, nicht leere Zeile (weder {@code exit} noch {@code help})
     */
    private void handle(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            say("Usage: <name> <command>. Type 'help'.");
            return;
        }
        if (parts.length > 2) {
            say("Too many arguments. Type 'help'.");
            return;
        }

        GameClient client = clients.get(parts[0].toLowerCase(Locale.ROOT));
        if (client == null) {
            say("Unknown player '" + parts[0] + "'. Known players: " + knownPlayers());
            return;
        }

        String name = client.getPlayer().name();
        String command = parts[1].toLowerCase(Locale.ROOT);

        switch (command) {
            case "join" -> sayFor(name, "Joined. " + describeState(client.join()));
            case "state" -> sayFor(name, describeState(client.refresh()));
            case "quit" -> {
                client.quit();
                sayFor(name, "Left the game.");
            }
            default -> {
                Move move = parseMove(command);
                if (move == null) {
                    sayFor(name, "Unknown command '" + parts[1] + "'. Type 'help'.");
                } else {
                    sayFor(name, describeStatus(client.play(move)));
                }
            }
        }
    }

    /**
     * Übersetzt einen Text in einen Spielzug.
     *
     * @param text {@code rock}/{@code r}, {@code paper}/{@code p} oder {@code scissors}/{@code s},
     *             ohne Rücksicht auf Groß- und Kleinschreibung
     * @return der Zug, oder {@code null}, wenn der Text kein Zug ist (die Methode wirft nie)
     */
    private Move parseMove(String text) {
        if (text == null) return null;
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "rock", "r" -> Move.ROCK;
            case "paper", "p" -> Move.PAPER;
            case "scissors", "s" -> Move.SCISSORS;
            default -> null;
        };
    }

    /**
     * Beschreibt den Status nach einem Zug als Text.
     *
     * @param status Rückgabe von {@code play}
     * @return lesbarer Text
     */
    private String describeStatus(MoveStatus status) {
        return switch (status) {
            case WAITING_FOR_OPPONENT -> "Move accepted. Waiting for the opponent.";
            case ROUND_COMPLETE -> "Move accepted. The round is complete.";
        };
    }

    /**
     * Beschreibt den Spielzustand in einer Zeile. Funktioniert für 0, 1 und 2 Spieler
     * und für einen fehlenden Sieger und wirft nie eine Exception.
     *
     * @param state Spielzustand
     * @return lesbarer Text in einer Zeile
     */
    private String describeState(GameState state) {
        if (state == null) return "No state available.";

        StringBuilder sb = new StringBuilder();
        sb.append("Phase: ").append(state.gamePhase());
        sb.append(" | Round: ").append(state.currentRound());
        sb.append(" | First to ").append(state.pointsToWin()).append(" points");
        sb.append(" | Players: ");

        List<Player> players = state.players();
        if (players == null || players.isEmpty()) {
            sb.append("none yet");
        } else {
            for (int i = 0; i < players.size() && i < 2; i++) {
                if (i > 0) sb.append(", ");
                int points = (i == 0) ? state.player1Score() : state.player2Score();
                sb.append(players.get(i).name()).append(": ").append(points);
            }
        }

        if (state.winner() != null) {
            sb.append(" | Winner: ").append(state.winner().name());
        }
        return sb.toString();
    }

    /** Gibt die Befehlsübersicht als <b>eine</b> Meldung aus. */
    private void printHelp() {
        say(String.join("\n",
                "Commands (type the player name first):",
                "  <name> join           join the game",
                "  <name> rock | r       play rock",
                "  <name> paper | p      play paper",
                "  <name> scissors | s   play scissors",
                "  <name> state          show the current game state",
                "  <name> quit           leave the game",
                "  help                  show this help",
                "  exit                  close the program",
                "Players: " + knownPlayers(),
                "Example: alice join"));
    }

    /**
     * Liefert die bekannten Spielernamen für Hilfe und Fehlermeldungen.
     *
     * @return Namen in Kleinbuchstaben, alphabetisch, mit Komma getrennt, oder {@code none}
     */
    private String knownPlayers() {
        return clients.isEmpty() ? "none" : String.join(", ", clients.keySet());
    }

    /**
     * Gibt eine Meldung ohne Präfix aus (genau ein Aufruf von {@code output}).
     *
     * @param text Meldung, darf Zeilenumbrüche enthalten
     */
    private void say(String text) {
        output.accept(text);
    }

    /**
     * Gibt eine Meldung mit dem Spielernamen als Präfix aus, im selben Format wie
     * {@link GameListenerImpl}.
     *
     * @param playerName Anzeigename des Spielers
     * @param text       Meldung
     */
    private void sayFor(String playerName, String text) {
        say("[" + playerName + "] " + text);
    }
}
// AI-GENERATED-END
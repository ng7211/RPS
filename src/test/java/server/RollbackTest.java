// AI-GENERATED-START: JUnit 5 Tests für Rollback- und Memento-Logik (RollbackTest) - 04.11.2026
package server;

import common.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RollbackTest {

    private Game game;
    private ListenerRegistry registry;
    private Notifier notifier;
    private TestListenerFactory factory;
    private GameServiceImpl service;

    private Player alice;
    private Player bob;
    private CallbackEndpoint epAlice;
    private CallbackEndpoint epBob;
    private TestListener listenerAlice;
    private TestListener listenerBob;

    private static class TestListener implements GameListener {
        int roundFinishedCount = 0;
        RoundResult lastResult = null;

        @Override public void onOpponentJoined(Player player) {}
        @Override public void onRoundFinished(RoundResult roundResult) {
            roundFinishedCount++;
            lastResult = roundResult;
        }
        @Override public void onGameFinished(GameState gameState) {}
    }

    private static class TestListenerFactory implements ListenerFactory {
        Map<CallbackEndpoint, TestListener> map = new HashMap<>();
        @Override public GameListener create(CallbackEndpoint endpoint) {
            return map.get(endpoint);
        }
    }

    @BeforeEach
    void setUp() {
        registry = new ListenerRegistry();
        notifier = new Notifier(registry);
        game = new Game(3, new ClassicRules());
        factory = new TestListenerFactory();
        service = new GameServiceImpl(game, registry, notifier, factory);

        alice = new Player(1,"Alice");
        bob = new Player(2,"Bob");

        epAlice = new CallbackEndpoint("localhost", 8081, "/alice");
        epBob = new CallbackEndpoint("localhost", 8082, "/bob");

        listenerAlice = new TestListener();
        listenerBob = new TestListener();

        factory.map.put(epAlice, listenerAlice);
        factory.map.put(epBob, listenerBob);

        service.register(alice, epAlice);
        service.register(bob, epBob);
    }

    @AfterEach
    void tearDown() {
        CausalContext.clear();
    }

    @Test
    void testDemoSzenario_VerspaeteterZugAlice() {
        // 1. Bob spielt SCISSORS mit {bob=3} -> kommt zuerst an, Runde bleibt offen (wartet auf Alice)
        CausalContext.set(Map.of("bob", 3));
        service.submitMove(bob, Move.SCISSORS);
        CausalContext.clear();

        assertEquals(0, listenerAlice.roundFinishedCount, "Noch kein Rundenende");

        // 2. Alice spielt PAPER mit {alice=6} -> Komplettiert Runde 1: Alice(PAPER) vs Bob(SCISSORS) -> Bob gewinnt Runde 1!
        CausalContext.set(Map.of("alice", 6));
        service.submitMove(alice, Move.PAPER);
        CausalContext.clear();

        assertEquals(1, listenerAlice.roundFinishedCount);
        assertEquals(Outcome.WINNERPLAYER2, listenerAlice.lastResult.outcome(), "Bob gewinnt Runde 1 mit SCISSORS gegen PAPER");

        // 3. Jetzt kommt Alice verspäteter Zug: ROCK mit {alice=4} (kausal vor dem vorherigen Stand)
        // Das löst ein Rollback aus: Runde 1 wird zu Alice(ROCK) vs Bob(SCISSORS) -> Alice gewinnt!
        CausalContext.set(Map.of("alice", 4));
        service.submitMove(alice, Move.ROCK);
        CausalContext.clear();

        // Überprüfungen nach dem Rollback & Replay
        GameState state = game.snapshot();
        assertEquals(1, state.player1Score(), "Alice hat jetzt 1 Punkt");
        assertEquals(0, state.player2Score(), "Bob hat 0 Punkte");
        assertEquals(Outcome.WINNERPLAYER1, listenerAlice.lastResult.outcome(), "Korrigiertes Ergebnis: Alice gewinnt mit ROCK gegen SCISSORS");
    }

    @Test
    void test1_RichtigeReihenfolgeKeinZurueckspulen() {
        // Züge in kausal korrekter Reihenfolge ({alice=1} vor {bob=2})
        CausalContext.set(Map.of("alice", 1));
        service.submitMove(alice, Move.ROCK);
        CausalContext.clear();

        CausalContext.set(Map.of("bob", 2));
        service.submitMove(bob, Move.SCISSORS);
        CausalContext.clear();

        assertEquals(1, listenerAlice.roundFinishedCount, "Genau eine Rundenmeldung, kein Rollback");
    }

    @Test
    void test2_NebenlaeufigUndUeberRungengrenzeHinweg() {
        // Bob spielt SCISSORS mit {bob=1}
        CausalContext.set(Map.of("bob", 1));
        service.submitMove(bob, Move.SCISSORS);
        CausalContext.clear();

        // Alice spielt PAPER mit {alice=2} -> schließt Runde 1 ab
        CausalContext.set(Map.of("alice", 2));
        service.submitMove(alice, Move.PAPER);
        CausalContext.clear();

        assertEquals(2, game.snapshot().currentRound(), "Runde 2 ist erreicht");

        // Alice schickt jetzt verspätet ROCK mit {alice=1} (kausal kleiner als der Stand)
        CausalContext.set(Map.of("alice", 1));
        service.submitMove(alice, Move.ROCK);
        CausalContext.clear();

        // Das ROCK zählt für Runde 1, PAPER rutscht in Runde 2
        assertEquals(2, game.snapshot().currentRound());
    }

    @Test
    void test3_OhneZeitstempelBereitsGezogenException() {
        // Ohne CausalContext verhält sich alles normal (lokaler Modus)
        service.submitMove(alice, Move.ROCK);

        // Derselbe Spieler zieht noch einmal ohne Zeitstempel -> AlreadyMovedException
        assertThrows(AlreadyMovedException.class, () -> service.submitMove(alice, Move.PAPER));
    }

    @Test
    void test4_MementoSaveAndRestore() {
        // Zustand speichern
        Game.Memento memento = game.save();

        // Eine Runde spielen
        CausalContext.set(Map.of("alice", 1));
        service.submitMove(alice, Move.ROCK);
        CausalContext.clear();
        CausalContext.set(Map.of("bob", 1));
        service.submitMove(bob, Move.SCISSORS);
        CausalContext.clear();

        assertEquals(2, game.snapshot().currentRound(), "Runde hat gewechselt");

        // Wiederherstellen
        game.restore(memento);

        assertEquals(1, game.snapshot().currentRound(), "Nach restore ist der alte Rundenstand wieder da");
        assertEquals(0, game.snapshot().player1Score(), "Punkte zurückgesetzt");
    }
}
// AI-GENERATED-END
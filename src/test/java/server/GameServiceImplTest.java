// AI-GENERATED-START: JUnit 5 Tests für GameServiceImpl (mit Record CallbackEndpoint) - 02.11.2026
package server;

import common.*;
import common.NameTakenException;
import common.UnknownPlayerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GameServiceImplTest {

    // --- 1. Test-Hilfsklassen ---

    private static class TestListener implements GameListener {
        boolean throwException = false;
        int opponentJoinedCount = 0;
        int roundFinishedCount = 0;
        int gameFinishedCount = 0;
        RoundResult lastResult = null;
        GameState lastState = null;

        @Override
        public void onOpponentJoined(Player player) {
            if (throwException) throw new RuntimeException("Test Exception in OpponentJoined");
            opponentJoinedCount++;
        }

        @Override
        public void onRoundFinished(RoundResult roundResult) {
            if (throwException) throw new RuntimeException("Test Exception in RoundFinished");
            roundFinishedCount++;
            lastResult = roundResult;
        }

        @Override
        public void onGameFinished(GameState gameState) {
            if (throwException) throw new RuntimeException("Test Exception in GameFinished");
            gameFinishedCount++;
            lastState = gameState;
        }
    }

    private static class TestListenerFactory implements ListenerFactory {
        Map<CallbackEndpoint, TestListener> map = new HashMap<>();

        @Override
        public GameListener create(CallbackEndpoint endpoint) {
            if (!map.containsKey(endpoint)) {
                throw new IllegalArgumentException("Ungültiger/Unbekannter Endpunkt");
            }
            return map.get(endpoint);
        }
    }

    // --- 2. Testaufbau ---

    private ListenerRegistry registry;
    private Notifier notifier;
    private Game game;
    private TestListenerFactory factory;
    private GameServiceImpl service;

    private Player p1;
    private Player p2;
    private CallbackEndpoint ep1;
    private CallbackEndpoint ep2;
    private TestListener listenerP1;
    private TestListener listenerP2;

    @BeforeEach
    void setUp() {
        registry = new ListenerRegistry();
        notifier = new Notifier(registry);
        // pointsToWin = 2, ClassicRules
        game = new Game(2, new ClassicRules());
        factory = new TestListenerFactory();
        service = new GameServiceImpl(game, registry, notifier, factory);

        p1 = new Player(1,"Alice");
        p2 = new Player(2,"Bob");

        // Dummy-Endpunkte mit den neuen Record-Parametern (host, port, path)
        ep1 = new CallbackEndpoint("localhost", 8081, "/client1");
        ep2 = new CallbackEndpoint("localhost", 8082, "/client2");

        listenerP1 = new TestListener();
        listenerP2 = new TestListener();

        factory.map.put(ep1, listenerP1);
        factory.map.put(ep2, listenerP2);
    }

    // --- 3. Tests pro Methode ---

    // ---------------------------------------------------------
    // 1. Konstruktor
    // ---------------------------------------------------------
    @Test
    void testKonstruktorNullParameterWerfenException() {
        assertThrows(IllegalArgumentException.class, () -> new GameServiceImpl(null, registry, notifier, factory));
        assertThrows(IllegalArgumentException.class, () -> new GameServiceImpl(game, null, notifier, factory));
        assertThrows(IllegalArgumentException.class, () -> new GameServiceImpl(game, registry, null, factory));
        assertThrows(IllegalArgumentException.class, () -> new GameServiceImpl(game, registry, notifier, null));
    }

    // ---------------------------------------------------------
    // 2. getState
    // ---------------------------------------------------------
    @Test
    void testGetStateBekannterSpielerBekommtZustand() {
        service.register(p1, ep1);
        GameState state = service.getState(p1);
        assertNotNull(state);
        assertTrue(state.players().contains(p1));
    }

    @Test
    void testGetStateUnbekannterSpielerWirftException() {
        assertThrows(UnknownPlayerException.class, () -> service.getState(p1), "Spieler nicht im Spiel");
    }

    // ---------------------------------------------------------
    // 3. register
    // ---------------------------------------------------------
    @Test
    void testRegisterErsterSpielerWaitingKeineRueckrufe() {
        GameState state = service.register(p1, ep1);
        assertEquals(GamePhase.WAITING, state.gamePhase());
        assertEquals(0, listenerP1.opponentJoinedCount);
    }

    @Test
    void testRegisterZweiterSpielerErsterBekommtRueckruf() {
        service.register(p1, ep1);
        GameState state = service.register(p2, ep2);

        assertEquals(GamePhase.RUNNING, state.gamePhase());
        assertEquals(1, listenerP1.opponentJoinedCount, "P1 muss über P2 benachrichtigt werden");
        assertEquals(0, listenerP2.opponentJoinedCount, "P2 wird nicht selbst benachrichtigt");
    }

    @Test
    void testRegisterNameDoppeltWirftExceptionRegistryUnveraendert() {
        service.register(p1, ep1);
        GameListener listenerVorher = registry.find(p1).orElseThrow();

        Player p1Clone = new Player(2, "Alice"); // gleicher Name, andere ID

        assertThrows(NameTakenException.class, () -> service.register(p1Clone, ep2));

        assertTrue(registry.find(p1Clone).isEmpty(), "Zweiter Spieler darf nicht eingetragen sein");
        assertSame(listenerVorher, registry.find(p1).orElseThrow(), "Listener des ersten Spielers muss unverändert sein");
        assertEquals(1, service.getState(p1).players().size(), "Es darf nur ein Spieler im Spiel sein");
    }

    @Test
    void testRegisterFactoryWirftExceptionSpielBleibtWaiting() {
        // Unbekannter Endpunkt für den Fehlerfall
        CallbackEndpoint unknownEp = new CallbackEndpoint("localhost", 9999, "/unknown");

        assertThrows(IllegalArgumentException.class, () -> service.register(p1, unknownEp));
        assertEquals(GamePhase.WAITING, game.snapshot().gamePhase(), "Spiel muss WAITING bleiben");
        assertThrows(UnknownPlayerException.class, () -> service.getState(p1), "Spieler darf nicht eingetragen sein");
    }

    // ---------------------------------------------------------
    // 4. submitMove
    // ---------------------------------------------------------
    @Test
    void testSubmitMoveErsterZugWaitingForOpponent() {
        service.register(p1, ep1);
        service.register(p2, ep2);

        MoveStatus status = service.submitMove(p1, Move.ROCK);
        assertEquals(MoveStatus.WAITING_FOR_OPPONENT, status);
        assertEquals(0, listenerP1.roundFinishedCount, "Noch keine Rückrufe");
    }

    @Test
    void testSubmitMoveZweiterZugRoundCompleteBeideBenachrichtigt() {
        service.register(p1, ep1);
        service.register(p2, ep2);

        service.submitMove(p1, Move.ROCK);
        MoveStatus status = service.submitMove(p2, Move.SCISSORS); // P1 gewinnt Runde 1

        assertEquals(MoveStatus.ROUND_COMPLETE, status);
        assertEquals(1, listenerP1.roundFinishedCount);
        assertEquals(1, listenerP2.roundFinishedCount);
        assertEquals(listenerP1.lastResult, listenerP2.lastResult, "Beide bekommen exakt dasselbe Resultat");
        assertEquals(0, listenerP1.gameFinishedCount, "Spiel ist noch nicht beendet (erst 1 Punkt)");
    }

    @Test
    void testSubmitMoveSpielendeNachPunkten() {
        service.register(p1, ep1);
        service.register(p2, ep2);

        // Runde 1 (P1 gewinnt)
        service.submitMove(p1, Move.ROCK);
        service.submitMove(p2, Move.SCISSORS);

        // Runde 2 (P1 gewinnt und erreicht 2 Punkte)
        service.submitMove(p1, Move.ROCK);
        service.submitMove(p2, Move.SCISSORS);

        assertEquals(2, listenerP1.roundFinishedCount);
        assertEquals(1, listenerP1.gameFinishedCount, "P1 muss onGameFinished bekommen");
        assertEquals(1, listenerP2.gameFinishedCount, "P2 muss onGameFinished bekommen");
        assertEquals(FinishReason.SCORE, listenerP1.lastState.reasonForEnd(), "Grund muss SCORE sein");
    }

    @Test
    void testSubmitMoveEinListenerWirftExceptionAndererBekommtTrotzdemRueckruf() {
        service.register(p1, ep1);
        service.register(p2, ep2);

        listenerP1.throwException = true; // P1 ist kaputt

        service.submitMove(p1, Move.ROCK);
        assertDoesNotThrow(() -> service.submitMove(p2, Move.SCISSORS)); // Absturz in P1 darf Service nicht killen

        assertEquals(1, listenerP2.roundFinishedCount, "P2 muss seinen Rückruf trotz Fehler bei P1 bekommen");
    }

    // ---------------------------------------------------------
    // 5. leave
    // ---------------------------------------------------------
    @Test
    void testLeaveInRunningGegnerBekommtForfeit() {
        service.register(p1, ep1);
        service.register(p2, ep2);

        service.leave(p2); // P2 verlässt das laufende Spiel

        assertEquals(1, listenerP1.gameFinishedCount, "P1 muss über das Ende benachrichtigt werden");
        assertEquals(FinishReason.FORFEIT, listenerP1.lastState.reasonForEnd(), "Grund muss FORFEIT sein");
    }

    @Test
    void testLeaveInWaitingKeineRueckrufe() {
        service.register(p1, ep1);

        service.leave(p1);

        assertEquals(0, listenerP1.gameFinishedCount, "In WAITING gibt es noch keinen Gegner zu benachrichtigen");
    }

    @Test
    void testLeaveNachFinishedKeineWeiterenRueckrufe() {
        service.register(p1, ep1);
        service.register(p2, ep2);

        // P1 gewinnt schnell 2 Runden
        service.submitMove(p1, Move.ROCK); service.submitMove(p2, Move.SCISSORS);
        service.submitMove(p1, Move.ROCK); service.submitMove(p2, Move.SCISSORS);

        // Zähler zurücksetzen / merken
        int finishedCountBeforeLeave = listenerP1.gameFinishedCount; // Sollte 1 sein
        assertEquals(1, finishedCountBeforeLeave);

        service.leave(p2); // P2 verlässt nach Spielende

        assertEquals(finishedCountBeforeLeave, listenerP1.gameFinishedCount, "Kein zweiter gameFinished Rückruf");
    }
}
// AI-GENERATED-END
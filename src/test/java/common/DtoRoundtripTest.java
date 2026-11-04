package common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoRoundtripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Player ALICE = new Player(1, "alice");
    private static final Player BOB = new Player(2, "bob");

    private static <T> T roundtrip(T original, Class<T> type) throws Exception {
        String json = MAPPER.writeValueAsString(original); //Marshalling
        System.out.println(type.getSimpleName() + ": " + json);
        return MAPPER.readValue(json, type); //Unmarshalling
    }

    @Test
    void player_roundtrip() throws Exception {
        assertEquals(ALICE, roundtrip(ALICE, Player.class));
    }

    @Test
    void callbackEndpoint_roundtrip() throws Exception {
        CallbackEndpoint original = new CallbackEndpoint("192.168.0.12", 8081, "/callback");
        assertEquals(original, roundtrip(original, CallbackEndpoint.class));
    }

    @Test
    void move_allValues_roundtrip() throws Exception {
        for (Move original : Move.values()) {
            assertEquals(original, roundtrip(original, Move.class));
        }
    }

    @Test
    void moveStatus_allValues_roundtrip() throws Exception {
        for (MoveStatus original : MoveStatus.values()) {
            assertEquals(original, roundtrip(original, MoveStatus.class));
        }
    }

    @Test
    void roundResult_player1Wins() throws Exception {
        RoundResult original = new RoundResult(1, ALICE, Move.ROCK, BOB, Move.SCISSORS, Outcome.WINNERPLAYER1);
        assertEquals(original, roundtrip(original, RoundResult.class));
    }

    @Test
    void roundResult_player2Wins() throws Exception {
        RoundResult original = new RoundResult(2, ALICE, Move.ROCK, BOB, Move.PAPER, Outcome.WINNERPLAYER2);
        assertEquals(original, roundtrip(original, RoundResult.class));
    }

    @Test
    void roundResult_draw() throws Exception {
        RoundResult original = new RoundResult(3, ALICE, Move.ROCK, BOB, Move.ROCK, Outcome.DRAW);
        assertEquals(original, roundtrip(original, RoundResult.class));
    }

    @Test
    void gameState_waiting_emptyHistoryAndNullWinner() throws Exception {
        GameState original = new GameState(
                List.of(ALICE), 0, 0, 1, List.of(), 3,
                GamePhase.WAITING, null, FinishReason.NONE);

        GameState copy = roundtrip(original, GameState.class);

        assertEquals(original, copy);
        assertNull(copy.winner());
        assertTrue(copy.roundHistory().isEmpty());
    }

    @Test
    void gameState_running_withHistoryIncludingDraw() throws Exception {
        List<RoundResult> history = List.of(
                new RoundResult(1, ALICE, Move.ROCK, BOB, Move.SCISSORS, Outcome.WINNERPLAYER1),
                new RoundResult(2, ALICE, Move.PAPER, BOB, Move.PAPER, Outcome.DRAW));
        GameState original = new GameState(
                List.of(ALICE, BOB), 1, 0, 3, history, 3,
                GamePhase.RUNNING, null, FinishReason.NONE);

        GameState copy = roundtrip(original, GameState.class);

        assertEquals(original, copy);
        assertEquals(2, copy.roundHistory().size());
    }

    @Test
    void gameState_finishedByScore() throws Exception {
        List<RoundResult> history = List.of(
                new RoundResult(1, ALICE, Move.ROCK, BOB, Move.SCISSORS, Outcome.WINNERPLAYER1));
        GameState original = new GameState(
                List.of(ALICE, BOB), 3, 1, 5, history, 3,
                GamePhase.FINISHED, ALICE, FinishReason.SCORE);

        assertEquals(original, roundtrip(original, GameState.class));
    }

    @Test
    void gameState_finishedByForfeit() throws Exception {
        GameState original = new GameState(
                List.of(ALICE, BOB), 1, 0, 2, List.of(), 3,
                GamePhase.FINISHED, BOB, FinishReason.FORFEIT);

        assertEquals(original, roundtrip(original, GameState.class));
    }

    @Test
    void player_jsonHatErwarteteFelder() throws Exception {
        com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(ALICE));
        assertEquals(2, node.size(), "Player soll genau zwei Felder haben");
        assertEquals(1, node.get("ID").asInt());
        assertEquals("alice", node.get("name").asText());
    }

    @Test
    void gameState_winnerNullWirdAlsNullUebertragen() throws Exception {
        GameState original = new GameState(List.of(ALICE), 0, 0, 1, List.of(), 3,
                GamePhase.WAITING, null, FinishReason.NONE);
        com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(original));
        assertTrue(node.has("winner"), "Feld winner muss vorhanden sein");
        assertTrue(node.get("winner").isNull());
    }

    @Test
    void player_mitUmlaut_roundtrip() throws Exception {
        Player original = new Player(3, "Jürgen Zoë");
        assertEquals(original, roundtrip(original, Player.class));
    }

    @Test
    void player_unbekanntesFeld_wirdAbgelehnt() {
        String json = "{\"id\":1,\"name\":\"alice\",\"extra\":42}";
        assertThrows(Exception.class, () -> MAPPER.readValue(json, Player.class));
    }

}


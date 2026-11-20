package de.protokollnull.agent

import de.protokollnull.agent.engine.Tactical
import de.protokollnull.agent.engine.TacticalQuick
import de.protokollnull.agent.model.TacticalState
import de.protokollnull.agent.model.TacticalUnit
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Alle Einsatzarten müssen ohne Kartenbewegung und ohne Rundentaste abschließbar sein. */
class TacticalQuickTest {
    private fun initial(objective: String, count: Int = 1, hold: Int = 2): TacticalState =
        TacticalState(
            width = 7, height = 5,
            tiles = listOf(
                "#######",
                "#..I.X#",
                "#.P.RZ#",
                "#I....#",
                "#######"
            ),
            units = listOf(
                TacticalUnit("player", "Agent", true, 2, 2, 14, 14, isPlayer = true, accuracy = 55),
                TacticalUnit("e1", "Wache", false, 5, 3, 7, 7),
                TacticalUnit("vip", "Schutzperson", true, 3, 3, 6, 6, vip = true)
            ),
            selectedUnit = "player", objective = objective,
            requiredSecured = count, holdTurns = hold, maxTurns = 10
        )

    private fun reachGoal(t: TacticalState): TacticalState {
        val a = TacticalQuick.act(t, "advance")
        assertEquals(1, a.quickProgress)
        assertFalse(a.finished)
        val b = TacticalQuick.act(a, "advance")
        assertEquals(2, b.quickProgress)
        assertFalse(b.finished)
        return b
    }

    @Test fun movementNeverGetsStuckAfterFirstStepOrNewRound() {
        val begin = initial("reach")
        val first = TacticalQuick.act(begin, "advance")
        assertEquals(2, first.turn)
        assertTrue(TacticalQuick.choices(first).any { it.id == "advance" })
        val second = TacticalQuick.act(first, "advance")
        assertEquals(3, second.turn)
        val done = TacticalQuick.act(second, "secure")
        assertTrue(done.log.toString(), done.finished && done.won)
    }

    @Test fun reachAndRendezvousFinishWithoutMovingPixels() {
        for (objective in listOf("reach", "rendezvous")) {
            val done = TacticalQuick.act(reachGoal(initial(objective)), "secure")
            assertTrue(objective, done.finished && done.won)
        }
    }

    @Test fun escortAndExtractFinishWithLivingVip() {
        for (objective in listOf("extract", "escort")) {
            val done = TacticalQuick.act(reachGoal(initial(objective)), "secure")
            assertTrue(objective, done.finished && done.won)
            assertTrue(done.units.first { it.vip }.evacuated)
        }
    }

    @Test fun intelAndReconProgressAcrossSeveralRounds() {
        for (objective in listOf("intel", "recon")) {
            val board = initial(objective, count = 2)
            val first = TacticalQuick.act(reachGoal(board), "secure")
            assertFalse(first.finished)
            assertEquals(1, first.secured.size)
            val second = TacticalQuick.act(first, "secure")
            assertTrue(objective, second.finished && second.won)
            assertEquals(2, second.secured.size)
        }
    }

    @Test fun protectionCompletesWithoutHavingToFindTinyTarget() {
        var t = initial("protect", hold = 3)
        repeat(2) {
            t = TacticalQuick.act(t, "secure")
            assertFalse(t.finished)
        }
        t = TacticalQuick.act(t, "secure")
        assertTrue(t.finished && t.won)
    }

    @Test fun opponentsCanBeDefeatedWithoutRangeOrLineOfSightClicks() {
        var t = initial("eliminate")
        repeat(3) {
            if (!t.finished) t = TacticalQuick.act(t, "combat")
        }
        assertTrue(t.log.toString(), t.finished && t.won)
    }

    @Test fun invalidActionsDoNotConsumeTurns() {
        val t = initial("reach")
        assertEquals(t, TacticalQuick.act(t, "secure"))
        assertEquals(t, TacticalQuick.act(t, "unknown"))
        assertTrue(TacticalQuick.choices(t).any { it.id == "advance" })
    }

    @Test fun simpleProgressPersistsThroughSaveAndReload() {
        val one = TacticalQuick.act(initial("reach"), "advance")
        val json = Json.encodeToString(TacticalState.serializer(), one)
        val restored = Json.decodeFromString(TacticalState.serializer(), json)
        assertEquals(one, restored)
        assertEquals(1, restored.quickProgress)
        val finished = TacticalQuick.act(TacticalQuick.act(restored, "advance"), "secure")
        assertTrue(finished.won)
    }

    @Test fun legacyBoardCanWalkAgainAfterRoundEnds() {
        val t = initial("reach").copy(units = listOf(
            TacticalUnit("player", "Agent", true, 2, 2, 14, 14, ap = 1, isPlayer = true),
            TacticalUnit("e1", "Wache", false, 5, 3, 7, 7, accuracy = 0)
        ))
        val next = Tactical.move(t, "player", 3, 2)
        assertEquals(0, next.units.first { it.isPlayer }.ap)
        val newRound = Tactical.endTurn(next)
        assertEquals(2, newRound.units.first { it.isPlayer }.ap)
        assertTrue("Bewegung in nächster Runde möglich", Tactical.reachable(newRound, newRound.units.first { it.isPlayer }).isNotEmpty())
    }
}

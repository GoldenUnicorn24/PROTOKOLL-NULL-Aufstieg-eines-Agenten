package de.protokollnull.agent

import de.protokollnull.agent.engine.Tactical
import de.protokollnull.agent.engine.TacticalUX
import de.protokollnull.agent.model.TacticalState
import de.protokollnull.agent.model.TacticalUnit
import org.junit.Assert.*
import org.junit.Test

/** Regelprüfung für die neue klare Einmal-Tippen -> Vorschau -> Bestätigen-Bedienung. */
class TacticalUXTest {
    private fun board(): TacticalState = TacticalState(
        width = 7,
        height = 5,
        tiles = listOf("#######", "#.....#", "#.P.G.#", "#.....#", "#######"),
        units = listOf(
            TacticalUnit("player", "Spieler", true, 2, 2, 10, 10, ap = 2, isPlayer = true),
            TacticalUnit("enemy", "Gegner", false, 4, 2, 6, 6)
        ),
        selectedUnit = "player"
    )

    private fun player(t: TacticalState): TacticalUnit = t.units.first { it.isPlayer }

    @Test fun movementHasOneTapPreviewAndASeparateLegalAction() {
        val t = board()
        val p = TacticalUX.preview(t, player(t), 3, 2)
        assertEquals("move", p.action)
        assertTrue(p.allowed)
        assertTrue(p.label.contains("Bewegen"))
        // Die Vorschau darf niemals den Spielzustand verändern.
        assertEquals(2, player(t).x)
        val afterConfirm = Tactical.move(t, player(t).id, 3, 2)
        assertEquals(3, player(afterConfirm).x)
        assertEquals(1, player(afterConfirm).ap)
    }

    @Test fun attackerSeesHitChanceButPreviewDoesNotFire() {
        val t = board()
        val p = TacticalUX.preview(t, player(t), 4, 2)
        assertEquals("attack", p.action)
        assertEquals("enemy", p.targetId)
        assertTrue(p.allowed)
        assertTrue(p.detail.contains("%"))
        assertEquals(6, t.units.first { !it.ally }.hp)
    }

    @Test fun blockedAndDistantTilesExplainWhy() {
        val t = board()
        val wall = TacticalUX.preview(t, player(t), 0, 0)
        assertFalse(wall.allowed)
        assertTrue(wall.detail.contains("nicht begehbar"))
        val outside = TacticalUX.preview(t, player(t), 20, 0)
        assertFalse(outside.allowed)
        assertTrue(outside.detail.contains("außerhalb"))
    }

    @Test fun outOfActionPointsNeverOffersAConfirmableMoveOrAttack() {
        val t = board().copy(units = board().units.map { if (it.isPlayer) it.copy(ap = 0) else it })
        assertFalse(TacticalUX.preview(t, player(t), 3, 2).allowed)
        assertFalse(TacticalUX.preview(t, player(t), 4, 2).allowed)
        assertTrue(TacticalUX.preview(t, player(t), 4, 2).detail.contains("Aktionspunkte"))
    }

    @Test fun specialActionsAreOnlyOfferedForValidTargets() {
        val t = board()
        val distant = TacticalUX.preview(t, player(t), 4, 2, "takedown")
        assertFalse(distant.allowed)
        assertTrue(distant.detail.contains("benachbarten"))
        val adjacent = TacticalUX.preview(t, player(t).copy(x = 3), 4, 2, "takedown")
        assertTrue(adjacent.allowed)
        val abilityPlayer = player(t).copy(ability = "distract")
        assertTrue(TacticalUX.preview(t, abilityPlayer, 4, 2, "ability").allowed)
        assertFalse(TacticalUX.preview(t, abilityPlayer.copy(abilityCooldown = 2), 4, 2, "ability").allowed)
    }

    @Test fun goalInstructionsExplainDifferentMissionTypes() {
        val t = board()
        assertTrue(TacticalUX.objective(t).contains("Gegner"))
        assertTrue(TacticalUX.objective(t.copy(objective = "escort")).contains("Schutzperson"))
        assertTrue(TacticalUX.objective(t.copy(objective = "intel", requiredSecured = 2)).contains("0/2"))
        assertTrue(TacticalUX.objective(t.copy(objective = "protect", holdTurns = 3)).contains("0/3"))
    }
}

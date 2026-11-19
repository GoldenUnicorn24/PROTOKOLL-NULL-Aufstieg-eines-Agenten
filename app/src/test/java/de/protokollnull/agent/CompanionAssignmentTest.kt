package de.protokollnull.agent

import de.protokollnull.agent.data.SaveMigration
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import org.junit.Assert.*
import org.junit.Test

class CompanionAssignmentTest {
    private val data = TestData.data
    private val draft = CompanionAssignment.dossier().copy(firstName = "Kim", profession = "tech")
    private val birthday = Clock.daysUntilAge(0, 18)
    private val game = Prologue.skip(data, GameState(pendingCompanion = draft)).state.copy(activeDialogue = null)

    @Test fun birthdayAssignmentSurvivesSaveAndOnlyHappensOnce() {
        val eve = World.timeSkip(data, game, birthday - 1).state
        assertEquals(draft, eve.pendingCompanion)
        assertTrue(eve.customNpcs.isEmpty())
        assertNull(World.npcLocation(data, eve, draft.id))
        assertFalse(Romance.romanceable(data, eve, draft.id))

        val restored = SaveMigration.decode(SaveMigration.encode(eve))
        val arrival = World.passTime(data, restored.copy(clock = GameClock(birthday - 1, 5)), 1).state
        assertNull(arrival.pendingCompanion)
        assertEquals(draft, CompanionAssignment.awaitingIntroduction(arrival))
        assertTrue(draft.id in arrival.team)
        assertEquals(0, Relations.get(data, arrival, draft.id).romanceStage)
        val greeted = CompanionAssignment.introduce(data, arrival, offerTour = true).state
        assertEquals(14, Relations.get(data, greeted, draft.id).friendship)
        assertFalse(CompanionAssignment.introduce(data, greeted, offerTour = true).ok)
        val later = World.timeSkip(data, SaveMigration.decode(SaveMigration.encode(greeted)), 730).state
        assertNull(CompanionAssignment.awaitingIntroduction(later))
        assertEquals(listOf(draft), later.customNpcs)
        assertTrue(Romance.allowed(data, later, draft.id))
        assertEquals(0, Relations.get(data, later, draft.id).romanceStage)
        var friends = later
        repeat(7) {
            if (friends.clock.slot == 5) friends = World.sleep(data, friends).state
            val visit = Social.interact(data, friends, draft.id, "time")
            assertTrue(visit.messages.toString(), visit.ok)
            friends = visit.state
        }
        assertEquals(0, Relations.get(data, friends, draft.id).romanceStage)
        assertTrue(Romance.advance(data, friends, draft.id).ok)
    }

    @Test fun skipAcrossBirthdayPreservesExistingTeamAndDatesTheArrival() {
        val start = game.copy(clock = GameClock(birthday - 10), team = listOf("michael"))
        val end = World.timeSkip(data, start, 400).state
        assertEquals(birthday, end.customNpcs.single().createdDay)
        assertEquals(start.team, end.team)
        val formal = CompanionAssignment.introduce(data, end, offerTour = false).state
        assertEquals(24, Relations.get(data, formal, draft.id).respect)
        assertEquals(10, Relations.get(data, formal, draft.id).friendship)
    }

    @Test fun skippingCompanionAndLoadingLegacySaveNeverCreatesOne() {
        val old = SaveMigration.decode("""{"schemaVersion":2,"phase":"game","clock":{"day":729}}""")
        val result = World.timeSkip(data, old, 2).state
        assertTrue(result.customNpcs.isEmpty())
        assertNull(CompanionAssignment.awaitingIntroduction(result))
    }
}

package de.protokollnull.agent

import de.protokollnull.agent.engine.Dialogues
import de.protokollnull.agent.engine.Endings
import de.protokollnull.agent.engine.Prologue
import de.protokollnull.agent.model.GameState
import org.junit.Assert.*
import org.junit.Test

class PrologueStoryTest {
    private val data = TestData.data

    private fun playThrough(picks: Map<String, String>): GameState {
        var s = GameState()
        val seen = mutableSetOf<String>()
        while (s.phase == "prologue") {
            val n = requireNotNull(Prologue.node(data, s))
            assertTrue("Schleife bei ${n.id}", seen.add(n.id))
            s = Prologue.choose(data, s, picks[n.id] ?: n.choices.first().id).state
        }
        assertTrue("Einstieg zu kurz", seen.size >= 12)
        return s
    }

    @Test fun choicesPersistIntoTheReunionAndEpilogue() {
        val versions = listOf(
            Triple("obey", "bus", "phoenix_obedient"),
            Triple("refuse", "chair", "phoenix_defiant"),
            Triple("warn", "socks", "kira_warned")
        )
        for ((response, callback, flag) in versions) {
            val s = playThrough(mapOf("prolog_04" to response, "rueckbrief" to "plums"))
            assertTrue(flag in s.flags)
            val rescued = s.copy(flags = s.flags + setOf("kira_rescued", "ending_reform"))
            val reunion = Dialogues.start(data, rescued, "dlg_kira_reunion").state
            val visible = Dialogues.visibleChoices(data, reunion).map { it.id }
            assertEquals(listOf(callback), visible.filter { it in listOf("bus", "chair", "socks") })
            assertTrue(Endings.epilogue(data, rescued).any { "Pflaumen" in it.text })
        }
    }

    @Test fun thePhotoAndTheSketchBothYieldALead() {
        for ((photo, answer, call) in listOf(Triple("accept", "print", "camera"), Triple("burn", "negative", "sketch"))) {
            val s = playThrough(mapOf("prolog_05" to photo))
            var file = Dialogues.start(data, s, "dlg_birkhoff_file").state
            assertTrue(Dialogues.visibleChoices(data, file).any { it.id == answer })
            file = Dialogues.choose(data, file, answer).state
            file = Dialogues.choose(data, file, "where").state
            file = Dialogues.choose(data, file, "ok").state
            assertTrue("archive_access" in file.flags)
            val contact = Dialogues.start(data, file, "dlg_voss_call").state
            val conversation = Dialogues.choose(data, contact, call)
            assertTrue(conversation.ok)
            assertTrue("commission_admitted" in Dialogues.choose(data, conversation.state, "press").state.flags)
        }
    }

    @Test fun skippedMemoriesDoNotUnlockSpecificAnswers() {
        val arrival = Prologue.skip(data, GameState()).state
        assertEquals(setOf("quiet", "half", "ask"), Dialogues.visibleChoices(data, arrival).map { it.id }.toSet())
        assertFalse(Dialogues.choose(data, arrival, "receipt").ok)
    }
}

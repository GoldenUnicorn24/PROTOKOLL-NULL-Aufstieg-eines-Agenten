package de.protokollnull.agent

import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * v1.0 End-to-End: spielt die Kampagne über die echten Engine-Aktionen durch – Prolog,
 * Dialoge, Story-Einsätze (Entscheidung, Untersuchung, Observation, Taktik), Nachbesprechung,
 * Quests und Beförderungen – bis zu jedem Ende und anschließend durch Kapitel 4.
 *
 * Nicht-erzählerische Hürden (Alter, Level, Ruf, Beurteilung, Energie) werden vom Testtreiber
 * erfüllt, damit der Story-Graph selbst geprüft wird: Kein Einsatz und kein Ende darf
 * unerreichbar sein.
 */
class E2EPlaythroughTest {
    private val d = TestData.data

    private data class Run(val state: GameState, val steps: Int, val missions: List<String>)

    private fun fresh(seed: Long): GameState {
        var s = GameState(seed = seed, player = PlayerCharacter(firstName = "E2E", codename = "Sieben"))
        s = Prologue.finish(d, s).state
        val strong = Attr.all.associateWith { 85 }
        return s.copy(player = s.player.copy(attributes = strong, skills = d.skills.associate { it.id to 3 }))
    }

    private fun apply(s: GameState, r: Result): GameState = if (r.ok) Quests.update(d, r.state).state else s

    /** Nicht-erzählerische Voraussetzungen erfüllen (Werte, Alter, Beurteilung, Erholung). */
    private fun groom(s0: GameState): GameState {
        var s = s0
        val p = s.player
        s = s.copy(player = p.copy(energy = 100, health = 100, stress = 10, wounds = 0, level = maxOf(p.level, 1)))
        val check = Progression.nextRankCheck(d, s) ?: return s
        val r = check.rank
        if (check.requirements.filter { !it.second }.all { (label, _) -> !label.startsWith("Mission") && r.requiredFlags.none { f -> label == (Progression.flagLabels[f] ?: f) } }) {
            // Nur Zahlenwerte fehlen: angleichen und befördern (die Prüfung selbst testet CareerTest/V05)
            val ageDays = Clock.daysUntilAge(s.clock.day, r.minAge)
            s = s.copy(clock = GameClock(s.clock.day + ageDays, 0))
            s = s.copy(player = s.player.copy(level = maxOf(s.player.level, r.minLevel), reputation = maxOf(s.player.reputation, r.minReputation)))
            r.supervisorId?.let { sup -> s = Relations.update(d, s, sup) { it.copy(trust = maxOf(it.trust, r.minSupervisorTrust)) } }
            s = s.copy(career = s.career.copy(evaluations = s.career.evaluations + Evaluation(s.clock.day, "A", 90, "Testtreiber")))
            if (Progression.nextRankCheck(d, s)?.ready == true) s = Quests.update(d, Progression.applyPromotion(d, s, r.rank)).state
        }
        return s
    }

    private fun play(seed: Long, finale: String, untilFlag: String = "ending_reached", start: GameState? = null, maxSteps: Int = 4000): Run {
        var s = start ?: fresh(seed)
        val done = mutableListOf<String>()
        var steps = 0
        while (untilFlag !in s.flags && steps++ < maxSteps) {
            val run = s.activeMission
            val dlg = s.activeDialogue
            when {
                dlg != null -> {
                    val choices = Dialogues.visibleChoices(d, s)
                    val pick = if (dlg.dialogueId == "dlg_final") choices.firstOrNull { it.id == finale } ?: choices.first() else choices.firstOrNull()
                    s = if (pick == null) s.copy(activeDialogue = null, completedDialogues = s.completedDialogues + dlg.dialogueId)
                        else apply(s, Dialogues.choose(d, s, pick.id))
                }
                run != null && run.result != null -> {
                    if (run.result == "success" && Ops.isContract(run.missionId).not()) done += run.missionId
                    s = apply(s, Missions.finish(d, s))
                }
                run != null -> {
                    val st = Missions.currentStage(d, s)!!
                    s = when (st.type) {
                        "search" -> apply(s, Missions.search(d, s, (0 until st.spots.size).first { it !in run.search!!.searched }))
                        "observe" -> apply(s, Missions.observe(d, s, run.observe!!.correct[run.observe!!.round]))
                        "tactical" -> apply(s, Missions.updateTactical(d, s) { t -> t.copy(finished = true, won = true) })
                        "narrative" -> apply(s, Missions.continueNarrative(d, s))
                        else -> {
                            val o = st.options.firstOrNull { Conds.met(it.requires, d, s) } ?: st.options.first()
                            apply(s, Missions.choose(d, s, o.id))
                        }
                    }
                }
                Endings.pending(s) -> s = s.copy(flags = s.flags + "epilogue_seen")
                CompanionAssignment.awaitingIntroduction(s) != null -> s = s.copy(pendingCompanion = null)
                else -> {
                    val story = Dialogues.pendingStory(d, s)
                    val mission = d.missions.firstOrNull { it.id !in s.completedMissions && Missions.lockReason(d, s, it) == null }
                    val talk = Life.ids(d, s).asSequence().filter { Relations.isKnownNpc(d, s, it) }
                        .flatMap { id -> Dialogues.available(d, s, id).filter { it.once }.asSequence() }.firstOrNull()
                    s = when {
                        story != null -> apply(s, Dialogues.start(d, s, story.id))
                        mission != null -> apply(groom(s), Missions.start(d, groom(s), mission.id, "balanced", emptyList()))
                        talk != null -> apply(s, Dialogues.start(d, s, talk.id))
                        else -> {
                            val before = s
                            var n = groom(s)
                            if (n == before || n.player.rank == before.player.rank) n = n.copy(clock = GameClock(n.clock.day + 60, 0))
                            n
                        }
                    }
                }
            }
        }
        return Run(s, steps, done)
    }

    @Test fun everyEndingIsReachableThroughRealPlay() {
        for ((i, ending) in listOf("reform", "order", "shadow", "exit").withIndex()) {
            val r = play(100L + i, ending)
            assertTrue("$ending: Finale erreicht nach ${r.steps} Schritten (Rang ${r.state.player.rank})", "ending_reached" in r.state.flags)
            assertEquals("ending_$ending", Endings.endingId(r.state))
            listOf("M001", "M010", "M015", "M017", "M019", "M021", "M022", "M016").forEach { assertTrue("$ending: $it gespielt", it in r.state.completedMissions) }
            assertEquals(12, r.state.player.rank)
            assertTrue(Endings.epilogue(d, r.state).size >= 3)
        }
    }

    @Test fun newEndingsAndChapterFourAreReachable() {
        // Bündnis- und Akademie-Strang entstehen im regulären Durchlauf und schalten ihre Enden frei
        val r = play(222L, "alliance")
        val s = r.state
        assertTrue("Finale erreicht", "ending_reached" in s.flags)
        assertTrue("Nikita-Bündnis im Durchlauf geschmiedet", "alliance_forged" in s.flags)
        assertTrue("Akademie im Durchlauf gegründet", "academy_founded" in s.flags)
        assertEquals("ending_alliance", Endings.endingId(s))
        // Kapitel 4 nach dem Epilog bis zum Abschluss spielen
        val ch4 = play(223L, "order", untilFlag = "ch4_done", start = s.copy(flags = s.flags + "epilogue_seen"))
        assertTrue("Kapitel 4 abgeschlossen nach ${ch4.steps} Schritten", "ch4_done" in ch4.state.flags)
        listOf("M030", "M031", "M032", "M033", "M034", "M035", "M036").forEach { assertTrue(it, it in ch4.state.completedMissions) }
        assertTrue("phoenix_law" in ch4.state.flags)
    }

    @Test fun allStoryMissionsArePlayableInOneCampaign() {
        val r = play(333L, "academy")
        assertEquals("ending_academy", Endings.endingId(r.state))
        val after = play(334L, "academy", untilFlag = "ch4_done", start = r.state.copy(flags = r.state.flags + "epilogue_seen"))
        val missing = d.missions.map { it.id }.filter { it !in after.state.completedMissions }
        // Bedingte Nebenstränge (z. B. Nikita-Bündnis ohne Kontakt) dürfen fehlen, die Hauptstory nicht
        val optional = setOf("M023", "M024", "M025", "M026", "M027", "M028", "M029")
        assertTrue("Hauptmissionen fehlen: ${missing - optional}", (missing - optional).isEmpty())
        assertTrue("Mindestens 30 Storymissionen in einem Durchlauf: ${after.state.completedMissions.size}", after.state.completedMissions.count { it in d.missionById } >= 30)
    }
}

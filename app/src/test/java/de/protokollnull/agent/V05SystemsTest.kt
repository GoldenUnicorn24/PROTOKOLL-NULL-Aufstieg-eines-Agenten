package de.protokollnull.agent

import de.protokollnull.agent.data.SaveMigration
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** Tests für Zeitplan-Simulation, Karriere 2.0 und Ausrüstungsprofile (v0.5). */
class V05SystemsTest {
    private val d = TestData.data

    private fun start(seed: Long = 42L): GameState {
        var s = GameState(seed = seed, player = PlayerCharacter(firstName = "Test", codename = "Null"))
        s = Prologue.finish(d, s).state
        return s.copy(activeDialogue = null, completedDialogues = s.completedDialogues + "dlg_arrival", flags = s.flags + "met_michael")
    }

    // --- Simulation -------------------------------------------------------------

    @Test fun simulationIsDeterministic() {
        listOf(1, 7, 30, 90, 365).forEach { days ->
            val a = Simulation.run(d, start(), days, stopOnEvents = false).state
            val b = Simulation.run(d, start(), days, stopOnEvents = false).state
            assertEquals("Tage $days", a, b)
            assertEquals(days, a.clock.day)
            assertEquals(days, a.simReports.last().endDay - a.simReports.last().startDay)
        }
    }

    @Test fun progressOnlyFromPlannedActivities() {
        val rest = start().copy(weekPlan = List(7) { "rest" })
        val r = Simulation.run(d, rest, 90, stopOnEvents = false).state
        assertEquals("Reine Erholung: keine Attributsteigerung", rest.player.attributes, r.player.attributes)
        assertEquals(0, r.simReports.last().xp)
        val combat = start().copy(weekPlan = List(7) { if (it < 5) "combat" else "rest" })
        val c = Simulation.run(d, combat, 90, stopOnEvents = false).state
        assertTrue((c.player.attributes[Attr.FITNESS] ?: 0) + (c.player.attributes[Attr.PRECISION] ?: 0) >
            (combat.player.attributes[Attr.FITNESS] ?: 0) + (combat.player.attributes[Attr.PRECISION] ?: 0))
        assertEquals("Intellekt ohne Techniktraining unverändert", combat.player.attributes[Attr.INTELLECT], c.player.attributes[Attr.INTELLECT])
    }

    @Test fun relationshipsOnlyWithMetAndPresentNpcs() {
        val s = start().copy(weekPlan = List(7) { "social" })
        val r = Simulation.run(d, s, 120, stopOnEvents = false).state
        // Nie getroffene / verborgene Figuren bleiben unverändert
        listOf("kira", "voss", "nikita", "helena").forEach { id -> assertNull("$id darf keine Beziehung bekommen", r.relations[id]) }
        // Percy ist nie in der Kantine
        assertEquals(Relations.get(d, s, "percy").friendship, Relations.get(d, r, "percy").friendship)
        assertTrue("mit Michael (gemeinsame Mahlzeiten)", Relations.get(d, r, "michael").friendship > Relations.get(d, s, "michael").friendship)
    }

    @Test fun woundsHealRealistically() {
        val s = start().let { it.copy(player = it.player.copy(wounds = 3, health = 60)) }
        val week = Simulation.run(d, s.copy(weekPlan = List(7) { "duty" }), 7, stopOnEvents = false).state
        assertTrue("Drei Wunden heilen nicht in einer Woche", week.player.wounds >= 2)
        val month = Simulation.run(d, s.copy(weekPlan = List(7) { "rest" }), 30, stopOnEvents = false).state
        assertEquals(0, month.player.wounds)
    }

    @Test fun stopsAtEighteenAndCompanionArrives() {
        val companion = CompanionAssignment.dossier()
        val s = start().copy(pendingCompanion = companion, clock = GameClock(day = Clock.daysUntilAge(0, 18) - 10))
        val r = Simulation.run(d, s, 90).state
        assertEquals(18, Ages.player(r))
        assertTrue(r.simReports.last().stopReason.contains("18"))
        assertTrue(r.clock.day <= Clock.daysUntilAge(0, 18) + 1)
        assertNull(r.pendingCompanion)
        assertEquals(1, r.customNpcs.count { it.id == companion.id })
        // erneuter Sprung führt die Ankunft nicht ein zweites Mal aus
        val again = Simulation.run(d, r, 30, stopOnEvents = false).state
        assertEquals(1, again.customNpcs.count { it.id == companion.id })
    }

    @Test fun stopsAtNewStoryEvent() {
        val s = start().copy(completedMissions = setOf("M005"))
        assertNotNull(Dialogues.pendingStory(d, s))
        // Ereignis schon vorher offen: kein Abbruch, bleibt aber erhalten
        val r = Simulation.run(d, s, 30).state
        assertEquals(30, r.clock.day)
        assertNotNull("Story-Ereignis geht nicht verloren", Dialogues.pendingStory(d, r))
    }

    @Test fun noRankTwelveThroughTimeSkip() {
        var s = start()
        repeat(12) { s = Simulation.run(d, s, 365, stopOnEvents = false).state }
        assertTrue("Ränge brauchen Prüfungen/Story", s.player.rank == 1)
        assertTrue(s.player.level < 60)
        Attr.all.forEach { assertTrue((s.player.attributes[it] ?: 0) <= Stats.attributeCap(s.player.level)) }
    }

    /** Zehnjähriger Spielverlauf mit Unterbrechungen: Invarianten über Kalender, Geld, Beziehungen, Ereignisse. */
    @Test fun tenYearSimulationKeepsInvariants() {
        var s = start(seed = 7L).copy(pendingCompanion = CompanionAssignment.dossier())
        var guard = 0
        var lastDay = s.clock.day
        val ages = mutableSetOf<Int>()
        while (s.clock.day < 3650 && guard++ < 200) {
            s = Simulation.run(d, s, 120).state
            assertTrue("Kalender läuft vorwärts", s.clock.day > lastDay)
            lastDay = s.clock.day
            ages += Ages.player(s)
            // offene Story-Ereignisse als gesehen markieren, damit der Lauf weitergeht
            Dialogues.pendingStory(d, s)?.let { s = s.copy(completedDialogues = s.completedDialogues + it.id) }
            s = CompanionAssignment.awaitingIntroduction(s)?.let { CompanionAssignment.introduce(d, s, true).state } ?: s
            assertTrue(s.player.credits >= 0)
            assertTrue(s.player.level in 1..100)
            assertTrue(s.player.health in 1..100)
            assertTrue(s.player.wounds >= 0)
            s.relations.values.forEach { r -> assertTrue(r.friendship in 0..100 && r.trust in 0..100) }
            s.simEventCounts.values.forEach { assertTrue("Ereignisse max. 3×", it <= 3) }
            Attr.all.forEach { assertTrue((s.player.attributes[it] ?: 0) <= Stats.attributeCap(s.player.level)) }
        }
        assertTrue(s.clock.day >= 3650)
        assertEquals(26, Ages.player(s))
        assertEquals("jeder Geburtstag genau einmal", (17..26).map { "age_$it" }.toSet(), s.flags.filter { it.startsWith("age_") }.toSet())
        assertEquals(1, s.customNpcs.size)
        assertTrue(s.career.evaluations.isNotEmpty())
        assertTrue(s.journal.count { it.type == "birthday" } == 10)
        val json = SaveMigration.encode(s)
        assertEquals(s, SaveMigration.decode(json))
    }

    // --- Karriere -----------------------------------------------------------------

    @Test fun evaluationReflectsPerformance() {
        val idle = start()
        val idleEval = Career.evaluate(d, idle).state.career.evaluations.last()
        assertTrue(idleEval.grade >= "D")
        val busy = Simulation.run(d, start().copy(weekPlan = listOf("combat", "tech", "recon", "combat", "exam", "rest", "rest")), 29, stopOnEvents = false).state
        val busyEval = Career.evaluate(d, busy).state.career.evaluations.last()
        assertTrue("Training verbessert die Note", busyEval.score > idleEval.score)
        assertFalse("Nur alle 30 Tage", Career.evaluate(d, Career.evaluate(d, busy).state).ok)
    }

    @Test fun rankTwoNeedsEvaluationAndMultiPhaseExam() {
        var s = start().copy(completedMissions = setOf("M001"))
        s = s.copy(player = s.player.copy(level = 3, reputation = 20))
        s = Relations.update(d, s, "michael") { it.copy(trust = 60) }
        val noEval = Progression.nextRankCheck(d, s)!!
        assertFalse("Ohne Beurteilung keine Beförderung", noEval.ready)
        s = s.copy(career = s.career.copy(evaluations = listOf(Evaluation(s.clock.day, "B", 65, "gut"))))
        assertTrue(Progression.nextRankCheck(d, s)!!.ready)
        assertFalse("Prüfung nur im Direktorat", Career.startExam(d, s).ok)
        s = s.copy(location = "director")
        s = Career.startExam(d, s).state
        assertNotNull(s.activeExam)
        assertFalse("Erst Theorie und Praxis", Career.chooseScenario(d, s, "aid").ok)
        s = Career.takePhase(d, s).state
        s = Career.takePhase(d, s).state
        assertEquals(2, s.activeExam!!.phase)
        s = Career.chooseScenario(d, s, "aid").state
        val e = s.activeExam!!
        assertTrue(e.finished)
        if (e.passed) {
            assertEquals(2, s.player.rank)
            assertTrue(s.journal.any { it.type == "promotion" })
        } else {
            assertEquals(1, s.player.rank)
            assertTrue(s.career.examCooldownUntil > s.clock.day)
            assertTrue(s.career.examPrepRequired >= 30)
            val retry = Career.closeExam(s)
            assertFalse("Sofortige Wiederholung gesperrt", Career.startExam(d, retry).ok)
        }
    }

    @Test fun failedExamRequiresPreparationAndCooldown() {
        var s = start().copy(completedMissions = setOf("M001"), location = "director")
        s = s.copy(player = s.player.copy(level = 3, reputation = 20, attributes = Attr.all.associateWith { 1 }))
        s = Relations.update(d, s, "michael") { it.copy(trust = 50) }
        s = s.copy(career = s.career.copy(evaluations = listOf(Evaluation(s.clock.day, "C", 45, "ok"))))
        s = Career.startExam(d, s).state
        s = Career.takePhase(d, s).state
        s = Career.takePhase(d, s).state
        s = Career.chooseScenario(d, s, "ignore").state
        assertFalse(s.activeExam!!.passed)
        s = Career.closeExam(s)
        assertTrue(Career.examLock(d, s)!!.contains("Tagen"))
        // nach Wartezeit, aber ohne Vorbereitung weiter gesperrt
        s = s.copy(clock = GameClock(s.career.examCooldownUntil, 0), career = s.career.copy(evaluations = listOf(Evaluation(s.career.examCooldownUntil, "C", 45, "ok"))))
        assertTrue(Career.examLock(d, s)!!.contains("Auflage"))
        s = s.copy(career = s.career.copy(examPrep = s.career.examPrepRequired), player = s.player.copy(energy = 100))
        assertNull(Career.examLock(d, s))
    }

    @Test fun trainingHasDailyLimitAndTrainerBonus() {
        var s = start().copy(location = "training", clock = GameClock(1, 1))
        s = Relations.update(d, s, "michael") { it.copy(trust = 70) }
        assertTrue(Progression.trainerBonus(d, s, "michael", Attr.FITNESS) > 0)
        assertEquals(0, Progression.trainerBonus(d, s, "michael", Attr.INTELLECT))
        var count = 0
        repeat(6) {
            s = s.copy(player = s.player.copy(energy = 100), clock = GameClock(1, 1))
            val r = Progression.train(d, s, "train_theory", trainerId = "michael", intensityId = "light")
            if (r.ok) { count++; s = r.state.copy(clock = GameClock(1, 1)) }
        }
        assertEquals(Progression.DAILY_SESSIONS + 1, count)
    }

    @Test fun mentorSessionsWithMichael() {
        var s = start().copy(location = "training", clock = GameClock(1, 1))
        assertNotNull("zu wenig Vertrauen", Career.mentorAvailable(d, s.let { Relations.update(d, it, "michael") { r -> r.copy(trust = 10) } }))
        s = Relations.update(d, s, "michael") { it.copy(trust = 60) }
        val r = Career.mentorExamPrep(d, s)
        assertTrue(r.messages.toString(), r.ok)
        assertEquals(15, r.state.career.examPrep)
        assertFalse("einmal pro Tag", Career.mentorTraining(d, r.state.copy(location = "training")).ok)
    }

    // --- Inventar ---------------------------------------------------------------------

    @Test fun loadoutsSaveAndApplyWithRestrictions() {
        var s = start()
        s = Inventory.add(d, s, "camera", 1).state
        s = Inventory.add(d, s, "binoculars", 1).state
        val cam = s.player.inventory.first { it.itemId == "camera" }
        s = Inventory.equip(d, s, cam.uid).state
        s = Inventory.saveLoadout(d, s, "recon").state
        assertEquals("camera", s.loadouts.first { it.id == "recon" }.items["gadget"])
        val bino = s.player.inventory.first { it.itemId == "binoculars" }
        s = Inventory.equip(d, s, bino.uid).state
        s = Inventory.applyLoadout(d, s, "recon").state
        assertEquals(cam.uid, s.player.equipment["gadget"])
        // Gegenstand im Spind wird beim Anlegen geholt
        s = Inventory.unequip(d, s, "gadget").state
        s = Inventory.toStorage(d, s, cam.uid).state
        s = Inventory.applyLoadout(d, s, "recon").state
        assertEquals(cam.uid, s.player.equipment["gadget"])
        // Rangsperre greift auch im Profil
        s = Inventory.add(d, s, "night_vision", 1).state
        val nv = s.loadouts.map { if (it.id == "analysis") it.copy(items = mapOf("gadget" to "night_vision")) else it }
        val r = Inventory.applyLoadout(d, s.copy(loadouts = nv), "analysis")
        assertNull(r.state.player.equipment["gadget"])
        assertTrue(r.messages.any { it.contains("Rang") })
    }

    @Test fun buyAndReturnNeverProfits() {
        var s = start().let { it.copy(player = it.player.copy(credits = 5000, rank = 6)) }
        d.items.filter { it.buyable && it.baseValue > 0 && it.minRank <= 6 }.forEach { item ->
            val before = s.player.credits
            val b = Inventory.buy(d, s, item.id)
            assertTrue(b.ok)
            val st = b.state.player.inventory.last { it.itemId == item.id }
            val sold = Inventory.sell(d, b.state, st.uid)
            assertTrue("${item.id}: kein Gewinn", sold.state.player.credits < before)
            s = sold.state
        }
        assertEquals("Rabatt ab Rang 6", Inventory.price(d.itemById.getValue("laptop")) * 90 / 100, Inventory.price(d.itemById.getValue("laptop"), s))
    }

    @Test fun compareShowsDifferences() {
        var s = start()
        s = Inventory.add(d, s, "camera", 1).state
        val cmp = Inventory.compare(d, s, s.player.inventory.first { it.itemId == "camera" }.uid)
        assertTrue(cmp.any { it.label == "Wahrnehmung" && it.delta > 0 })
    }

    @Test fun itemsGiveSituationalMissionBonus() {
        val opts = d.missions.flatMap { m -> m.stages.flatMap { it.options } }.filter { it.itemBonus.isNotEmpty() }
        assertTrue(opts.size >= 5)
        var s = start()
        val opt = opts.first { "camera" in it.itemBonus }
        assertEquals(0, Missions.itemBonus(d, s, opt))
        s = Inventory.add(d, s, "camera", 1).state
        val carried = Missions.itemBonus(d, s, opt)
        s = Inventory.equip(d, s, s.player.inventory.first { it.itemId == "camera" }.uid).state
        assertTrue(Missions.itemBonus(d, s, opt) > carried)
    }

    @Test fun archiveOpensAtRankSeven() {
        val s = start()
        val room = d.roomById.getValue("archives")
        assertNotNull(World.roomLocked(d, s, room))
        assertNull(World.roomLocked(d, s.copy(player = s.player.copy(rank = 7)), room))
    }

    // --- Spielstände -------------------------------------------------------------------

    @Test fun v04SaveWithoutNewFieldsLoads() {
        val s = start()
        val json = Json { encodeDefaults = true }
        val obj = json.parseToJsonElement(SaveMigration.encode(s)).jsonObject
        val stripped = JsonObject(obj.filterKeys { it !in setOf("weekPlan", "simReports", "simEventCounts", "career", "journal", "activeExam", "loadouts") })
        val player = JsonObject(stripped["player"]!!.jsonObject.filterKeys { it != "woundProgress" })
        val legacy = JsonObject(stripped + ("player" to player))
        val loaded = SaveMigration.decode(legacy.toString())
        assertEquals(s.player.codename, loaded.player.codename)
        assertEquals(4, loaded.loadouts.size)
        assertEquals(7, loaded.weekPlan.size)
        assertEquals(CareerState(), loaded.career)
        assertTrue(Simulation.run(d, loaded, 30).ok)
    }
}

package de.protokollnull.agent

import de.protokollnull.agent.data.SaveMigration
import de.protokollnull.agent.data.SaveRepository
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RulesTest {
    private val d = TestData.data

    private fun gameStart(): GameState {
        var s = GameState(seed = 42L, player = PlayerCharacter(firstName = "Test", codename = "Null"))
        s = Prologue.finish(d, s).state
        // Ankunftsdialog überspringen
        return s.copy(activeDialogue = null, completedDialogues = s.completedDialogues + "dlg_arrival", flags = s.flags + "met_michael")
    }

    private fun atAge(s: GameState, age: Int) = s.copy(clock = GameClock(day = (age - 16) * 365))

    @Test fun xpLevelCapsAt100() {
        var s = gameStart()
        repeat(40) { s = Progression.grantXp(d, s, 500_000).state }
        assertEquals(100, s.player.level)
        assertEquals(0, s.player.xp)
        val before = s.player.skillPoints
        s = Progression.grantXp(d, s, 10_000).state
        assertEquals(100, s.player.level)
        assertEquals(before, s.player.skillPoints)
    }

    @Test fun recruitEditorUnlocksAt18() {
        val s16 = gameStart()
        assertFalse(Social.canCreateRecruit(s16))
        assertFalse(Social.createRecruit(d, s16, "Mira", "K", "female", AvatarSpec(), listOf("loyal"), "", "field", emptyList(), emptyMap()).ok)
        val s18 = atAge(s16, 18)
        assertTrue(Social.canCreateRecruit(s18))
        val r = Social.createRecruit(d, s18, "Mira", "K", "female", AvatarSpec(), listOf("loyal"), "", "field", emptyList(), mapOf("observe" to 1))
        assertTrue(r.ok)
        val npc = r.state.customNpcs.single()
        assertEquals(16, Ages.npc(d, r.state, npc.id))
        // nicht sofort Partner: niedriges Vertrauen, keine Romanze
        assertTrue(Relations.get(d, r.state, npc.id).trust < 30)
        assertEquals(0, Relations.get(d, r.state, npc.id).romanceStage)
    }

    @Test fun ageAppropriateRomanceAndOptionalAlexRoute() {
        val s16 = gameStart()
        assertEquals(17, Ages.npc(d, s16, "maya"))
        assertTrue("16/17: altersgerechte Romanze", Romance.allowed(d, s16, "maya"))
        assertEquals(2, Romance.maxStage(d, s16, "maya"))
        assertFalse("Alex ohne Annäherungsentscheidung bleibt Freundin", Romance.allowed(d, s16, "alex"))
        val unlocked = s16.copy(flags = s16.flags + "alex_romance_unlocked")
        assertTrue("Alex 17, Spieler 16", Romance.allowed(d, unlocked, "alex"))
        assertEquals(2, Romance.maxStage(d, unlocked, "alex"))
        val adult = atAge(s16, 18).copy(flags = s16.flags + "alex_romance_unlocked")
        assertTrue(Romance.allowed(d, adult, "alex"))
        assertEquals(5, Romance.maxStage(d, adult, "alex"))
    }

    @Test fun eighteenAndSixteenCanBeTogetherButNotMoveIn() {
        val s18 = atAge(gameStart(), 18)
        val r = Social.createRecruit(d, s18, "Mira", "K", "female",
            AvatarSpec(), emptyList(), "", "tech", emptyList(), emptyMap())
        var s = r.state
        val id = s.customNpcs.single().id
        assertEquals(16, Ages.npc(d, s, id))
        assertTrue(Romance.allowed(d, s, id))
        assertEquals(2, Romance.maxStage(d, s, id))
        s = Effs.apply(Effects(affection = 30), d, s, id).state
        assertEquals(30, Relations.get(d, s, id).affection)
        s = Relations.update(d, s, id) { it.copy(
            romanceStage = 2, romanceSinceDay = s.clock.day - 100,
            affection = 100, trust = 100
        ) }
        s = s.copy(player = s.player.copy(apartment = true))
        assertNull("auch mit Wohnung keine Erwachsenenfunktion", Romance.nextStep(d, s, id))
        assertFalse(Romance.advance(d, s, id).ok)
        s = s.copy(clock = GameClock(day = s.clock.day + 730))
        assertEquals(18, Ages.npc(d, s, id))
        assertEquals(5, Romance.maxStage(d, s, id))
        assertEquals("move_in", Romance.nextStep(d, s, id)?.id)
    }

    @Test fun nineteenAndSixteenBlockedUntilBothAdults() {
        val nineteen = atAge(gameStart(), 19)
        val s = Social.createRecruit(d, nineteen, "Mira", "K", "female",
            AvatarSpec(), emptyList(), "", "intel", emptyList(), emptyMap()).state
        val id = s.customNpcs.single().id
        assertFalse("19/16 zu weit auseinander", Romance.allowed(d, s, id))
        assertNull(Romance.nextStep(d, s, id))
        val changed = Effs.apply(Effects(affection = 25), d, s, id).state
        assertEquals(0, Relations.get(d, changed, id).affection)
        val adult = s.copy(clock = GameClock(day = s.clock.day + 730))
        assertTrue("21/18: zwei Erwachsene", Romance.allowed(d, adult, id))
    }

    @Test fun previousTeenRelationshipContinuesAfterMigration() {
        val s16 = gameStart()
        val s = Relations.update(d, s16, "maya") { it.copy(
            romanceStage = 2, romanceSinceDay = 5, affection = 75, trust = 80
        ) }
        assertTrue(Romance.allowed(d, s, "maya"))
        assertEquals(2, Romance.maxStage(d, s, "maya"))
        assertNull(Romance.nextStep(d, s, "maya"))
        val restored = SaveMigration.decode(SaveMigration.encode(s))
        assertEquals(2, Relations.get(d, restored, "maya").romanceStage)
        assertNull(Romance.nextStep(d, restored, "maya"))
        val bothAdults = restored.copy(clock = GameClock(day = 730),
            player = restored.player.copy(apartment = true))
        assertEquals("move_in", Romance.nextStep(d, bothAdults, "maya")?.id)
    }

    @Test fun alexDialogueUnlocksOnlyWhenPlayerChoosesInterest() {
        val s16 = gameStart()
        val young = Relations.update(d, s16.copy(flags = s16.flags + "met_alex"), "alex") {
            it.copy(friendship = 65, trust = 70)
        }
        assertTrue(Dialogues.available(d, young, "alex").any { it.id == "dlg_alex_choice" })
        val dialog = Dialogues.start(d, young, "dlg_alex_choice").state
        val heard = Dialogues.choose(d, dialog, "listen").state
        val chosen = Dialogues.choose(d, heard, "interest").state
        assertTrue("alex_romance_unlocked" in chosen.flags)
        assertTrue(Romance.allowed(d, chosen, "alex"))
        val friendly = Dialogues.choose(d, dialog, "friendship").state
        assertFalse(Romance.allowed(d, friendly, "alex"))
    }

    @Test fun trustIsClamped() {
        var s = gameStart()
        s = Effs.apply(Effects(trust = 500), d, s, "michael").state
        assertEquals(100, Relations.get(d, s, "michael").trust)
        s = Effs.apply(Effects(trust = -900), d, s, "michael").state
        assertEquals(0, Relations.get(d, s, "michael").trust)
    }

    @Test fun itemStacksRespectMaxStack() {
        var s = gameStart()
        s = Inventory.add(d, s, "bandage", 20).state // maxStack 8
        val stacks = s.player.inventory.filter { it.itemId == "bandage" } + s.player.storage.filter { it.itemId == "bandage" }
        assertEquals(22, stacks.sumOf { it.quantity })
        assertTrue(stacks.all { it.quantity <= 8 })
        s = Inventory.removeById(s, "bandage", 5)
        assertEquals(17 - (s.player.storage.filter { it.itemId == "bandage" }.sumOf { it.quantity }), Inventory.count(s, "bandage"))
    }

    @Test fun weightLimitMovesItemsToStorage() {
        var s = gameStart()
        repeat(6) { s = Inventory.add(d, s, "toolbox", 1).state } // je 2,5 kg
        assertTrue(Inventory.weight(d, s.player.inventory) <= Inventory.capacity(d, s))
        assertTrue(s.player.storage.any { it.itemId == "toolbox" })
    }

    @Test fun equipRespectsRankAndAge() {
        var s = gameStart()
        s = Inventory.add(d, s, "pistol_service", 1).state
        val st = s.player.inventory.first { it.itemId == "pistol_service" }
        assertNotNull(Inventory.canEquip(d, s, st))
        assertFalse(Inventory.equip(d, s, st.uid).ok)
    }

    @Test fun rank12RequiresEverything() {
        var s = atAge(gameStart(), 30)
        s = s.copy(player = s.player.copy(rank = 11, level = 100, reputation = 5000))
        var check = Progression.nextRankCheck(d, s)!!
        assertEquals(12, check.rank.rank)
        assertFalse("Vertrauen Percy fehlt", check.ready)
        s = Effs.apply(Effects(trust = 100), d, s, "percy").state
        check = Progression.nextRankCheck(d, s)!!
        assertTrue(check.ready)
        // zu jung
        val young = s.copy(clock = GameClock(day = (28 - 16) * 365))
        assertFalse(Progression.nextRankCheck(d, young)!!.ready)
    }

    @Test fun trainingCostsEnergyAndAddsStress() {
        val s = gameStart()
        val r = Progression.train(d, s, "train_fitness")
        assertTrue(r.ok)
        assertTrue(r.state.player.energy < s.player.energy)
        assertTrue(r.state.player.stress > s.player.stress)
        val tired = s.copy(player = s.player.copy(energy = 5))
        assertFalse(Progression.train(d, tired, "train_fitness").ok)
    }

    @Test fun timeSkipAgesWithoutDailyLoop() {
        val s = gameStart()
        val r = World.timeSkip(d, s, 730)
        assertEquals(18, Ages.player(r.state))
        assertTrue("age_18" in r.state.flags)
        assertTrue(r.state.player.level > s.player.level)
    }

    @Test fun saveRoundTripAndAtomicSlots() {
        val dir = Files.createTempDirectory("pn").toFile()
        val repo = SaveRepository(dir)
        var s = gameStart()
        s = Inventory.add(d, s, "medkit", 2).state
        repo.save("slot2", s)
        val loaded = repo.load("slot2")!!
        assertEquals(s.copy(lastAutosave = loaded.lastAutosave), loaded)
        assertNotNull(repo.info("slot2"))
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun legacyV1SaveMigrates() {
        val legacy = """{"schemaVersion":1,"player":{"name":"Ghost","gender":"male","ageAtStart":16,"elapsedDays":400,"level":3,"xp":20,"rank":2,"reputation":12,"credits":900,"energy":80,"stress":20,"skillPoints":1,"skills":{"defense":1},"relationships":{"michael":70},"inventory":[{"itemId":"id_card","quantity":1},{"itemId":"bandage","quantity":2}]},"currentStoryNode":"training_001","completedMissions":["M001"],"flags":[]}"""
        val s = SaveMigration.decode(legacy)
        assertEquals(CURRENT_SCHEMA, s.schemaVersion)
        assertEquals(17, Ages.player(s))
        assertEquals(3, s.player.level)
        assertEquals(70, s.relations["michael"]?.trust)
        assertTrue("M001" in s.completedMissions)
        assertEquals(2, s.player.inventory.size)
        // und erneut serialisierbar
        assertEquals(s, SaveMigration.decode(SaveMigration.encode(s)))
    }

    @Test fun dialogueChoicesApplyEffectsAndComplete() {
        var s = gameStart()
        s = Dialogues.start(d, s, "dlg_thom_intro").state
        assertNotNull(s.activeDialogue)
        val before = Relations.get(d, s, "thom").friendship
        s = Dialogues.choose(d, s, "friend").state
        s = Dialogues.choose(d, s, "ok").state
        assertNull(s.activeDialogue)
        assertTrue("dlg_thom_intro" in s.completedDialogues)
        assertTrue("met_thom" in s.flags)
        assertTrue(Relations.get(d, s, "thom").friendship > before)
        assertFalse("einmalige Dialoge nicht erneut", Dialogues.start(d, s, "dlg_thom_intro").ok)
    }

    @Test fun missionFlowWithDebrief() {
        var s = gameStart().copy(flags = gameStart().flags + setOf("met_thom", "met_jaden"))
        assertNull(Missions.lockReason(d, s, d.missionById.getValue("M003")))
        s = Missions.start(d, s, "M003", "social", emptyList()).state
        assertNotNull(s.activeMission)
        var guard = 0
        while (s.activeMission?.result == null && guard++ < 10) {
            val st = Missions.currentStage(d, s)!!
            s = Missions.choose(d, s, st.options.last().id).state
        }
        assertNotNull(s.activeMission?.result)
        val xpBefore = s.player.xp + s.player.level * 100000
        s = Missions.finish(d, s).state
        assertNull(s.activeMission)
        assertTrue(s.reports.isNotEmpty())
        assertTrue(s.player.xp + s.player.level * 100000 > xpBefore)
    }

    @Test fun lockedMissionsCannotStart() {
        val s = gameStart()
        assertFalse(Missions.start(d, s, "M012", "balanced", emptyList()).ok) // ab 18 & Rang 4
    }

    @Test fun tacticalBoardIsDeterministicAndFinishes() {
        val s = gameStart()
        val m = d.missionById.getValue("M001")
        val setup = m.stages.first { it.type == "tactical" }.tactical!!
        var t1 = Tactical.setup(d, s, setup, emptyList(), 7L, 1)
        var t2 = Tactical.setup(d, s, setup, emptyList(), 7L, 1)
        repeat(20) {
            t1 = Tactical.endTurn(t1); t2 = Tactical.endTurn(t2)
        }
        assertEquals(t1, t2)
        assertTrue(t1.finished)
    }

    @Test fun generatorRespectsAgeLimits() {
        val s = gameStart()
        repeat(30) { i ->
            val m = MissionGenerator.generate(d, s, i.toLong(), "T$i")
            assertTrue(m.threat <= 2)
            assertTrue(m.type in listOf("dialogue", "investigation", "observation"))
            assertTrue(m.stages.isNotEmpty())
        }
    }

    @Test fun prologueLeadsToGame() {
        var s = GameState(seed = 3L)
        var guard = 0
        while (s.phase == "prologue" && guard++ < 20) {
            val node = Prologue.node(d, s)!!
            s = Prologue.choose(d, s, node.choices.first().id).state
        }
        assertEquals("game", s.phase)
        assertTrue("prologue_done" in s.flags)
        assertTrue(s.player.inventory.any { it.itemId == "id_card" })
        assertEquals("dlg_arrival", s.activeDialogue?.dialogueId)
    }

    @Test fun endingsProduceEpilogue() {
        var s = atAge(gameStart(), 30)
        assertFalse(Endings.pending(s))
        s = s.copy(flags = s.flags + setOf("ending_reform", "voss_arrested", "kira_rescued", "kira_joined"))
        assertTrue(Endings.pending(s))
        val ep = Endings.epilogue(d, s)
        assertTrue(ep.size >= 4)
        assertEquals("Ende: Im Licht", ep.first().title)
        assertTrue(ep.any { it.title == "Kira" })
        assertFalse(Endings.pending(s.copy(flags = s.flags + "epilogue_seen")))
    }

    @Test fun hiddenCharactersAppearAfterFlag() {
        val s = gameStart()
        assertFalse(World.revealed(d, s, "kira"))
        assertTrue(World.revealed(d, s.copy(flags = s.flags + "kira_rescued"), "kira"))
        assertTrue(World.revealed(d, s, "michael"))
    }

    @Test fun storyChainReachesFinale() {
        // Jede Story-Mission hängt an existierenden Missionen; Kapitel 3 endet in der Direktoratsabstimmung
        val m016 = d.missionById.getValue("M016")
        assertTrue("voss_resolved" in m016.requires!!.flagsAll)
        val ranks = d.ranks.associateBy { it.rank }
        assertEquals(listOf("M020"), ranks.getValue(8).requiredMissions)
        assertTrue(d.dialogues.any { it.id == "dlg_final" && it.nodes.first().choices.size == 4 })
    }
}

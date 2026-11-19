package de.protokollnull.agent

import de.protokollnull.agent.data.SaveMigration
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** v0.6 „Living Division“: NPC-Leben, Erinnerungen, Gefährten-Laufbahn, HQ-Ereignisse, Beziehungen 2.0. */
class LivingDivisionTest {
    private val d = TestData.data

    private fun start(seed: Long = 42L, companion: CustomNpc? = null): GameState {
        var s = GameState(seed = seed, player = PlayerCharacter(firstName = "Test", codename = "Null"), pendingCompanion = companion)
        s = Prologue.finish(d, s).state
        return s.copy(activeDialogue = null, completedDialogues = s.completedDialogues + "dlg_arrival", flags = s.flags + "met_michael" + "met_alex")
    }

    private fun adult(seed: Long = 42L) = start(seed).let { it.copy(clock = GameClock(day = 800), living = LivingState()) }

    private fun withLife(s: GameState, id: String, f: (NpcLife) -> NpcLife) =
        s.copy(living = s.living.copy(lives = s.living.lives + (id to f(Life.life(d, s, id)))))

    private fun skipYears(s0: GameState, days: Int): GameState {
        var s = s0
        var guard = 0
        while (s.clock.day < s0.clock.day + days && guard++ < 100) {
            s = Simulation.run(d, s, 120, stopOnEvents = false).state
            s = CompanionAssignment.awaitingIntroduction(s)?.let { CompanionAssignment.introduce(d, s, true).state } ?: s
        }
        return s
    }

    // --- Lebenssimulation ------------------------------------------------------------

    @Test fun lifeSimulationIsDeterministicAndPersistent() {
        val a = Simulation.run(d, start(), 365, stopOnEvents = false).state
        val b = Simulation.run(d, start(), 365, stopOnEvents = false).state
        assertEquals(a.living, b.living)
        assertTrue("alle Figuren haben ein Leben", d.agents.all { it.id in a.living.lives })
        assertEquals(a.clock.day, a.living.lastDay)
        assertEquals(a, SaveMigration.decode(SaveMigration.encode(a)))
        // Ein anderer Seed ergibt ein anderes Leben
        val c = Simulation.run(d, start(seed = 99L), 365, stopOnEvents = false).state
        assertNotEquals(a.living.lives, c.living.lives)
        // Erneutes Fortschreiben am selben Tag ändert nichts (idempotent)
        assertEquals(a.living, Life.advance(d, a).living)
    }

    @Test fun tenYearsOfLivingDivision() {
        val s = skipYears(start(seed = 7L, companion = CompanionAssignment.dossier().copy(careerWish = "intel")), 3650)
        assertTrue(s.clock.day >= 3650)
        val types = s.living.feed.map { it.type }.toSet()
        val lives = s.living.lives
        assertTrue("Beförderungen", lives.values.any { it.examsPassed > 0 })
        assertTrue("Durchgefallene Prüfungen", lives.values.any { it.examsFailed > 0 })
        assertTrue("Einsätze", lives.values.sumOf { it.missions } > 20)
        assertTrue("Verletzungen", lives.values.any { it.injuries > 0 })
        assertTrue("Urlaube oder Versetzungen", "vacation" in types || "transferred" in types)
        assertTrue("NPC-Freundschaften", s.living.bonds.values.any { it >= 50 })
        assertTrue(s.living.feed.size <= Life.FEED_CAP)
        s.living.bonds.values.forEach { assertTrue(it in -100..100) }
        // Paare nur unter Erwachsenen, nie mit Spielerromanzen-Figuren
        s.living.couples.forEach { key ->
            key.split("|").forEach { id ->
                assertTrue("$id volljährig", Ages.npc(d, s, id) >= 18)
                assertFalse("$id ist keine Romanze des Spielers", d.agentById[id]?.romanceable == true)
            }
        }
        // Beziehungen mit dem Spieler entstehen nie automatisch
        s.relations.values.forEach { assertEquals(0, it.romanceStage) }
        // Ränge bleiben in den Grenzen der Laufbahn
        lives.forEach { (id, l) -> assertTrue("$id Rang ${l.rank}", l.rank in 1..9) }
        // Gefährtin hat eine eigene Laufbahn
        val companion = lives.getValue("assigned_companion")
        assertTrue(companion.trainingDays > 100)
        assertTrue(companion.rank > 1)
        assertEquals("intel", companion.focus)
        // Erinnerungen bleiben verdichtet
        s.living.memories.values.forEach { b ->
            assertTrue(b.entries.size <= Memories.ENTRY_CAP)
            assertTrue(b.summaries.size <= Memories.SUMMARY_CAP)
        }
        assertEquals(s, SaveMigration.decode(SaveMigration.encode(s)))
    }

    @Test fun companionCareerDependsOnCharacterNotJustTime() {
        val eager = CompanionAssignment.dossier().copy(traits = listOf("ehrgeizig", "diszipliniert"), careerWish = "tech")
        val calm = CompanionAssignment.dossier().copy(traits = listOf("ruhig", "humorvoll"), careerWish = "medic")
        val a = skipYears(start(seed = 5L, companion = eager), 730 + 1000)
        val b = skipYears(start(seed = 5L, companion = calm), 730 + 1000)
        val la = Life.life(d, a, "assigned_companion"); val lb = Life.life(d, b, "assigned_companion")
        assertEquals("tech", la.focus); assertEquals("medic", lb.focus)
        assertTrue("Ehrgeiz trainiert mehr (${la.trainingDays} vs ${lb.trainingDays})", la.trainingDays > lb.trainingDays)
        assertTrue(Life.competence(d, a, "assigned_companion", "tech") > Life.competence(d, b, "assigned_companion", "tech"))
        // Rang kommt aus der eigenen Laufbahn, nicht aus der Zeit
        assertEquals(la.rank, Social.customRank(d, a, a.customNpcs.single()))
    }

    @Test fun playerAdviceChangesCompanionFocus() {
        val s0 = skipYears(start(companion = CompanionAssignment.dossier().copy(careerWish = "intel")), 740)
        val s = Relations.update(d, s0, "assigned_companion") { it.copy(trust = 70) }
        val r = Life.setFocus(d, s, "assigned_companion", "medic")
        assertTrue(r.messages.toString(), r.ok)
        assertEquals("medic", Life.life(d, r.state, "assigned_companion").focus)
        assertFalse("nur alle 14 Tage", Life.setFocus(d, r.state, "assigned_companion", "tech").ok)
        // Bei geringem Vertrauen bleibt die Figur bei ihrem Berufswunsch
        val low = Relations.update(d, s0, "assigned_companion") { it.copy(trust = 20) }
        val refused = Life.setFocus(d, low, "assigned_companion", "combat")
        assertFalse(refused.ok)
        assertEquals("intel", Life.life(d, refused.state, "assigned_companion").focus)
    }

    @Test fun presenceFollowsLifeStatus() {
        val s = adult().copy(clock = GameClock(day = 800, slot = 3))
        assertNull(World.npcLocation(d, withLife(s, "alex") { it.copy(status = LifeStatus.MISSION, statusUntil = 802) }, "alex"))
        assertEquals("medical", World.npcLocation(d, withLife(s, "alex") { it.copy(status = LifeStatus.INJURED, statusUntil = 805) }, "alex"))
        assertEquals("city", World.npcLocation(d, withLife(s, "birkhoff") { it.copy(status = LifeStatus.FREE) }, "birkhoff"))
        val gone = withLife(s, "thom") { it.copy(status = LifeStatus.TRANSFERRED, statusUntil = 1000) }
        assertTrue(d.hq.rooms.none { "thom" in World.npcsIn(d, gone, it.id) })
        assertTrue(Life.statusLine(d, gone, "thom").startsWith("Versetzt"))
        // Percy verlässt in seiner Freizeit nicht das Direktorat
        val percy = withLife(s, "percy") { it.copy(status = LifeStatus.FREE) }
        assertEquals(World.npcLocation(d, s, "percy"), World.npcLocation(d, percy, "percy"))
    }

    @Test fun statusExpiresAndNpcReturns() {
        var s = withLife(adult(), "thom") { it.copy(status = LifeStatus.VACATION, statusUntil = 801) }
        s = s.copy(living = s.living.copy(lastDay = 800))
        s = World.sleep(d, s.copy(clock = GameClock(800, 4))).state
        assertEquals(LifeStatus.VACATION, s.living.lives["thom"]?.status)
        s = World.sleep(d, s.copy(clock = s.clock.copy(slot = 4))).state
        assertNotEquals(LifeStatus.VACATION, s.living.lives["thom"]?.status)
        assertTrue(s.living.feed.any { it.npcId == "thom" && it.type == "returned" })
    }

    // --- Erinnerungen ---------------------------------------------------------------

    @Test fun memoriesAreStructuredAndSummarized() {
        var s = adult()
        repeat(40) { i -> s = Relations.update(d, s.copy(clock = GameClock(800 + i)), "alex") { it.copy(memories = it.memories + "Gemeinsames Training Nr. $i") } }
        val book = Memories.book(s, "alex")
        assertTrue(book.entries.size <= Memories.ENTRY_CAP)
        assertTrue(book.summaries.isNotEmpty())
        assertEquals(40, book.entries.size + book.summaries.sumOf { it.count })
        assertEquals("training", book.entries.last().type)
        assertEquals(839, book.entries.last().day)
        assertEquals(12, Relations.get(d, s, "alex").memories.size)
        // Geschenk und Konflikt werden erkannt
        assertEquals("gift", Memories.infer("Geschenk: Kamera").first)
        assertEquals("conflict", Memories.infer("Konflikt: Du hast mich angelogen").first)
    }

    @Test fun legacyMemoriesMigrateFromSchemaTwo() {
        val json = """{"schemaVersion":2,"phase":"game","clock":{"day":400},"relations":{"alex":{"memories":["Ein stiller Pakt mit Alex","Geschenk: Uhr"]}}}"""
        val s = SaveMigration.decode(json)
        assertEquals(CURRENT_SCHEMA, s.schemaVersion)
        val book = Memories.book(s, "alex")
        assertEquals(2, book.entries.size)
        assertEquals(listOf("trust", "gift"), book.entries.map { it.type })
        assertTrue(book.entries.all { it.day == -1 })
    }

    @Test fun v05SaveWithoutLivingDataLoadsAndStartsLife() {
        val s = Simulation.run(d, start(), 40, stopOnEvents = false).state
        val obj = Json.parseToJsonElement(SaveMigration.encode(s)).jsonObject
        val v05 = JsonObject(obj.filterKeys { it != "living" } + ("schemaVersion" to Json.parseToJsonElement("2")))
        val agentsStripped = v05.toString()
        val loaded = SaveMigration.decode(agentsStripped)
        assertEquals(s.player, loaded.player)
        assertTrue(loaded.living.lives.isEmpty())
        // Erster neuer Tag startet das Leben ohne rückwirkend Jahre zu simulieren
        val next = World.sleep(d, loaded.copy(clock = loaded.clock.copy(slot = 4))).state
        assertTrue(next.living.lives.isNotEmpty())
        assertEquals(next.clock.day, next.living.lastDay)
        assertTrue(next.living.lives.values.all { it.trainingDays <= 1 })
        assertTrue(Simulation.run(d, next, 30).ok)
    }

    // --- HQ-Ereignisse ----------------------------------------------------------------

    private fun pending(s: GameState, id: String) = s.copy(living = s.living.copy(pendingHqEvent = id, pendingHqEventDay = s.clock.day))

    @Test fun hqEventHasRealConsequences() {
        val base = withLife(adult(), "alex") { it.copy(status = LifeStatus.INJURED, statusUntil = 805) }
        assertTrue(HqEvents.eligible(d, base, d.hqEventById.getValue("hq_alex_injured"), base.clock.day))
        val before = Relations.get(d, base, "alex")
        val r = HqEvents.resolve(d, pending(base, "hq_alex_injured"), "visit")
        assertTrue(r.ok)
        assertNull(r.state.living.pendingHqEvent)
        assertEquals(before.friendship + 5, Relations.get(d, r.state, "alex").friendship)
        assertTrue(Memories.book(r.state, "alex").entries.any { it.type == "hq" })
        assertEquals("visit", r.state.living.hqHistory.last().choiceId)
        // Nicht wiederholbar, bevor die Sperre abläuft
        assertFalse(HqEvents.eligible(d, r.state, d.hqEventById.getValue("hq_alex_injured"), r.state.clock.day + 10))
    }

    @Test fun betrayalCreatesConflictAndChangesLife() {
        val s = adult().let { Relations.update(d, it, "percy") { r -> r } }
        val r = HqEvents.resolve(d, pending(s, "hq_percy_report"), "betray")
        assertTrue(r.ok)
        assertNotNull(r.state.living.conflicts["alex"])
        assertTrue(Life.life(d, r.state, "alex").mood < Life.life(d, s, "alex").mood)
        assertTrue(Relations.get(d, r.state, "alex").trust < Relations.get(d, s, "alex").trust)
        // Konflikt blockiert romantische Schritte, bis er geklärt ist
        val romance = r.state.copy(flags = r.state.flags + "alex_romance_unlocked")
        assertEquals("conflict", Romance.nextStep(d, romance, "alex")?.id)
    }

    @Test fun eventsChangeDutyRosterAndExpireWithConsequences() {
        val s = adult().let { Relations.update(d, it, "thom") { r -> r } }
        val r = HqEvents.resolve(d, pending(s, "hq_amanda_bench"), "agree").state
        assertEquals(LifeStatus.VACATION, r.living.lives["thom"]?.status)
        assertNull(World.npcLocation(d, r, "thom"))
        // Ein verpasstes Ereignis wendet „ignore“ an
        val p = pending(Relations.update(d, s, "alex") { it }, "hq_alex_promoted").copy(flags = s.flags + "lf_alex_promoted")
        val friendship = Relations.get(d, p, "alex").friendship
        val later = HqEvents.daily(d, p, p.clock.day + HqEvents.EXPIRE_DAYS)
        assertNull(later.living.pendingHqEvent)
        assertEquals(friendship - 2, Relations.get(d, later, "alex").friendship)
        assertFalse("lf_alex_promoted" in later.flags)
    }

    @Test fun eventsRespectKnowledgeAndArrival() {
        val s = adult()
        assertFalse("Nikita unbekannt", HqEvents.eligible(d, s, d.hqEventById.getValue("hq_nikita_drop"), s.clock.day))
        assertFalse("Gefährte noch nicht da", HqEvents.eligible(d, s.copy(flags = s.flags + "lf_assigned_companion_promoted"), d.hqEventById.getValue("hq_companion_promoted"), s.clock.day))
        // Über viele Tage im Tagesspiel entstehen Ereignisse – und nur zulässige
        var t = s.copy(living = s.living.copy(lastDay = s.clock.day))
        var seen = 0
        repeat(120) {
            t = World.sleep(d, t.copy(clock = t.clock.copy(slot = 4))).state
            HqEvents.pending(d, t)?.let { e ->
                seen++
                assertTrue(e.id, e.npcId == null || Relations.isKnownNpc(d, t, e.npcId))
                t = HqEvents.resolve(d, t, HqEvents.visibleChoices(d, t, e).first().id).state
            }
        }
        assertTrue("HQ-Ereignisse im Tagesspiel ($seen)", seen > 3)
        assertTrue(t.living.hqHistory.size >= seen)
    }

    // --- Beziehungen 2.0 -------------------------------------------------------------------

    @Test fun giftsMatchInterests() {
        var s = adult()
        s = Inventory.add(d, s, "camera", 2).state
        s = Inventory.add(d, s, "flashlight", 1).state
        val camera = Bonds.preview(d, s, "alex", "camera")
        val torch = Bonds.preview(d, s, "alex", "flashlight")
        assertEquals(10, camera.bonus)
        assertTrue(camera.bonus > torch.bonus)
        val f0 = Relations.get(d, s, "alex").friendship
        val r = Bonds.give(d, s, "alex", "camera")
        assertTrue(r.ok)
        assertEquals((f0 + 10).coerceAtMost(100), Relations.get(d, r.state, "alex").friendship)
        assertTrue("perfect_gift" in Bonds.milestones(r.state, "alex"))
        assertEquals("Wiederholung wirkt kaum", 1, Bonds.preview(d, r.state, "alex", "camera").bonus)
        assertTrue(Social.interactions(d, r.state, "alex").any { it.id == "gift:camera" })
    }

    private fun romanceReady(): GameState {
        var s = adult()
        s = s.copy(flags = s.flags + "alex_romance_unlocked", player = s.player.copy(interests = listOf("Musik", "Fotografie"),
            attributes = s.player.attributes + (Attr.CHARISMA to 100), skills = s.player.skills + ("empathy" to 5)))
        s = Relations.update(d, s, "alex") { it.copy(friendship = 80, trust = 80, affection = 70, romanceStage = 1) }
        s = Relations.update(d, s, "maya") { it.copy(friendship = 60, trust = 60, affection = 40, romanceStage = 1) }
        return s
    }

    @Test fun committingIsADeliberateChoiceThatEndsOtherInterests() {
        val s = romanceReady()
        val step = Romance.nextStep(d, s, "alex")
        assertEquals("confess", step?.id)
        assertTrue(step!!.available)
        assertEquals(listOf("maya"), Romance.otherInterests(d, s, "alex"))
        var r = Romance.advance(d, s, "alex")
        var tries = 0
        while (!r.ok && tries++ < 10) r = Romance.advance(d, Relations.update(d, s, "alex") { it.copy(affection = 70) }.copy(rolls = s.rolls + tries * 7), "alex")
        assertTrue(r.messages.toString(), r.ok)
        assertEquals(2, Relations.get(d, r.state, "alex").romanceStage)
        assertEquals(0, Relations.get(d, r.state, "maya").romanceStage)
        assertTrue("maya" in r.state.living.closedRomances)
        assertTrue("partner" in Bonds.milestones(r.state, "alex"))
        assertEquals("busy", Romance.nextStep(d, r.state, "maya")?.id)
        // Trennung ist ebenfalls bewusst – und hinterlässt einen Konflikt
        val split = Romance.stayFriends(d, r.state, "alex").state
        assertEquals(0, Relations.get(d, split, "alex").romanceStage)
        assertNotNull(split.living.conflicts["alex"])
        assertTrue("breakup" in Bonds.milestones(split, "alex"))
    }

    @Test fun stayingFriendsKeepsTheFriendshipAndPausesRomance() {
        val s = romanceReady()
        val r = Romance.stayFriends(d, s, "maya").state
        assertEquals(0, Relations.get(d, r, "maya").romanceStage)
        assertNull(r.living.conflicts["maya"])
        assertTrue("stay_friends" in Bonds.milestones(r, "maya"))
        assertEquals("closed", Romance.nextStep(d, r, "maya")?.id)
        assertEquals("interest", Romance.nextStep(d, r.copy(clock = GameClock(r.clock.day + 91)), "maya")?.id)
    }

    @Test fun conflictsCanBeTalkedOut() {
        var s = Bonds.addConflict(d, adult(), "alex", "Du hast mich angelogen", 1)
        s = s.copy(player = s.player.copy(attributes = s.player.attributes + (Attr.CHARISMA to 100), skills = s.player.skills + ("empathy" to 5)))
        s = Relations.update(d, s, "alex") { it.copy(trust = 70) }
        assertTrue(Social.interactions(d, s, "alex").any { it.id == "talk_out" })
        var tries = 0
        while ("alex" in s.living.conflicts && tries++ < 8) {
            s = Bonds.talkOut(d, s, "alex").state
            s = World.sleep(d, s.copy(clock = s.clock.copy(slot = 4))).state
        }
        assertNull(s.living.conflicts["alex"])
        assertTrue("conflict_resolved" in Bonds.milestones(s, "alex"))
    }

    @Test fun compatibilityExplainsItself() {
        val base = adult()
        val match = base.copy(player = base.player.copy(interests = listOf("Musik", "Fotografie")))
        val c0 = Bonds.compatibility(d, base, "alex"); val c1 = Bonds.compatibility(d, match, "alex")
        assertTrue(c1.score > c0.score)
        assertTrue(c1.reasons.any { "Interessen" in it })
        val conflicted = Bonds.addConflict(d, match, "alex", "Streit", 2)
        assertTrue(Bonds.compatibility(d, conflicted, "alex").score < c1.score)
    }

    // --- Altersregel bleibt bestehen ---------------------------------------------------------

    @Test fun romanceStaysAdultsOnly() {
        val young = start().copy(flags = start().flags + "alex_romance_unlocked")
        assertFalse(Romance.allowed(d, young, "alex"))
        assertNull(Romance.nextStep(d, young, "alex"))
        listOf("dlg_alexv6_rooftop", "dlg_alexv6_reconsider").forEach { id ->
            val dlg = d.dialogueById.getValue(id)
            assertTrue("$id ab 18", (dlg.conditions?.minAge ?: 0) >= 18)
        }
        val crush = Relations.update(d, young, "alex") { it.copy(friendship = 90, trust = 90, affection = 0) }
        val r = Social.interact(d, crush, "alex", "time")
        assertEquals("keine Zuneigung unter 18", 0, Relations.get(d, r.state, "alex").affection)
    }

    @Test fun alexRouteHasHandwrittenScenesAndFriendshipPath() {
        val scenes = d.dialogues.filter { it.id.startsWith("dlg_alexv6_") }
        assertTrue(scenes.size in 6..10)
        assertTrue(scenes.all { it.npcId == "alex" })
        // Freundschaftsweg ohne Romanzen-Flag
        val friendship = scenes.filter { "alex_friendship_chosen" in (it.conditions?.flagsAll ?: emptyList()) }
        assertTrue(friendship.isNotEmpty())
        assertTrue(friendship.none { "alex_romance_unlocked" in (it.conditions?.flagsAll ?: emptyList()) })
        // Romantische Effekte nur in Szenen ab 18
        scenes.forEach { sc ->
            val romantic = sc.nodes.flatMap { it.choices }.any { (it.effects?.affection ?: 0) > 0 }
            if (romantic) assertTrue(sc.id, (sc.conditions?.minAge ?: 0) >= 18)
        }
    }

    @Test fun hqEventCatalogIsValid() {
        val npcs = d.agents.map { it.id }.toSet() + "assigned_companion"
        assertTrue(d.hqEvents.size >= 20)
        assertEquals(d.hqEvents.size, d.hqEvents.map { it.id }.toSet().size)
        d.hqEvents.forEach { e ->
            (listOfNotNull(e.npcId) + e.involved + e.requirePresent + e.requireStatus.keys).forEach { assertTrue("${e.id}: $it", it in npcs) }
            e.requireStatus.values.forEach { assertTrue(e.id, it in LifeStatus.all) }
            assertTrue(e.id, e.choices.count { it.id != "ignore" } >= 2)
            e.choices.forEach { c ->
                assertTrue("${e.id}/${c.id}", c.result.isNotBlank())
                c.check?.let { assertTrue(it.attribute in Attr.all); it.skill?.let { sk -> assertTrue("${e.id} $sk", sk in d.skillById) } }
                (c.life + c.failLife).forEach { l ->
                    assertTrue(l.npcId in npcs)
                    l.status?.let { st -> assertTrue(st in LifeStatus.all) }
                    l.bondWith?.let { b -> assertTrue(b in npcs) }
                }
                (c.npcEffects.keys + c.failNpcEffects.keys + c.conflicts.keys + c.memories.keys).forEach { assertTrue("${e.id}: $it", it in npcs) }
            }
        }
    }
}

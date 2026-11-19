package de.protokollnull.agent

import de.protokollnull.agent.data.SaveMigration
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** v0.7 „Shadow Operations“: Missionsdirektor 2.0, Taktik 2.0, Fraktionen, Akten, Ketten, Teamstatus. */
class ShadowOpsTest {
    private val d = TestData.data

    private fun start(seed: Long = 42L, companion: CustomNpc? = null): GameState {
        var s = GameState(seed = seed, player = PlayerCharacter(firstName = "Test", codename = "Null"), pendingCompanion = companion)
        s = Prologue.finish(d, s).state
        return s.copy(activeDialogue = null, completedDialogues = s.completedDialogues + "dlg_arrival", flags = s.flags + "met_michael" + "met_alex" + "met_birkhoff")
    }

    private fun adult(seed: Long = 42L) = start(seed).let { it.copy(clock = GameClock(day = 800), player = it.player.copy(rank = 4, energy = 100, health = 100)) }

    private fun board(rows: List<String>, objective: String = "eliminate", s: GameState = adult(), team: List<String> = emptyList(), hold: Int = 0) =
        Tactical.setup(d, s, TacticalSetup(rows, objective = objective, maxTurns = 20, holdTurns = hold), team, 11L, 1)

    private fun withLife(s: GameState, id: String, f: (NpcLife) -> NpcLife) =
        s.copy(living = s.living.copy(lives = s.living.lives + (id to f(Life.life(d, s, id)))))

    private fun place(t: TacticalState, id: String, x: Int, y: Int) = t.copy(units = t.units.map { if (it.id == id) it.copy(x = x, y = y) else it })

    // --- Taktik 2.0 --------------------------------------------------------------------

    @Test fun mapLibraryIsValidAndVaried() {
        val maps = TacticalMaps.all
        assertTrue("mindestens 12 Karten (${maps.size})", maps.size >= 12)
        assertTrue("mindestens 4 Umgebungen", maps.map { it.environment }.toSet().size >= 4)
        assertEquals(maps.size, maps.map { it.id }.toSet().size)
        assertEquals("keine umbenannten Duplikate", maps.size, maps.map { it.rows }.toSet().size)
        maps.forEach { m ->
            assertEquals(m.id, 11, m.rows.size)
            assertTrue(m.id, m.rows.all { it.length == 9 })
            val chars = m.rows.joinToString("")
            assertTrue(m.id, chars.count { it == 'P' } >= 4)
            assertTrue(m.id, chars.count { it in "EGO" } >= 2)
            listOf('X', 'Z', 'V').forEach { c -> assertEquals("${m.id} $c", 1, chars.count { it == c }) }
            assertTrue(m.id, chars.count { it == 'I' } >= 2 && chars.count { it == 'R' } >= 2)
            // Alle Ziele vom Teamstart aus erreichbar
            val t = Tactical.setup(d, adult(), TacticalSetup(emptyList(), mapId = m.id, objective = "eliminate"), emptyList(), 1L, 1)
            val start = m.rows.indexOfFirst { 'P' in it }.let { y -> m.rows[y].indexOf('P') to y }
            val reach = Tactical.reachable(t.copy(units = emptyList()), TacticalUnit("p", "p", true, start.first, start.second, 1, 1), 999) + start
            m.rows.forEachIndexed { y, r -> r.forEachIndexed { x, c -> if (c in "XIRZVEGOP") assertTrue("${m.id}: $c bei $x,$y erreichbar", (x to y) in reach) } }
        }
        assertTrue(Ops.regions.all { TacticalMaps.forRegion(it).isNotEmpty() })
    }

    @Test fun bfsRespectsWallsWaterGlassAndUnits() {
        val t = board(listOf("E....", ".#~w.", "..P..", "....."))
        val p = t.units.first { it.isPlayer }
        val r = Tactical.reachable(t, p)
        assertFalse((1 to 1) in r); assertFalse((2 to 1) in r); assertFalse((3 to 1) in r)
        assertTrue((2 to 3) in r)
        assertTrue("Umweg um Hindernisse", (0 to 1) in r)
    }

    @Test fun lineOfSightBlockedByWallsAndDoorsNotByWaterOrGlass() {
        val t = board(listOf("P.#.E", "P.d.E", "P.~.E", "P.w.E", "P...E"))
        assertFalse(Tactical.lineOfSight(t, 0, 0, 4, 0))
        assertFalse(Tactical.lineOfSight(t, 0, 1, 4, 1))
        assertTrue(Tactical.lineOfSight(t, 0, 2, 4, 2))
        assertTrue(Tactical.lineOfSight(t, 0, 3, 4, 3))
        assertTrue(Tactical.lineOfSight(t, 0, 4, 4, 4))
        // Diagonal zwischen zwei Wandecken hindurch: keine Sicht
        val corner = board(listOf("P#...", "#E...", "....."))
        assertFalse(Tactical.lineOfSight(corner, 0, 0, 2, 2))
    }

    @Test fun noAttacksThroughSolidWalls() {
        var t = board(listOf("..E..", "#####", "..P.."))
        val e = t.units.first { !it.ally }
        val before = e.hp
        t = Tactical.attack(t, "player", e.id)
        assertEquals(before, t.units.first { it.id == e.id }.hp)
        assertEquals(2, t.units.first { it.isPlayer }.ap)
        assertTrue(t.log.last().contains("kein Sichtkontakt"))
        // Gegner schießt ebenfalls nicht durch die Wand
        val hp = t.units.first { it.isPlayer }.hp
        repeat(3) { t = Tactical.endTurn(t) }
        assertEquals(hp, t.units.first { it.isPlayer }.hp)
    }

    @Test fun coverDependsOnDirection() {
        val t = board(listOf("E....", ".....", ".c...", ".P...", "....."))
        val p = t.units.first { it.isPlayer }
        assertEquals("Deckung zwischen Ziel und Angreifer", 20, Tactical.cover(t, 1, 0, p.x, p.y))
        assertEquals("ungedeckt von hinten", 0, Tactical.cover(t, 1, 4, p.x, p.y))
        assertEquals("auf Deckung", 25, Tactical.cover(t, 1, 0, 1, 2))
        val e = t.units.first { !it.ally }
        assertTrue(Tactical.hitChanceFrom(t, e, 1, 0, p) < Tactical.hitChanceFrom(t, e, 1, 4, p))
    }

    @Test fun intelObjectiveRequiresSecuringPoints() {
        var t = board(listOf("#####", "#I.I#", "#.P.#", "#..E#", "#####"), objective = "intel")
        assertEquals(2, t.requiredSecured)
        t = place(t, "player", 1, 1)
        t = Tactical.secure(t, "player")
        assertFalse(t.finished)
        assertEquals("doppelt zählt nicht", 1, Tactical.secure(t, "player").secured.size)
        t = place(t, "player", 3, 1)
        t = Tactical.secure(t, "player")
        assertTrue(t.finished && t.won)
    }

    @Test fun reconAndRendezvousObjectives() {
        var t = board(listOf("R...R", ".....", "..P..", "#####", "E...."), objective = "recon")
        assertEquals(2, t.requiredSecured)
        t = Tactical.move(t, "player", 1, 1).let { place(it, "player", 0, 1) }
        t = Tactical.move(t, "player", 0, 0)
        assertEquals(1, t.secured.size)
        t = Tactical.endTurn(t)
        t = place(t, "player", 4, 1)
        t = Tactical.move(t, "player", 4, 0)
        assertTrue(t.won)
        var z = board(listOf("..Z..", ".....", "..P..", "E...."), objective = "rendezvous")
        z = Tactical.move(z, "player", 2, 0)
        assertTrue(z.won)
    }

    @Test fun extractionNeedsTheVipAlive() {
        val s = adult()
        var t = Tactical.setup(d, s, TacticalSetup(emptyList(), mapId = "market", objective = "extract", vipName = "Zeugin"), emptyList(), 3L, 1)
        val vip = t.units.first { it.vip }
        assertEquals("Zeugin", vip.name)
        val x = t.tiles.indexOfFirst { 'X' in it }.let { y -> t.tiles[y].indexOf('X') to y }
        t = place(t, "vip", x.first, x.second + 1)
        t = Tactical.move(t, "vip", x.first, x.second)
        assertTrue(t.units.first { it.vip }.evacuated)
        assertTrue(t.finished && t.won)
        // Schutzperson kampfunfähig → gescheitert
        var f = Tactical.setup(d, s, TacticalSetup(emptyList(), mapId = "market", objective = "escort"), emptyList(), 3L, 1)
        f = f.copy(units = f.units.map { if (it.vip) it.copy(hp = 0) else it })
        f = Tactical.checkEnd(f)
        assertTrue(f.finished && !f.won)
        // Schutzperson kann nicht angreifen
        val g = Tactical.setup(d, s, TacticalSetup(emptyList(), mapId = "market", objective = "escort"), emptyList(), 3L, 1)
        assertEquals(g, Tactical.attack(g, "vip", g.units.first { !it.ally }.id))
    }

    @Test fun protectObjectiveHoldsZone() {
        var t = board(listOf("#####", "#.Z.#", "#.P.#", "#####", "E...."), objective = "protect", hold = 2)
        t = Tactical.move(t, "player", 2, 1)
        t = Tactical.endTurn(t)
        assertEquals(1, t.held)
        t = Tactical.endTurn(t)
        assertTrue(t.won)
    }

    @Test fun stalemateIsDetected() {
        var t = board(listOf("O#..", "##..", "..P."))
        repeat(Tactical.STALEMATE_ROUNDS + 1) { if (!t.finished) t = Tactical.endTurn(t) }
        assertTrue(t.finished)
        assertFalse(t.won)
        assertTrue(t.log.any { "Patt" in it })
    }

    @Test fun tacticalIsReproducibleOnNewMaps() {
        val s = adult()
        TacticalMaps.all.take(6).forEach { m ->
            var a = Tactical.setup(d, s, TacticalSetup(emptyList(), mapId = m.id, objective = "eliminate"), emptyList(), 99L, 4)
            var b = Tactical.setup(d, s, TacticalSetup(emptyList(), mapId = m.id, objective = "eliminate"), emptyList(), 99L, 4)
            assertEquals(a, b)
            assertTrue("Zusatzgegner bei hoher Gefahr", a.units.count { !it.ally } >= m.rows.joinToString("").count { it in "EGO" } + 1)
            repeat(5) { a = Tactical.endTurn(a); b = Tactical.endTurn(b) }
            assertEquals(a, b)
        }
    }

    @Test fun alliesBringLifeCompetence() {
        val s = adult()
        val base = Tactical.allyUnit(d, s, "alex", 0, 0)
        val trained = Tactical.allyUnit(d, withLife(s, "alex") { it.copy(skillGain = mapOf("combat" to 4)) }, "alex", 0, 0)
        assertTrue(trained.accuracy > base.accuracy)
        assertTrue(trained.maxHp > base.maxHp)
    }

    // --- Missionsdirektor 2.0 --------------------------------------------------------------

    @Test fun poolIsDeterministicAndNeverDuplicates() {
        val s = Ops.refresh(d, adult())
        assertTrue(s.ops.contracts.isNotEmpty())
        assertEquals(s, Ops.refresh(d, s))
        assertEquals(s.ops.contracts, Ops.refresh(d, adult()).ops.contracts)
        val reloaded = SaveMigration.decode(SaveMigration.encode(s))
        assertEquals(s, reloaded)
        assertEquals(reloaded, Ops.refresh(d, reloaded))
        var t = s
        repeat(30) { t = World.sleep(d, t.copy(clock = t.clock.copy(slot = 4))).state }
        val ids = t.ops.contracts.map { it.id } + t.ops.history.map { it.id }
        assertEquals("keine doppelten IDs", ids.size, ids.toSet().size)
        assertTrue(t.ops.contracts.count { it.chainId == null } <= Ops.POOL_MAX)
    }

    @Test fun archetypesAreDistinctWithAlternativeApproaches() {
        assertTrue(Ops.archetypes.size >= 10)
        val s = adult()
        Ops.archetypes.forEach { a ->
            assertTrue(a.id, a.approaches.size >= 2)
            assertTrue(a.id, a.objectives.size >= 3)
            val briefings = (0 until 6).mapNotNull { Ops.generate(d, s, 800, it, Rng.mix(it.toLong(), a.id.hashCode().toLong()), forced = a) }
            assertTrue(a.id, briefings.isNotEmpty())
            briefings.forEach { c ->
                val m = Ops.toMission(d, s, c)
                val approach = m.stages.first { it.id == "approach" }
                assertTrue(approach.options.size >= 2)
                assertTrue("Abschluss mit Wahlfolgen", m.stages.last().options.size >= 2 || c.phases > 1)
                assertTrue(c.risks.isNotEmpty())
                assertTrue(c.successConsequence.isNotBlank() && c.failConsequence.isNotBlank())
                val tacticalObjective = a.objectives.first { it.id == c.objectiveId }.tactical != null
                if (c.approaches.any { it.tactical } && tacticalObjective) assertTrue(m.stages.any { it.type == "tactical" && it.tactical?.mapId == c.mapId })
                // Jede Option führt zu einer existierenden Stage
                val ids = m.stages.map { it.id }.toSet() + setOf(Missions.END_SUCCESS, Missions.END_FAIL, Missions.END_ABORT)
                m.stages.forEach { st -> st.options.forEach { o -> listOfNotNull(o.success.next, o.failure?.next).forEach { n -> assertTrue("${a.id}: $n", n in ids) } }; st.next?.let { assertTrue(it in ids) } }
            }
            assertTrue("Briefings variieren (${a.id})", briefings.map { it.objectiveId to it.regionId }.toSet().size > 1)
        }
    }

    @Test fun minorsOnlyGetSafeContracts() {
        var s = start()
        repeat(60) { s = World.sleep(d, s.copy(clock = s.clock.copy(slot = 4))).state }
        assertTrue(Ages.player(s) < 18)
        val all = s.ops.contracts
        assertTrue(all.isNotEmpty())
        all.forEach { c ->
            assertTrue(c.archetype, Ops.archetypeById.getValue(c.archetype).minorSafe)
            assertTrue(c.difficulty <= 2)
            assertTrue("kein Taktikbrett unter 18", c.approaches.none { it.tactical })
            assertNull(Missions.lockReason(d, s, Ops.toMission(d, s, c)))
        }
    }

    @Test fun teamRespectsLifeStatusAgeAndRank() {
        var s = adult()
        s = Relations.update(d, s, "alex") { it.copy(trust = 60) }
        val probe = MissionDefinition("probe", "", "hq", "x", threat = 2)
        assertTrue("alex" in Missions.teamCandidates(d, s, probe))
        val hurt = withLife(s, "alex") { it.copy(status = LifeStatus.INJURED, statusUntil = 805) }
        assertFalse("alex" in Missions.teamCandidates(d, hurt, probe))
        assertEquals("verletzt", Ops.unavailableReason(d, hurt, "alex"))
        val away = withLife(s, "alex") { it.copy(status = LifeStatus.VACATION, statusUntil = 805) }
        assertFalse("alex" in Missions.teamCandidates(d, away, probe))
        // Gefährtin mit 16: nur sichere Aufgaben
        val comp = CompanionAssignment.dossier().copy(id = "assigned_companion", createdDay = 790, ageAtCreation = 16)
        val withComp = Relations.update(d, s.copy(customNpcs = listOf(comp)), comp.id) { it.copy(trust = 60) }
        assertTrue(Ages.npc(d, withComp, comp.id) < 18)
        assertTrue(comp.id in Missions.teamCandidates(d, withComp, probe.copy(minAge = 16)))
        assertFalse(comp.id in Missions.teamCandidates(d, withComp, probe.copy(minAge = 18)))
        assertFalse(comp.id in Missions.teamCandidates(d, withComp, probe.copy(threat = 3)))
    }

    private fun contractState(arch: String = "data", phases: Int = 1, seed: Long = 5L): Pair<GameState, Contract> {
        val s0 = Ops.refresh(d, adult())
        val c = Ops.generate(d, s0, 800, 99, seed, forced = Ops.archetypeById.getValue(arch))!!.copy(phases = phases, complicationId = "none")
        return s0.copy(ops = s0.ops.copy(contracts = s0.ops.contracts + c)) to c
    }

    private fun play(s: GameState, id: String, result: String, team: List<String> = emptyList()): GameState {
        val started = Missions.start(d, s, id, "balanced", team)
        assertTrue(started.messages.toString(), started.ok)
        val run = started.state.activeMission!!
        return Missions.finish(d, started.state.copy(activeMission = run.copy(result = result, intel = 2))).state
    }

    @Test fun successChangesWorldTeamAndIsSettledOnce() {
        val (s0, c) = contractState()
        val s = Relations.update(d, s0, "alex") { it.copy(trust = 60) }
        val influence = Ops.faction(s, c.targetFaction).influence
        val r = play(s, c.id, "success", listOf("alex"))
        assertNull(Ops.contract(r, c.id))
        assertEquals(ContractStatus.DONE, r.ops.history.last { it.id == c.id }.status)
        assertTrue(Ops.faction(r, c.targetFaction).influence < influence)
        assertEquals(1, Life.life(d, r, "alex").missions)
        assertTrue(r.ops.worldLog.isNotEmpty())
        assertFalse(c.id in r.completedMissions)
        // Kein zweites Verbuchen derselben Nachbesprechung
        val run = MissionRun(c.id, 0L, "brief", result = "success")
        val key = r.ops.settled.first { it.startsWith(c.id) }
        val again = Ops.afterMission(d, r, Ops.toMission(d, s, c), run.copy(seed = key.substringAfter("#").toLong()), "success")
        assertEquals(r, again.first)
        assertNull("Auftrag nicht erneut startbar", Missions.definition(d, r, c.id))
    }

    @Test fun multiDayContractsRunInPhases() {
        val (s, c) = contractState("observation", phases = 2)
        val r = play(s, c.id, "success")
        val mid = Ops.contract(r, c.id)!!
        assertEquals(ContractStatus.IN_PROGRESS, mid.status)
        assertEquals(1, mid.phase)
        assertNotNull(Missions.lockReason(d, r, Ops.toMission(d, r, mid)))
        val tomorrow = r.copy(clock = GameClock(r.clock.day + 1))
        assertNull(Missions.lockReason(d, tomorrow, Ops.toMission(d, tomorrow, mid)))
        assertTrue(Ops.toMission(d, tomorrow, mid).stages.any { it.id == "close" })
        val done = play(tomorrow, c.id, "success")
        assertEquals(ContractStatus.DONE, done.ops.history.last { it.id == c.id }.status)
        // Wer die zweite Phase verstreichen lässt, scheitert mit Begründung
        val late = Ops.refresh(d, r.copy(clock = GameClock(mid.expiresDay + 1), ops = r.ops.copy(lastGenDay = mid.expiresDay)))
        assertEquals(ContractStatus.FAILED, late.ops.history.last { it.id == c.id }.status)
    }

    @Test fun expiryPostponeAndDeclineAreExplained() {
        val (s, c) = contractState()
        val p = Ops.postpone(d, s, c.id)
        assertTrue(p.ok)
        assertEquals(c.expiresDay + 3, Ops.contract(p.state, c.id)!!.expiresDay)
        assertFalse("nur einmal", Ops.postpone(d, p.state, c.id).ok)
        val infl = Ops.faction(s, c.targetFaction).influence
        val expired = Ops.refresh(d, s.copy(clock = GameClock(c.expiresDay + 1)))
        assertEquals(ContractStatus.EXPIRED, expired.ops.history.last { it.id == c.id }.status)
        assertTrue(Ops.faction(expired, c.targetFaction).influence > infl)
        assertTrue(expired.ops.worldLog.any { "verstrichen" in it })
        val dec = Ops.decline(d, s, c.id)
        assertEquals(ContractStatus.DECLINED, dec.state.ops.history.last().status)
    }

    @Test fun teamOnlyComplicationIsEnforced() {
        val (s, c) = contractState()
        val strict = s.copy(ops = s.ops.copy(contracts = s.ops.contracts.map { if (it.id == c.id) it.copy(complicationId = "team_only") else it }))
        assertFalse(Missions.start(d, strict, c.id, "balanced", emptyList()).ok)
    }

    // --- Fraktionen, Akten, Ketten --------------------------------------------------------

    @Test fun factionArcsChangeTheWorld() {
        var s = adult()
        s = Ops.shiftInfluence(s, "syndicate", 40)
        s = Ops.weeklyFactions(d, s)
        assertEquals(1, Ops.faction(s, "syndicate").arc)
        assertTrue(s.ops.worldLog.any { "Gegenoffensive" in it })
        val poor = Ops.weeklyFactions(d, Ops.shiftAttitude(adult(), "government", -60))
        assertEquals(1, Ops.faction(poor, "government").arc)
        val rich = Ops.generate(d, adult(), 800, 1, 77L, forced = Ops.archetypeById.getValue("data"))!!
        val cut = Ops.generate(d, poor, 800, 1, 77L, forced = Ops.archetypeById.getValue("data"))!!
        assertTrue("Budgetkürzung senkt Belohnungen", cut.credits < rich.credits)
    }

    @Test fun casesWeighEvidenceAndContradictions() {
        var s = Cases.daily(d, adult())
        assertTrue(s.ops.cases.containsKey("harbor_mole"))
        listOf("hm1").forEach { s = Cases.addClue(s, it, "Test").first }
        assertEquals(1, Cases.scores(s, "harbor_mole")["Hafenmeister Krüger"])
        s = Cases.addClue(s, "hm4", "Test").first
        assertEquals("widerlegt", 0, Cases.scores(s, "harbor_mole")["Hafenmeister Krüger"])
        assertEquals(1, Cases.contradictionsFound(s, "harbor_mole").size)
        assertFalse(Cases.solve(d, s, "harbor_mole", "Zollbeamtin Wendt", "evidence").ok)
        listOf("hm2", "hm5").forEach { s = Cases.addClue(s, it, "Test").first }
        val solved = Cases.solve(d, s, "harbor_mole", "Zollbeamtin Wendt", "evidence")
        assertTrue(solved.ok)
        assertTrue("case_harbor_mole_solved" in solved.state.flags)
        assertTrue(Ops.faction(solved.state, "syndicate").influence < Ops.faction(s, "syndicate").influence)
        // Falsche Konfrontation hat bleibende Folgen
        var w = Cases.daily(d, adult())
        listOf("hm3", "hm1", "hm6").forEach { w = Cases.addClue(w, it, "Test").first }
        assertTrue(Cases.methods(w, "harbor_mole", "Kranführer Ostrowski").first { it.first.id == "trap" }.second)
        // Bekannte Hinweise überstehen Speichern und Laden
        assertEquals(s.ops.clues, SaveMigration.decode(SaveMigration.encode(s)).ops.clues)
    }

    @Test fun trapContractRevealsTheTruth() {
        var s = Ops.refresh(d, Cases.daily(d, adult()))
        listOf("hm1", "hm3", "hm7").forEach { s = Cases.addClue(s, it, "Test").first }
        val r = Cases.solve(d, s, "harbor_mole", "Hafenmeister Krüger", "trap")
        assertTrue(r.ok)
        val trap = r.state.ops.contracts.single { it.caseId == "harbor_mole" }
        val done = play(r.state, trap.id, "success")
        val case = done.ops.cases.getValue("harbor_mole")
        assertTrue(case.solved)
        assertEquals("Zollbeamtin Wendt", case.accused)
    }

    @Test fun chainsOfferStepsAndApplyDecisions() {
        var s = adult().copy(clock = GameClock(day = 800))
        s = Ops.refresh(d, s)
        val lesson = s.ops.contracts.firstOrNull { it.chainId == "michael_lesson" }
        assertNotNull("Michaels Kette wird angeboten", lesson)
        assertTrue(s.ops.contracts.count { it.chainId != null } <= Chains.MAX_ACTIVE)
        // Drei Schritte durchspielen; der letzte trifft eine Entscheidung
        var t = s
        repeat(3) { i ->
            val c = t.ops.contracts.first { it.chainId == "michael_lesson" }
            assertEquals(i, c.chainStep)
            if (i == 2) t = t.copy(flags = t.flags + Chains.decisionFlag("michael_lesson", 2, "chance"))
            t = play(t, c.id, "success")
            t = t.copy(clock = GameClock(t.clock.day + 2), ops = t.ops.copy(lastGenDay = t.clock.day + 1))
            t = Ops.refresh(d, t)
        }
        assertEquals(3, Chains.progress(t, "michael_lesson"))
        assertTrue("chain_michael_lesson_done" in t.flags)
        assertTrue(Relations.get(d, t, "michael").trust > Relations.get(d, s, "michael").trust)
        assertNull(t.ops.contracts.firstOrNull { it.chainId == "michael_lesson" })
    }

    @Test fun chainCatalogIsConsistent() {
        assertTrue(d.chains.size in 8..12)
        val npcs = d.agents.map { it.id }.toSet()
        d.chains.forEach { c ->
            assertTrue(c.id, c.steps.size >= 2)
            assertTrue(c.id, c.steps.any { it.decisions.size >= 2 })
            c.steps.forEach { st ->
                val a = Ops.archetypeById[st.archetype]
                assertNotNull("${c.id}: ${st.archetype}", a)
                assertTrue("${c.id}: ${st.objective}", a!!.objectives.any { it.id == st.objective })
                assertTrue(st.regionId in Ops.regions)
                st.clientNpc?.let { assertTrue(it in npcs) }
                if (c.minAge < 18) assertTrue("${c.id}: ${st.archetype} unter 18 sicher", a.minorSafe)
                st.decisions.forEach { dec ->
                    dec.clue?.let { assertNotNull("${c.id}: Hinweis $it", Cases.clueDef(it)) }
                    dec.npcEffects.keys.forEach { assertTrue(it in npcs) }
                }
            }
        }
        assertTrue(Cases.all.size >= 3)
        Cases.all.forEach { cs -> assertTrue(cs.truth in cs.suspects); assertTrue(cs.clues.count { it.points == cs.truth } >= 2) }
    }

    // --- Spielstände & Langzeit -----------------------------------------------------------

    @Test fun v06SaveWithoutOpsLoads() {
        val s = Simulation.run(d, start(), 20, stopOnEvents = false).state
        val obj = Json.parseToJsonElement(SaveMigration.encode(s)).jsonObject
        val legacy = JsonObject(obj.filterKeys { it != "ops" } + ("schemaVersion" to Json.parseToJsonElement("3")))
        val loaded = SaveMigration.decode(legacy.toString())
        assertEquals(CURRENT_SCHEMA, loaded.schemaVersion)
        assertEquals(s.player, loaded.player)
        assertEquals(s.living, loaded.living)
        assertTrue(loaded.ops.contracts.isEmpty())
        val next = World.sleep(d, loaded.copy(clock = loaded.clock.copy(slot = 4))).state
        assertTrue(next.ops.contracts.isNotEmpty())
    }

    @Test fun tenYearsOfShadowOperations() {
        var s = start(seed = 9L, companion = CompanionAssignment.dossier())
        var guard = 0
        while (s.clock.day < 3650 && guard++ < 60) {
            s = Simulation.run(d, s, 90, stopOnEvents = false).state
            s = CompanionAssignment.awaitingIntroduction(s)?.let { CompanionAssignment.introduce(d, s, true).state } ?: s
            // Ein paar Aufträge tatsächlich spielen
            s.ops.contracts.firstOrNull { Missions.lockReason(d, s, Ops.toMission(d, s, it)) == null }?.let { c ->
                val st = Missions.start(d, s.copy(player = s.player.copy(energy = 100, health = 100)), c.id, "balanced", emptyList())
                if (st.ok) s = Missions.finish(d, st.state.copy(activeMission = st.state.activeMission!!.copy(result = if (guard % 3 == 0) "fail" else "success"))).state
            }
        }
        assertTrue(s.clock.day >= 3650)
        assertTrue(s.ops.history.size in 1..Ops.HISTORY_CAP)
        assertTrue(s.ops.worldLog.size <= Ops.WORLD_LOG_CAP)
        assertTrue(s.ops.contracts.count { it.chainId == null } <= Ops.POOL_MAX + 3)
        assertEquals(s.ops.clues.size, s.ops.clues.map { it.id }.toSet().size)
        s.ops.factions.values.forEach { assertTrue(it.influence in 0..100 && it.resources in 0..100) }
        s.factionStanding.values.forEach { assertTrue(it in -100..100) }
        assertTrue(s.ops.cases.isNotEmpty())
        // Romanzen entstehen weiterhin nie automatisch
        s.relations.values.forEach { assertEquals(0, it.romanceStage) }
        assertEquals(s, SaveMigration.decode(SaveMigration.encode(s)))
    }
}

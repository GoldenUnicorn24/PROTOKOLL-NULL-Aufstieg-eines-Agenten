package de.protokollnull.agent

import de.protokollnull.agent.data.SaveMigration
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** v0.8 „Director's Legacy“: Teamführung, Delegation, Direktorat, Standorte, Privatleben. */
class DirectorsLegacyTest {
    private val d = TestData.data

    private fun start(seed: Long = 42L): GameState {
        var s = GameState(seed = seed, player = PlayerCharacter(firstName = "Test", codename = "Null"))
        s = Prologue.finish(d, s).state
        return s.copy(activeDialogue = null, completedDialogues = s.completedDialogues + "dlg_arrival", flags = s.flags + "met_michael" + "met_alex" + "met_birkhoff")
    }

    /** Spielstand mit gegebenem Rang am Tag 800 (Spieler volljährig), Lebenssimulation fortgeschrieben. */
    private fun leader(rank: Int = 9, seed: Long = 42L): GameState {
        var s = start(seed)
        s = s.copy(clock = GameClock(day = 800), player = s.player.copy(rank = rank, energy = 100, health = 100, credits = 5000))
        s = Life.advance(d, s)
        s = Directorate.ensure(d, s)
        return s
    }

    private fun withLife(s: GameState, id: String, f: (NpcLife) -> NpcLife) =
        s.copy(living = s.living.copy(lives = s.living.lives + (id to f(Life.life(d, s, id)))))

    /** Macht Figuren dienstbereit, erfahren und loyal (deterministische Testgrundlage). */
    private fun ready(s0: GameState, ids: List<String>, rank: Int = 4): GameState = ids.fold(s0) { acc, id ->
        val s = withLife(acc, id) { it.copy(status = LifeStatus.DUTY, statusUntil = -1, statusNote = "", rank = maxOf(it.rank, rank), mood = 85, stress = 10) }
        Relations.update(d, s, id) { it.copy(loyalty = 95, trust = 80, friendship = maxOf(it.friendship, 50)) }
    }

    private fun adults(s: GameState) = Command.staff(d, s).filter { Ages.npc(d, s, it) >= 18 }

    private fun withContract(s: GameState, difficulty: Int = 1, seed: Long = 5L): Pair<GameState, Contract> {
        val c = Ops.generate(d, s, s.clock.day, 99, seed, forced = Ops.archetypeById.getValue("data"))!!
            .copy(id = "TEST$seed", difficulty = difficulty, phases = 1, phase = 0, complicationId = "none", chainId = null, caseId = null,
                status = ContractStatus.OPEN, expiresDay = s.clock.day + 14, maxTeam = 3)
        return s.copy(ops = s.ops.copy(contracts = s.ops.contracts + c)) to c
    }

    private fun minorRecruit(s0: GameState): Pair<GameState, String> {
        val s = Social.createRecruit(d, s0, "Mira", "K", "female", AvatarSpec(), emptyList(), "", "tech", emptyList(), emptyMap()).state
        return s to s.customNpcs.last().id
    }

    // --- Rangstufen ------------------------------------------------------------------------

    @Test fun rankGatesForLeadershipLevels() {
        val r7 = leader(7)
        val id = adults(r7).first()
        val t7 = ready(r7, listOf(id)).copy(team = listOf(id))
        assertFalse(Command.isLeader(t7))
        assertFalse(Command.setRole(d, t7, id, "recon").ok)
        assertEquals("Teamaufgaben ab Rang 8 (Teamleiter).", Command.canAssign(d, t7, id, "roster"))
        assertNull("Training geht schon vorher", Command.canAssign(d, t7, id, "training"))
        assertNull(t7.director)

        val t8 = t7.copy(player = t7.player.copy(rank = 8))
        assertTrue(Command.setRole(d, t8, id, "recon").ok)
        val (s8, c) = withContract(t8)
        assertEquals("Delegieren ab Rang 9 (Einsatzkommandant).", Delegations.validate(d, s8, c, listOf(id), null))
        assertNull(Delegations.validate(d, s8.copy(player = s8.player.copy(rank = 9)), c, listOf(id), null))

        assertEquals(0, Directorate.tier(leader(9)))
        assertNull(leader(9).director)
        val r10 = leader(10); val r11 = leader(11); val r12 = leader(12)
        assertEquals(listOf(1, 2, 3), listOf(r10, r11, r12).map { Directorate.tier(it) })
        val carry = leader(9).lead.teamBudget
        assertEquals(15000 + carry, r10.director!!.budget)
        assertEquals(50000 + carry, r12.director!!.budget)
        assertEquals(listOf(3, 4, 5), listOf(r10, r11, r12).map { Directorate.maxLevel(it) })
        assertTrue(Directorate.allowedPolicies(r10).isEmpty())
        assertFalse("secrecy" in Directorate.allowedPolicies(r11))
        assertTrue("secrecy" in Directorate.allowedPolicies(r12))
        assertFalse(Directorate.setPolicy(d, r11, "secrecy").ok)
        assertTrue(Directorate.setPolicy(d, r12, "secrecy").ok)
    }

    @Test fun sectionLeaderManagesOnlyOwnDepartment() {
        var s = leader(10)
        assertFalse(Directorate.canManage(s, "intel"))
        s = Directorate.chooseSection(d, s, "intel").state
        assertTrue(Directorate.canManage(s, "intel"))
        assertFalse(Directorate.canManage(s, "medical"))
        assertFalse(Directorate.upgrade(d, s, "medical").ok)
        val up = Directorate.upgrade(d, s, "intel")
        assertTrue(up.ok)
        assertEquals(2, up.state.director!!.departments["intel"])
        assertTrue(Directorate.canManage(leader(11), "medical"))
    }

    // --- Personal: Minderjährige, Verfügbarkeit, Posten -----------------------------------------

    @Test fun minorsOnlyTrainNeverLeadOrDeploy() {
        var (s, kid) = minorRecruit(leader(10))
        assertEquals(16, Ages.npc(d, s, kid))
        val st = Command.status(d, s, kid)
        assertTrue(st.available && st.trainingOnly)
        assertNull(Command.canAssign(d, s, kid, "training"))
        assertNotNull(Command.canAssign(d, s, kid, "prep"))
        assertNotNull(Command.canAssign(d, s, kid, "recon"))
        val (sc, c) = withContract(s)
        assertNotNull("keine Delegation mit Minderjährigen", Delegations.validate(d, sc, c, listOf(kid), null))
        s = Directorate.chooseSection(d, sc, "tech").state
        assertFalse("keine Abteilungsleitung", Directorate.assignHead(d, s, "tech", kid).ok)
        s = Directorate.earn(s, 20000)
        s = Sites.build(d, s, "safehouse").state
        assertFalse("kein Außenstellenposten", Sites.assignStaff(d, s, "safehouse", kid).ok)
        assertTrue(Directorate.dilemmaById.getValue("young_volunteer").choices.all { it.text.startsWith("Ablehnen") })
    }

    @Test fun tasksResolveExactlyOnceAndBlockPartners() {
        val ids = adults(leader(8)).take(2)
        assertEquals(2, ids.size)
        var s = ready(leader(8), ids).let { it.copy(team = ids) }
        s = withLife(s, ids[1]) { it.copy(rank = 2) }
        s = withLife(s, ids[0]) { it.copy(rank = 6) }
        val r = Command.assign(d, s, ids[1], "mentoring", ids[0])
        assertTrue(r.messages.joinToString(), r.ok)
        s = r.state
        assertEquals("task", Command.status(d, s, ids[0]).code)
        assertFalse("Mentor ist gebunden", Command.assign(d, s, ids[0], "roster").ok)
        val task = s.lead.tasks.single()
        val xp0 = Life.life(d, s, ids[1]).careerXp
        val after = Command.daily(d, s.copy(clock = GameClock(task.endDay + 1)))
        assertTrue(after.lead.tasks.single().done)
        val xp1 = Life.life(d, after, ids[1]).careerXp
        assertTrue(xp1 > xp0)
        assertEquals("keine Doppelauswertung", after, Command.daily(d, after))
    }

    @Test fun personnelCanRefuseAndCompanionStaysIndependent() {
        var s = Directorate.earn(leader(8), 10000)
        val id = adults(s).first()
        s = ready(s, listOf(id))
        s = withLife(s, id) { it.copy(mood = 5, stress = 95) }
        s = Relations.update(d, s, id) { it.copy(loyalty = 0) }
        s = Sites.build(d, s, "safehouse").state
        val r = Sites.assignStaff(d, s, "safehouse", id)
        assertFalse(r.ok)
        assertTrue(id in r.state.lead.refusals)
        assertFalse("Abkühlzeit", Personnel.willAccept(d, r.state, id, "social", 1).first)
        // Begleitung entscheidet selbst über eine Versetzung
        val (sc, kid) = minorRecruit(leader(10))
        val t = Personnel.transfer(d, Relations.update(d, sc, kid) { it.copy(trust = 10) }, kid)
        assertFalse(t.ok)
    }

    @Test fun reviewsAndTransfersHaveConsequences() {
        var s = leader(10)
        val id = Command.staff(d, s).first { it in setOf("thom", "jaden", "ryan", "helena", "sonya", "owen") && Ages.npc(d, s, it) >= 18 }
        s = ready(s, listOf(id)).copy(team = listOf(id))
        val rv = Personnel.review(d, s, id)
        assertTrue(rv.ok)
        assertNotNull(rv.state.lead.reviews[id])
        assertFalse("höchstens alle 30 Tage", Personnel.review(d, rv.state, id).ok)
        assertFalse(Personnel.transfer(d, s, "michael").ok)
        val tr = Personnel.transfer(d, rv.state, id)
        assertTrue(tr.ok)
        assertEquals(LifeStatus.TRANSFERRED, tr.state.living.lives[id]?.status)
        assertFalse(id in tr.state.team)
        assertEquals("transferred", Command.status(d, tr.state, id).code)
    }

    // --- Delegation ----------------------------------------------------------------------------

    private fun delegationSetup(): Triple<GameState, Contract, List<String>> {
        val base = leader(9)
        val team = adults(base).take(2)
        val (s, c) = withContract(ready(base, team, rank = 5))
        return Triple(s, c, team)
    }

    @Test fun delegationIsNeverInstantAndUsesRealContracts() {
        val (s0, c, team) = delegationSetup()
        assertTrue(c in Delegations.delegable(d, s0))
        val p = Delegations.prepare(d, s0, c.id, team, team.first())
        assertTrue(p.messages.joinToString(), p.ok)
        assertFalse("keine Doppeldelegation", c in Delegations.delegable(d, p.state))
        assertFalse(Delegations.prepare(d, p.state, c.id, team, null).ok)
        val del = p.state.lead.delegations.single()
        val run = Delegations.start(d, p.state, del.id)
        assertTrue(run.ok)
        val s = run.state
        val running = s.lead.delegations.single()
        assertEquals(DelegationStatus.RUNNING, running.status)
        assertEquals(ContractStatus.DELEGATED, Ops.contract(s, c.id)?.status)
        assertTrue(running.returnDay > s.clock.day)
        team.forEach { assertEquals(LifeStatus.MISSION, s.living.lives[it]?.status) }
        assertEquals("am selben Tag kein Ergebnis", s, Delegations.daily(d, s))
        assertEquals(s, Delegations.daily(d, s.copy(clock = GameClock(running.returnDay - 1))).copy(clock = s.clock))
    }

    @Test fun delegationSettlesExactlyOnce() {
        val (s0, c, team) = delegationSetup()
        var s = Delegations.start(d, Delegations.prepare(d, s0, c.id, team, null).state, "D0").state
        val back = s.lead.delegations.single().returnDay
        for (day in back..back + 3) s = Delegations.daily(d, s.copy(clock = GameClock(day)))
        val done = s.lead.delegations.single()
        assertTrue(DelegationStatus.closed(done.status))
        assertTrue(done.settled)
        assertTrue(s.ops.contracts.none { it.id == c.id })
        assertEquals(1, s.ops.history.count { it.id == c.id })
        assertEquals(1, s.reports.count { it.missionId == c.id })
        val again = Delegations.daily(d, s.copy(clock = GameClock(back + 10)))
        assertEquals(s.ops.history, again.ops.history)
        assertEquals(s.lead.delegations, again.lead.delegations)
        assertEquals(s.reports, again.reports)
    }

    @Test fun abortingDelegationReturnsTeamAndUpsetsClient() {
        val (s0, c, team) = delegationSetup()
        val prepared = Delegations.prepare(d, s0, c.id, team, null).state
        val dropped = Delegations.abort(d, prepared, "D0")
        assertTrue(dropped.state.lead.delegations.isEmpty())
        assertTrue(c in Delegations.delegable(d, dropped.state))
        val running = Delegations.start(d, prepared, "D0").state
        val a = Delegations.abort(d, running, "D0")
        assertTrue(a.ok)
        assertEquals(DelegationStatus.ABORTED, a.state.lead.delegations.single().status)
        assertEquals(running.lead.cohesion - 3, a.state.lead.cohesion)
        team.forEach { assertNotEquals(LifeStatus.MISSION, a.state.living.lives[it]?.status) }
        assertEquals(1, a.state.ops.history.count { it.id == c.id && it.status == ContractStatus.ABORTED })
        assertFalse(Delegations.abort(d, a.state, "D0").ok)
    }

    @Test fun delegatedPeopleCannotBeDoubleBooked() {
        val (s0, c, team) = delegationSetup()
        val s = Delegations.start(d, Delegations.prepare(d, s0, c.id, team, null).state, "D0").state
        val (s2, c2) = withContract(s, seed = 6L)
        assertNotNull(Delegations.validate(d, s2, c2, listOf(team.first()), null))
        assertFalse(Command.assign(d, s2, team.first(), "roster").ok)
        assertNotNull(Personnel.assignmentOf(d, s2, team.first()))
        val late = Ops.expire(d, s2, c.expiresDay + 30)
        assertEquals("delegierte Aufträge verfallen nicht", ContractStatus.DELEGATED, Ops.contract(late, c.id)?.status)
    }

    // --- Finanzen ------------------------------------------------------------------------------

    @Test fun financeRunsOncePerWeekAndKeepsPersonalCreditsSeparate() {
        val s = leader(12).copy(clock = GameClock(day = 805))
        val r1 = Directorate.weekly(d, s)
        assertEquals(1, r1.state.lead.reports.count { it.kind == "week" })
        assertEquals(s.player.credits, r1.state.player.credits)
        val r2 = Directorate.weekly(d, r1.state)
        assertEquals("zweiter Aufruf in derselben Woche ändert nichts", r1.state, r2.state)
        val (income, costs) = Directorate.weeklyLines(d, s)
        assertEquals(s.director!!.budget + income.sumOf { it.amount } - costs.sumOf { it.amount }, r1.state.director!!.budget)
    }

    @Test fun timeSkipProducesWeeklyAndMonthlyReports() {
        val s = Simulation.run(d, leader(10), 35, stopOnEvents = false).state
        val weeks = s.lead.reports.filter { it.kind == "week" }
        assertTrue("Wochenberichte: ${weeks.size}", weeks.size in 4..6)
        assertEquals(weeks.size, weeks.map { it.title }.toSet().size)
        assertTrue(s.lead.reports.any { it.kind == "month" })
        assertTrue(Directorate.budget(s) >= 0)
    }

    @Test fun projectsProgressOnlyWhenPaid() {
        var s = Directorate.chooseSection(d, leader(10), "training").state
        s = Directorate.startProject(d, s, "sim_course").state
        assertEquals(1, Directorate.activeProjects(s).size)
        assertFalse("nur eigene Abteilung", Directorate.startProject(d, s, "trauma_unit").ok)
        s = Directorate.startProject(d, s, "mentor_program").state
        assertFalse("höchstens 2 Projekte als Sektionsleitung", Directorate.startProject(d, s, "perimeter").ok)
        val broke = s.copy(director = s.director!!.copy(budget = 0), clock = GameClock(805),
            lead = s.lead.copy(hqModules = mapOf("training_hall" to 3, "analysis_room" to 3, "med_ward" to 3)))
        val paused = Directorate.weekly(d, broke).state
        assertTrue(paused.lead.projects.all { it.weeksDone == 0 })
        assertTrue(paused.director!!.morale < broke.director!!.morale + 2)
        var rich = s.copy(director = s.director!!.copy(budget = 100000))
        repeat(4) { w -> rich = Directorate.weekly(d, rich.copy(clock = GameClock(805 + w * 7))).state }
        assertTrue("proj_sim_course" in rich.flags)
        assertTrue("proj_mentor_program" in rich.flags)
        assertTrue(Directorate.activeProjects(rich).isEmpty())
    }

    @Test fun budgetRequestHasCooldownAndIsDecidedWeekly() {
        var s = leader(10)
        assertFalse(Directorate.requestBudget(d, s, 500).ok)
        s = Directorate.requestBudget(d, s, 5000).state
        assertFalse(Directorate.requestBudget(d, s, 5000).ok)
        val after = Directorate.weekly(d, s.copy(clock = GameClock(805))).state
        assertEquals(0, after.lead.budgetRequest)
        assertTrue(after.lead.reports.filter { it.kind == "week" }.last().lines.any { it.startsWith("Haushaltsantrag") })
        assertFalse("28 Tage Abstand", Directorate.requestBudget(d, after, 5000).ok)
        assertFalse(Directorate.requestBudget(d, leader(12), 5000).ok)
    }

    // --- Dilemmata und Spätfolgen ---------------------------------------------------------------

    @Test fun dilemmasAreHandwrittenAndConsequential() {
        assertTrue(Directorate.dilemmas.size >= 6)
        assertEquals(Directorate.dilemmas.size, Directorate.dilemmas.map { it.id }.toSet().size)
        assertTrue(Directorate.dilemmas.all { it.choices.size >= 2 && it.text.length > 40 })
        var s = leader(12).let { it.copy(lead = it.lead.copy(pendingDilemma = "leak", dilemmaDay = it.clock.day)) }
        val r = Directorate.resolveDilemma(d, s, "ignore")
        assertTrue(r.ok)
        s = r.state
        assertNull(s.lead.pendingDilemma)
        assertEquals(1, s.lead.dilemmaHistory.size)
        assertEquals(1, s.lead.fallout.size)
        val due = s.lead.fallout.single().day
        val (before, _) = Directorate.dailyFallout(d, s.copy(clock = GameClock(due - 1)))
        assertEquals(1, before.lead.fallout.size)
        val (hit, msgs) = Directorate.dailyFallout(d, s.copy(clock = GameClock(due)))
        assertTrue(hit.lead.fallout.isEmpty())
        assertEquals(1, msgs.size)
        assertTrue(hit.director!!.oversight < s.director!!.oversight)
        assertEquals("genau einmal", hit, Directorate.dailyFallout(d, hit).first)
        assertFalse(Directorate.resolveDilemma(d, hit, "ignore").ok)
    }

    @Test fun strongSecurityPreventsExposedCoverUp() {
        var s = leader(12)
        s = s.copy(director = s.director!!.copy(departments = s.director!!.departments + ("security" to 3)),
            lead = s.lead.copy(pendingDilemma = "failed_mission"))
        s = Directorate.resolveDilemma(d, s, "cover").state
        val due = s.lead.fallout.single().day
        val (after, msgs) = Directorate.dailyFallout(d, s.copy(clock = GameClock(due)))
        assertEquals(s.director!!.oversight, after.director!!.oversight)
        assertTrue(msgs.single().contains("hält"))
    }

    // --- Standorte ----------------------------------------------------------------------------

    @Test fun outpostsNeedStaffAndCanBeClosedAndReopened() {
        var s = Directorate.earn(leader(9), 30000)
        assertFalse("Stützpunkt erst ab Rang 10", Sites.build(d, s, "base").ok)
        assertTrue(Sites.outposts.size >= 3)
        s = Sites.build(d, s, "safehouse").state
        assertEquals(0, Sites.siteLevel(d, s, "safehouse"))
        val ids = adults(s).take(2)
        s = ready(s, ids).copy(team = ids)
        val a = Sites.assignStaff(d, s, "safehouse", ids[0])
        assertTrue(a.messages.joinToString(), a.ok)
        s = a.state
        assertEquals(1, Sites.siteLevel(d, s, "safehouse"))
        assertFalse(ids[0] in s.team)
        s = Sites.build(d, s, "analysis").state
        assertFalse("niemand hat zwei Posten", Sites.assignStaff(d, s, "analysis", ids[0]).ok)
        val budget = Directorate.budget(s)
        s = Sites.close(d, s, "safehouse").state
        assertEquals(0, Sites.siteLevel(d, s, "safehouse"))
        assertNull(Personnel.assignmentOf(d, s, ids[0]))
        s = Sites.build(d, s, "safehouse").state
        assertEquals(budget - Sites.outpostById.getValue("safehouse").buildCost / 4, Directorate.budget(s))
    }

    @Test fun hqModulesRespectRankAndChangeUpkeep() {
        var s = Directorate.earn(leader(8), 20000)
        s = Sites.upgradeModule(d, s, "training_hall").state
        assertEquals(1, Sites.moduleLevel(s, "training_hall"))
        assertFalse("Stufe 2 ab Rang 10", Sites.upgradeModule(d, s, "training_hall").ok)
        assertFalse("Technik ab Rang 9", Sites.upgradeModule(d, s, "tech_workshop").ok)
        val costs = Directorate.weeklyLines(d, s).second
        assertTrue(costs.any { it.label.contains(Sites.moduleById.getValue("training_hall").name) })
        assertEquals(13, d.hq.rooms.size)
    }

    // --- Privatleben --------------------------------------------------------------------------

    @Test fun v07ApartmentAndCoverJobMigrate() {
        val json = """{"schemaVersion":4,"phase":"game","clock":{"day":800},"player":{"firstName":"Alt","codename":"Null","apartment":true,"coverJob":"Fahrradkurier"}}"""
        val s = SaveMigration.decode(json)
        assertEquals(CURRENT_SCHEMA, s.schemaVersion)
        assertEquals("small", s.life.housing)
        assertEquals("courier", s.life.coverJob)
        assertTrue(s.player.apartment)
        assertEquals(800 / 7, s.life.lastRentWeek)
        val fresh = SaveMigration.decode("""{"schemaVersion":4,"phase":"game","clock":{"day":800}}""")
        assertEquals("quarters", fresh.life.housing)
        assertNull(fresh.life.coverJob)
    }

    @Test fun rentIsChargedOnceAndUnpaidRentEvicts() {
        var s = Home.move(d, leader(5), "small").state
        assertTrue(s.player.apartment)
        val week = Clock.week(s.clock.day)
        val next = s.copy(clock = GameClock((week + 1) * 7))
        val (paid, _) = Home.weekly(d, next)
        assertEquals(next.player.credits - 150, paid.player.credits)
        assertEquals(paid.player.credits, Home.weekly(d, paid).first.player.credits)
        var poor = paid.copy(player = paid.player.copy(credits = 0))
        poor = Home.weekly(d, poor.copy(clock = GameClock((week + 2) * 7))).first
        assertEquals(1, poor.life.missedRent)
        poor = Home.weekly(d, poor.copy(clock = GameClock((week + 3) * 7))).first
        assertEquals("quarters", poor.life.housing)
        assertFalse(poor.player.apartment)
        assertTrue(poor.life.furniture.isEmpty())
    }

    @Test fun housingTiersAndFurnitureHaveEffects() {
        assertNotNull("ab 18", Home.canMove(d, start(), "small"))
        assertNotNull("ab Rang 8", Home.canMove(d, leader(5), "upscale"))
        var s = leader(8).let { it.copy(player = it.player.copy(credits = 10000)) }
        assertFalse("im Quartier keine Möbel", Home.buyFurniture(d, s, "bed").ok)
        s = Home.move(d, s, "small").state
        val base = Home.sleepEnergyBonus(s)
        s = Home.buyFurniture(d, s, "bed").state
        assertEquals(base + 5, Home.sleepEnergyBonus(s))
        s = Home.buyFurniture(d, s, "music").state
        s = Home.buyFurniture(d, s, "plants").state
        assertFalse("3 Plätze", Home.buyFurniture(d, s, "desk").ok)
        val big = Home.move(d, s, "upscale").state
        assertEquals(s.life.furniture, big.life.furniture)
        assertTrue(Home.sleepEnergyBonus(big) > Home.sleepEnergyBonus(s))
        assertEquals(big.player.credits + Home.furnitureById.getValue("bed").cost / 3, Home.sellFurniture(big, "bed").state.player.credits)
    }

    @Test fun leisureIsCappedPerDayAndCostsTime() {
        var s = leader(5).let { it.copy(clock = GameClock(800, 1), player = it.player.copy(energy = 100, credits = 500)) }
        val r = Home.doActivity(d, s, "library")
        assertTrue(r.ok)
        s = r.state.let { it.copy(clock = GameClock(800, 1), life = it.life.copy(pendingEvent = null)) }
        assertNotNull("einmal pro Tag", Home.canDo(d, s, "library"))
        s = Home.doActivity(d, s, "museum").state.let { it.copy(clock = GameClock(800, 1), life = it.life.copy(pendingEvent = null)) }
        s = Home.doActivity(d, s, "cafe").state.let { it.copy(clock = GameClock(800, 1), life = it.life.copy(pendingEvent = null)) }
        assertEquals("Für heute genug Freizeit – der Dienst ruft", Home.canDo(d, s, "photo"))
        assertNotNull("Uhrzeit zählt", Home.canDo(d, s.copy(clock = GameClock(801, 5)), "museum"))
        assertNull(Home.canDo(d, s.copy(clock = GameClock(801, 1)), "photo"))
        assertEquals(2, s.life.hobbies.values.sum())
    }

    @Test fun meetingsAreFriendshipOnlyAndRespectRomanceRules() {
        var s = leader(5).let { it.copy(clock = GameClock(800, 2)) }
        s = ready(s, listOf("alex", "michael"))
        s = Relations.update(d, s, "alex") { it.copy(friendship = 60, romanceStage = 0) }
        val list = Home.meetingsFor(d, s)
        assertTrue(list.size >= 5)
        val r = Home.meet(d, s, "alex_photo")
        assertTrue(r.messages.joinToString(), r.ok)
        val rel = Relations.get(d, r.state, "alex")
        assertTrue(rel.friendship > 60)
        assertEquals("Treffen ändern keine Romanzenstufe", 0, rel.romanceStage)
        assertEquals("Ihr habt euch gerade erst getroffen", Home.canMeet(d, r.state.copy(clock = GameClock(800, 2)), list.first { it.id == "alex_music" }))
        list.firstOrNull { it.id == "nikita_roof" }?.let { assertNotNull("Nikita nur mit Kontakt", Home.canMeet(d, s, it)) }
    }

    @Test fun coverJobPaysFullOnlyWithShift() {
        var s = leader(3).let { it.copy(clock = GameClock(800, 0)) }
        s = Home.takeJob(d, s, "courier").state
        val w = Clock.week(800)
        val worked = Home.weekly(d, s.copy(clock = GameClock((w + 1) * 7))).first
        assertEquals(s.player.credits + 120, worked.player.credits)
        val lazy = Home.weekly(d, worked.copy(clock = GameClock((w + 2) * 7))).first
        assertEquals(worked.player.credits + 60, lazy.player.credits)
        assertEquals(1, lazy.life.jobWarnings)
        val shift = Home.workShift(d, lazy.copy(player = lazy.player.copy(energy = 100))).state
        assertFalse("eine Schicht pro Woche", Home.workShift(d, shift.copy(player = shift.player.copy(energy = 100))).ok)
        val paid = Home.weekly(d, shift.copy(clock = GameClock((w + 3) * 7))).first
        assertEquals(shift.player.credits + 120, paid.player.credits)
        assertNotNull(Home.takeJob(d, start(), "courier").messages.firstOrNull())
        assertFalse("ab 17", Home.takeJob(d, start(), "courier").ok)
    }

    @Test fun lifeEventsAreHandwrittenResolvableAndExpire() {
        assertTrue(Home.events.size >= 12)
        assertEquals(Home.events.size, Home.events.map { it.id }.toSet().size)
        assertTrue(Home.events.all { it.choices.size >= 2 && it.text.length > 30 })
        val e = Home.events.first { it.choices.none { c -> c.needsFurniture != null } }
        var s = leader(5).let { it.copy(life = it.life.copy(pendingEvent = e.id, eventDay = it.clock.day)) }
        val r = Home.resolve(d, s, e.choices.first().id)
        assertTrue(r.ok)
        assertNull(r.state.life.pendingEvent)
        assertTrue(e.id in r.state.life.eventsSeen)
        s = Home.daily(s.copy(clock = GameClock(s.clock.day + 4)))
        assertNull("verfällt nach drei Tagen", s.life.pendingEvent)
    }

    // --- Langzeit, Determinismus, Spielstände -------------------------------------------------------

    private fun invariants(s: GameState) {
        assertTrue(Directorate.budget(s) >= 0)
        assertTrue(s.player.credits >= 0)
        val posts = mutableListOf<String>()
        s.director?.heads?.values?.let { posts += it }
        s.lead.sites.values.filter { it.open }.forEach { posts += it.staff }
        s.lead.delegations.filter { !DelegationStatus.closed(it.status) }.forEach { posts += it.team }
        assertEquals("niemand mit zwei Posten: $posts", posts.size, posts.toSet().size)
        posts.forEach { assertTrue("$it volljährig", Ages.npc(d, s, it) >= 18) }
        assertTrue(s.lead.reports.size <= Directorate.REPORT_CAP)
        assertTrue(s.lead.log.size <= 40)
        assertTrue(s.lead.delegations.filter { it.status == DelegationStatus.RUNNING }.all { !it.settled })
    }

    @Test fun shortAndMediumRegressionsKeepInvariants() {
        listOf(1, 7, 30, 90).forEach { days ->
            val r = Simulation.run(d, leader(10), days, stopOnEvents = false)
            assertTrue("$days Tage", r.ok)
            invariants(r.state)
        }
    }

    @Test fun yearAndDecadeRegressionsStayStable() {
        val year = Simulation.run(d, leader(12), 365, stopOnEvents = false).state
        invariants(year)
        assertTrue(year.lead.reports.any { it.kind == "month" })
        var s = leader(9)
        var guard = 0
        while (s.clock.day < 800 + 3650 && guard++ < 40) {
            s = Simulation.run(d, s, 365, stopOnEvents = false).state
            s = CompanionAssignment.awaitingIntroduction(s)?.let { CompanionAssignment.introduce(d, s, true).state } ?: s
            s = s.copy(activeDialogue = null, lead = s.lead.copy(pendingDilemma = null), life = s.life.copy(pendingEvent = null))
        }
        assertTrue(s.clock.day >= 800 + 3650)
        invariants(s)
    }

    @Test fun leadershipIsDeterministicAndSurvivesSaving() {
        val a = Simulation.run(d, leader(10), 120, stopOnEvents = false).state
        val b = Simulation.run(d, leader(10), 120, stopOnEvents = false).state
        assertEquals(a.lead, b.lead)
        assertEquals(a.life, b.life)
        assertEquals(a, SaveMigration.decode(SaveMigration.encode(a)))
    }

    @Test fun olderSavegamesLoadAndKeepRunning() {
        listOf(
            """{"schemaVersion":2,"phase":"game","clock":{"day":400}}""",
            """{"schemaVersion":3,"phase":"game","clock":{"day":500},"player":{"firstName":"A","codename":"B","rank":8}}""",
            """{"schemaVersion":4,"phase":"game","clock":{"day":900},"player":{"firstName":"A","codename":"B","rank":12,"apartment":true},"director":{"budget":42000}}"""
        ).forEach { json ->
            val s = SaveMigration.decode(json)
            assertEquals(CURRENT_SCHEMA, s.schemaVersion)
            val r = Simulation.run(d, s, 14, stopOnEvents = false)
            assertTrue(json, r.ok)
            invariants(r.state)
        }
        val director = SaveMigration.decode("""{"schemaVersion":4,"phase":"game","clock":{"day":900},"player":{"rank":12},"director":{"budget":42000}}""")
        assertEquals(42000, director.director!!.budget)
    }

    @Test fun richV07SaveKeepsTeamOpsInventoryAndApartment() {
        var s = leader(9)
        s = Ops.refresh(d, s).copy(team = listOf("alex", "birkhoff"))
        s = s.copy(player = s.player.copy(apartment = true, coverJob = "Fahrradkurier",
            inventory = d.items.mapIndexed { i, it -> ItemStack(i + 1L, it.id) }))
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(SaveMigration.encode(s)).jsonObject
        val v07 = kotlinx.serialization.json.JsonObject(obj.filterKeys { it != "lead" && it != "life" } +
            ("schemaVersion" to kotlinx.serialization.json.JsonPrimitive(4)))
        val loaded = SaveMigration.decode(v07)
        assertEquals(s.team, loaded.team)
        assertEquals(s.ops, loaded.ops)
        assertEquals(s.living, loaded.living)
        assertEquals(s.relations, loaded.relations)
        assertEquals(102, loaded.player.inventory.size)
        assertEquals("small", loaded.life.housing)
        assertEquals("courier", loaded.life.coverJob)
        assertEquals(LeadershipState(), loaded.lead)
        val next = Simulation.run(d, loaded, 14, stopOnEvents = false).state
        assertEquals("Miete einmal pro Woche", 2, next.life.lastRentWeek - loaded.life.lastRentWeek)
        invariants(next)
    }

    @Test fun reloadMidProjectAndDelegationDoesNotDoubleCount() {
        val (s0, c, team) = delegationSetup()
        var s = Directorate.ensure(d, s0.copy(player = s0.player.copy(rank = 10)))
        s = Directorate.chooseSection(d, s, "training").state
        s = Directorate.startProject(d, s, "sim_course").state
        s = Delegations.start(d, Delegations.prepare(d, s, c.id, team, null).state, "D0").state
        s = Directorate.weekly(d, s.copy(clock = GameClock(805))).state
        val reloaded = SaveMigration.decode(SaveMigration.encode(s))
        assertEquals(s, reloaded)
        assertEquals("gleiche Woche nach dem Laden", reloaded, Directorate.weekly(d, reloaded).state)
        assertEquals(1, reloaded.lead.projects.single().weeksDone)
        val back = reloaded.lead.delegations.single().returnDay
        var t = reloaded
        for (day in back..back + 3) t = Delegations.daily(d, SaveMigration.decode(SaveMigration.encode(t.copy(clock = GameClock(day)))))
        assertEquals(1, t.ops.history.count { it.id == c.id })
        assertEquals(1, t.reports.count { it.missionId == c.id })
    }

    @Test fun leadershipQualityChangesMissionChecks() {
        val base = leader(8)
        val team = adults(base).take(3)
        var s = ready(base, team).copy(team = team)
        assertEquals(0, Command.missionBonus(d, s.copy(player = s.player.copy(rank = 7)), team))
        val bad = s.copy(lead = s.lead.copy(cohesion = 0))
        assertTrue("schlechte Führung erschwert", Command.missionBonus(d, bad, team) < 0)
        assertTrue("aber nicht unmöglich", Command.missionBonus(d, bad, team) >= -8)
        team.forEachIndexed { i, id -> s = Command.setRole(d, s, id, Command.roles[i].id).state }
        val good = s.copy(lead = s.lead.copy(cohesion = 90))
        assertTrue(Command.missionBonus(d, good, team) > Command.missionBonus(d, bad, team))
        assertTrue(Command.missionBonus(d, good, team) <= 12)
    }

    @Test fun teenRomanceRulesRemainUnchanged() {
        val adult = start().copy(clock = GameClock(day = 2 * 365))
        val s = Social.createRecruit(d, adult, "Mira", "K", "female", AvatarSpec(), emptyList(), "", "tech", emptyList(), emptyMap()).state
        val id = s.customNpcs.single().id
        assertEquals(16, Ages.npc(d, s, id))
        assertTrue(Romance.allowed(d, s, id))
        assertEquals(2, Romance.maxStage(d, s, id))
        val withHome = Home.move(d, s.copy(player = s.player.copy(credits = 1000)), "small").state
        val close = Relations.update(d, withHome, id) { it.copy(romanceStage = 2, romanceSinceDay = 0, affection = 100, trust = 100) }
        assertNull("auch mit eigener Wohnung keine Erwachsenenfunktion", Romance.nextStep(d, close, id))
        assertEquals(2, Romance.maxStage(d, start(), "maya"))
        assertTrue(Home.meetings.none { it.description.contains("Kuss", true) || it.description.contains("Date", true) })
    }
}

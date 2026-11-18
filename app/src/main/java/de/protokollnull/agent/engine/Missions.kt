package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

object Missions {
    const val END_SUCCESS = "END_SUCCESS"
    const val END_FAIL = "END_FAIL"
    const val END_ABORT = "END_ABORT"

    data class Approach(val id: String, val name: String, val description: String, val attributes: Set<String>)

    val approaches = listOf(
        Approach("balanced", "Ausgewogen", "Keine Boni, keine Nachteile.", emptySet()),
        Approach("stealth", "Leise", "+10 auf Wahrnehmung/Intellekt-Proben, Alarm wiegt schwerer.", setOf(Attr.PERCEPTION, Attr.INTELLECT)),
        Approach("social", "Sozial", "+10 auf Ausstrahlung/Willenskraft-Proben.", setOf(Attr.CHARISMA, Attr.WILLPOWER)),
        Approach("direct", "Direkt", "+10 auf Fitness/Präzision, startet mit Alarm 1.", setOf(Attr.FITNESS, Attr.PRECISION))
    )

    fun definition(data: GameData, state: GameState, id: String): MissionDefinition? =
        data.missionById[id] ?: state.board.firstOrNull { it.id == id } ?: state.activeMission?.generated?.takeIf { it.id == id }

    fun lockReason(data: GameData, state: GameState, m: MissionDefinition): String? {
        if (m.id in state.completedMissions && !m.repeatable) return "Abgeschlossen"
        if (Ages.player(state) < m.minAge) return "Ab ${m.minAge} Jahren"
        if (state.player.rank < m.minRank) return "Ab Rang ${m.minRank} (${data.rank(m.minRank).name})"
        if (!Conds.met(m.requires, data, state)) return "Voraussetzung: " + Conds.describe(m.requires, data).ifEmpty { listOf("Story-Fortschritt") }.joinToString()
        if (m.stages.isEmpty()) return "In Entwicklung"
        return null
    }

    fun storyMissions(data: GameData, state: GameState) = data.missions

    fun teamCandidates(data: GameData, state: GameState, m: MissionDefinition): List<String> {
        val ids = data.agents.filter { it.factionId == "division" && it.id !in setOf("percy", "amanda") }.map { it.id } + state.customNpcs.map { it.id }
        return ids.filter { id ->
            val age = Ages.npc(data, state, id)
            val trust = Relations.get(data, state, id).trust
            trust >= 30 && (m.threat < 3 || age >= 18)
        }
    }

    fun start(data: GameData, state: GameState, missionId: String, approach: String, team: List<String>): Result {
        if (state.activeMission != null) return state.fail("Ein Einsatz läuft bereits.")
        val m = definition(data, state, missionId) ?: return state.fail("Mission unbekannt")
        lockReason(data, state, m)?.let { return state.fail(it) }
        if (state.player.energy < 25) return state.fail("Mindestens 25 Energie nötig. Ausruhen oder schlafen.")
        if (state.player.health < 40) return state.fail("Gesundheit zu niedrig – erst zur Krankenstation.")
        val maxTeam = minOf(m.maxTeam, Progression.teamSize(data, state))
        val validTeam = team.distinct().filter { it in teamCandidates(data, state, m) }.take(maxTeam)
        val seed = Rng.mix(state.seed, m.id.hashCode().toLong(), state.clock.day.toLong(), state.rolls.toLong())
        val first = m.stages.first()
        var run = MissionRun(
            missionId = m.id, seed = seed, stageId = first.id,
            generated = if (data.missionById.containsKey(m.id)) null else m,
            approach = approach, team = validTeam,
            alarm = (if (approach == "direct") 1 else 0) + m.witnesses / 2,
            intel = (state.director?.departments?.get("intel") ?: 1) - 1,
            log = listOf("Einsatz begonnen: ${m.title}")
        )
        run = enterStage(data, state, m, run, first)
        return Result(state.copy(activeMission = run, rolls = state.rolls + 1), listOf("Briefing abgeschlossen – Einsatz läuft."))
    }

    fun currentMission(data: GameData, state: GameState): MissionDefinition? {
        val run = state.activeMission ?: return null
        return run.generated ?: data.missionById[run.missionId]
    }

    fun currentStage(data: GameData, state: GameState): StageDefinition? {
        val run = state.activeMission ?: return null
        return currentMission(data, state)?.stages?.firstOrNull { it.id == run.stageId }
    }

    private fun enterStage(data: GameData, state: GameState, m: MissionDefinition, run0: MissionRun, st: StageDefinition): MissionRun {
        val rng = Rng(Rng.mix(run0.seed, st.id.hashCode().toLong()))
        var run = run0.copy(stageId = st.id, search = null, observe = null, tactical = null)
        when (st.type) {
            "search" -> {
                val n = st.spots.size
                val clueCount = (st.cluesNeeded + 1).coerceAtMost(n)
                val clues = rng.shuffled((0 until n).toList()).take(clueCount).toSet()
                run = run.copy(search = SearchState(st.spots, clues, actionsLeft = st.actions + bonusActions(data, state)))
            }
            "observe" -> {
                val correct = (0 until st.rounds).map { rng.nextInt(st.targets.size.coerceAtLeast(1)) }
                run = run.copy(observe = ObserveState(rounds = st.rounds, targets = st.targets, correct = correct))
            }
            "tactical" -> {
                val setup = st.tactical ?: TacticalSetup(listOf("#.E.E.#", "#.....#", "#.P.P.#"))
                run = run.copy(tactical = Tactical.setup(data, state, setup, run.team, run.seed + st.id.hashCode(), m.threat))
            }
        }
        return run
    }

    private fun bonusActions(data: GameData, state: GameState): Int {
        val gear = Stats.equipped(data, state.player).map { it.id }
        return (if ("flashlight" in gear) 1 else 0) + (if ("laptop" in gear || "smartphone" in gear) 1 else 0) +
            (if (state.activeMission?.approach == "stealth") 1 else 0)
    }

    /** Gesamtbonus für Proben im Einsatz (Ansatz, Team, Ausrüstung, Intel). */
    fun checkBonus(data: GameData, state: GameState, check: CheckDef): Int {
        val run = state.activeMission ?: return 0
        val m = currentMission(data, state)
        val approach = approaches.firstOrNull { it.id == run.approach }
        var bonus = if (approach != null && check.attribute in approach.attributes) 10 else 0
        bonus += run.team.size * 3
        bonus += run.intel.coerceAtMost(5) * 3
        bonus -= run.alarm * 3
        if (m != null) {
            val carried = state.player.inventory.map { it.itemId }.toSet()
            bonus += (m.recommendedItems.count { it in carried } * 5).coerceAtMost(15)
        }
        return bonus
    }

    fun optionChance(data: GameData, state: GameState, option: StageOption): Int? =
        option.check?.let { Stats.checkChance(data, state, it, checkBonus(data, state, it)) }

    fun choose(data: GameData, state: GameState, optionId: String): Result {
        val run = state.activeMission ?: return state.fail("Kein Einsatz aktiv")
        val m = currentMission(data, state) ?: return state.fail("Mission fehlt")
        val st = currentStage(data, state) ?: return state.fail("Stage fehlt")
        val opt = st.options.firstOrNull { it.id == optionId } ?: return state.fail("Option fehlt")
        if (!Conds.met(opt.requires, data, state)) return state.fail("Voraussetzungen fehlen")
        var s = state
        var outcome = opt.success
        var rollText = ""
        if (opt.check != null) {
            val (s1, roll) = Checks.roll(data, s, opt.check, checkBonus(data, s, opt.check), salt = run.seed + st.id.hashCode())
            s = s1
            if (!roll.success) outcome = opt.failure ?: Outcome("Es gelingt nicht.", alarm = 1)
            rollText = if (roll.success) " ✓ (${roll.roll}/${roll.chance})" else " ✗ (${roll.roll}/${roll.chance})"
        }
        return applyOutcome(data, s, m, st, outcome, opt.text + rollText)
    }

    private fun applyOutcome(data: GameData, state: GameState, m: MissionDefinition, st: StageDefinition, o: Outcome, label: String): Result {
        val run = state.activeMission!!
        val alarmMult = if (run.approach == "stealth" && o.alarm > 0) 2 else 1
        val securityBonus = if ((state.director?.departments?.get("security") ?: 1) >= 3 && o.alarm > 0) 1 else 0
        var r = run.copy(
            intel = (run.intel + o.intel).coerceAtLeast(0),
            alarm = (run.alarm + o.alarm * alarmMult - securityBonus).coerceAtLeast(0),
            cover = (run.cover + o.cover).coerceAtMost(5),
            stressTaken = run.stressTaken + o.stress,
            damageTaken = run.damageTaken - o.health.coerceAtMost(0),
            log = run.log + ("▸ $label" + if (o.text.isNotBlank()) " – ${o.text}" else ""),
            lastText = o.text,
            pendingEffects = if (o.effects != null) run.pendingEffects + o.effects else run.pendingEffects
        )
        var s = state.copy(activeMission = r)
        // Abbruchbedingungen: Tarnung aufgeflogen / Alarmstufe
        val next = when {
            r.cover <= 0 -> { r = r.copy(log = r.log + "Deine Tarnung ist aufgeflogen!"); END_FAIL }
            r.alarm >= 6 -> { r = r.copy(log = r.log + "Alarmstufe Rot – Rückzug befohlen!"); END_FAIL }
            else -> o.next ?: st.next ?: nextStageId(m, st)
        }
        s = s.copy(activeMission = r)
        return goTo(data, s, m, next)
    }

    private fun nextStageId(m: MissionDefinition, st: StageDefinition): String {
        val idx = m.stages.indexOfFirst { it.id == st.id }
        return m.stages.getOrNull(idx + 1)?.id ?: END_SUCCESS
    }

    private fun goTo(data: GameData, state: GameState, m: MissionDefinition, next: String): Result {
        val run = state.activeMission!!
        if (next == END_SUCCESS || next == END_FAIL || next == END_ABORT) {
            val res = when (next) { END_SUCCESS -> "success"; END_FAIL -> "fail"; else -> "abort" }
            return Result(state.copy(activeMission = run.copy(result = res)))
        }
        val st = m.stages.firstOrNull { it.id == next } ?: return Result(state.copy(activeMission = run.copy(result = "success")))
        return Result(state.copy(activeMission = enterStage(data, state, m, run, st)))
    }

    fun continueNarrative(data: GameData, state: GameState): Result {
        val m = currentMission(data, state) ?: return Result(state)
        val st = currentStage(data, state) ?: return Result(state)
        return goTo(data, state, m, st.next ?: nextStageId(m, st))
    }

    // --- Untersuchung -----------------------------------------------------------

    fun search(data: GameData, state: GameState, index: Int): Result {
        val run = state.activeMission ?: return Result(state)
        val m = currentMission(data, state) ?: return Result(state)
        val st = currentStage(data, state) ?: return Result(state)
        val se = run.search ?: return Result(state)
        if (index in se.searched || se.actionsLeft <= 0) return Result(state)
        val hit = index in se.clues
        var s = state
        var hints = se.hints
        if (!hit) {
            // Wahrnehmungsprobe: Hinweis auf Nachbarfelder (Anzahl versteckter Spuren in der Nähe)
            val (s1, roll) = Checks.roll(data, s, CheckDef(Attr.PERCEPTION, "observe", 45), checkBonus(data, s, CheckDef(Attr.PERCEPTION)), salt = index.toLong())
            s = s1
            if (roll.success) {
                val near = neighbors(index, se.spots.size).count { it in se.clues && it !in se.searched }
                hints = hints + (index to near)
            }
        }
        val ns = se.copy(searched = se.searched + index, found = se.found + if (hit) 1 else 0, actionsLeft = se.actionsLeft - 1, hints = hints)
        val line = if (hit) "Hinweis gefunden: ${se.spots[index]}" else "${se.spots[index]}: nichts" + (hints[index]?.let { " (Spuren in der Nähe: $it)" } ?: "")
        var r = run.copy(search = ns, log = run.log + line)
        s = s.copy(activeMission = r)
        if (ns.found >= st.cluesNeeded) {
            r = r.copy(intel = r.intel + ns.found, log = r.log + (st.successText.ifBlank { "Genug Hinweise gesichert." }))
            s = s.copy(activeMission = r)
            return goTo(data, s, m, st.next ?: nextStageId(m, st))
        }
        if (ns.actionsLeft <= 0) {
            r = r.copy(intel = r.intel + ns.found, alarm = r.alarm + 2, log = r.log + (st.failText.ifBlank { "Die Zeit ist um – unvollständige Spurenlage, Alarm +2." }))
            s = s.copy(activeMission = r)
            return goTo(data, s, m, st.next ?: nextStageId(m, st))
        }
        return Result(s)
    }

    fun neighbors(i: Int, n: Int): List<Int> {
        val cols = 3
        val x = i % cols; val y = i / cols
        return listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
            .filter { (a, b) -> a in 0 until cols && b >= 0 }
            .map { (a, b) -> b * cols + a }.filter { it < n }
    }

    // --- Beobachtung -----------------------------------------------------------

    fun observeHint(data: GameData, state: GameState): Result {
        val run = state.activeMission ?: return Result(state)
        val ob = run.observe ?: return Result(state)
        if (ob.hintRevealed) return Result(state)
        val (s1, roll) = Checks.roll(data, state, CheckDef(Attr.PERCEPTION, "observe", 45), checkBonus(data, state, CheckDef(Attr.PERCEPTION)), salt = ob.round.toLong())
        val gear = Stats.equipped(data, state.player).map { it.id }
        val ok = roll.success || "binoculars" in gear && roll.roll <= roll.chance + 15
        val nr = if (ok) run.copy(observe = ob.copy(hintRevealed = true), log = run.log + "Dir fällt etwas auf: ${ob.targets[ob.correct[ob.round]]} verhält sich ungewöhnlich.")
        else run.copy(observe = ob.copy(hintRevealed = true), alarm = run.alarm + 0, log = run.log + "Zu viel Bewegung – du erkennst kein Muster. (${roll.roll}/${roll.chance})")
        return Result(s1.copy(activeMission = nr))
    }

    fun observe(data: GameData, state: GameState, target: Int): Result {
        val run = state.activeMission ?: return Result(state)
        val m = currentMission(data, state) ?: return Result(state)
        val st = currentStage(data, state) ?: return Result(state)
        val ob = run.observe ?: return Result(state)
        val correct = ob.correct[ob.round] == target
        val nob = ob.copy(round = ob.round + 1, hintRevealed = false, hits = ob.hits + if (correct) 1 else 0)
        var r = run.copy(
            observe = nob,
            alarm = run.alarm + if (correct) 0 else (if ((ob.round + target) % 2 == 0) 1 else 0),
            log = run.log + if (correct) "Treffer: ${ob.targets[target]} ist Teil der Übergabe." else "${ob.targets[target]} war eine falsche Spur."
        )
        var s = state.copy(activeMission = r)
        if (nob.round >= nob.rounds) {
            r = if (nob.hits >= st.intelNeeded) r.copy(intel = r.intel + nob.hits, log = r.log + st.successText.ifBlank { "Observation erfolgreich." })
            else r.copy(intel = r.intel + nob.hits, alarm = r.alarm + 1, log = r.log + st.failText.ifBlank { "Lückenhafte Observation." })
            s = s.copy(activeMission = r)
            val next = if (nob.hits >= st.intelNeeded) (st.next ?: nextStageId(m, st)) else (st.next ?: nextStageId(m, st))
            return goTo(data, s, m, next)
        }
        return Result(s)
    }

    // --- Taktik ------------------------------------------------------------------

    fun updateTactical(data: GameData, state: GameState, f: (TacticalState) -> TacticalState): Result {
        val run = state.activeMission ?: return Result(state)
        val t = run.tactical ?: return Result(state)
        val nt = f(t)
        var s = state.copy(activeMission = run.copy(tactical = nt))
        if (nt.finished) {
            val m = currentMission(data, s) ?: return Result(s)
            val st = currentStage(data, s) ?: return Result(s)
            val player = nt.units.first { it.isPlayer }
            val dmg = (player.maxHp - player.hp).coerceAtLeast(0) * 5
            val r = s.activeMission!!
            s = s.copy(activeMission = r.copy(damageTaken = r.damageTaken + dmg, stressTaken = r.stressTaken + 8, log = r.log + nt.log.takeLast(1)))
            return if (nt.won) goTo(data, s, m, st.next ?: nextStageId(m, st)) else goTo(data, s, m, END_FAIL)
        }
        return Result(s)
    }

    fun abort(data: GameData, state: GameState): Result {
        val run = state.activeMission ?: return Result(state)
        return Result(state.copy(activeMission = run.copy(result = "abort", log = run.log + "Einsatz abgebrochen.")))
    }

    /** Nachbesprechung: Belohnungen und Konsequenzen anwenden. */
    fun finish(data: GameData, state: GameState): Result {
        val run = state.activeMission ?: return Result(state)
        val m = currentMission(data, state) ?: return Result(state.copy(activeMission = null))
        val result = run.result ?: return state.fail("Einsatz läuft noch")
        var s = state.copy(activeMission = null)
        val notes = mutableListOf<String>()
        val alarmPenalty = run.alarm >= 4
        var xp = 0; var credits = 0; var rep = 0
        when (result) {
            "success" -> {
                xp = m.xp + run.intel * 10
                credits = m.credits
                rep = m.reputation + (if (alarmPenalty) -2 else 2)
                s = s.copy(completedMissions = s.completedMissions + m.id, board = s.board.filter { it.id != m.id })
                notes += m.debriefSuccess.ifBlank { "Auftrag erfüllt." }
                val e = Effs.apply(m.successEffects, data, s, m.giverId)
                s = e.state; notes += e.messages
                m.rewardItems.forEach { (id, q) -> val r = Inventory.add(data, s, id, q); s = r.state; notes += r.messages }
                run.pendingEffects.forEach { pe -> val r = Effs.apply(pe, data, s, m.giverId); s = r.state; notes += r.messages }
                run.team.forEach { id -> s = Relations.update(data, s, id) { it.copy(loyalty = it.loyalty + 4, trust = it.trust + 2, respect = it.respect + 2, memories = it.memories + "Gemeinsamer Einsatz: ${m.title}") } }
                if (run.team.isNotEmpty()) notes += "Team: Loyalität +4"
            }
            "fail" -> {
                xp = m.xp / 3
                rep = -(m.reputation / 2).coerceAtLeast(1)
                s = s.copy(failedMissions = s.failedMissions + m.id, board = s.board.filter { it.id != m.id })
                notes += m.debriefFail.ifBlank { "Der Einsatz ist gescheitert." }
                val e = Effs.apply(m.failEffects, data, s, m.giverId)
                s = e.state; notes += e.messages
                // Story-Missionen bleiben wiederholbar, damit die Kampagne nicht blockiert
                s = s.copy(player = s.player.copy(wounds = s.player.wounds + 1))
                notes += "Du trägst eine Verletzung davon (Wunden +1)."
                run.team.forEach { id -> s = Relations.update(data, s, id) { it.copy(trust = it.trust - 2) } }
            }
            else -> {
                rep = -1
                notes += "Abbruch gemeldet. Kein Ergebnis, aber kein Totalverlust."
            }
        }
        if (alarmPenalty && result != "abort") {
            notes += "Hohe Alarmstufe: Zeugen und Kameras – Fraktion ${data.factionById[m.factionId]?.name ?: m.factionId} ist alarmiert."
            s = s.copy(factionStanding = s.factionStanding + (m.factionId to ((s.factionStanding[m.factionId] ?: 0) - 5)))
        }
        val p = s.player
        s = s.copy(player = p.copy(
            credits = p.credits + credits,
            reputation = (p.reputation + rep).coerceAtLeast(0),
            stress = (p.stress + 8 + m.threat * 3 + run.stressTaken).pct(),
            energy = (p.energy - 15 - m.threat * 5).pct(),
            health = (p.health - run.damageTaken).coerceIn(5, 100)
        ))
        if (credits > 0) notes += "+$credits Cr"
        if (rep != 0) notes += "Ruf ${Effs.signed(rep)}"
        if (run.damageTaken > 0) notes += "Gesundheit −${run.damageTaken}"
        val xr = Progression.grantXp(data, s, xp)
        s = xr.state; notes += xr.messages
        s = Inventory.wear(s, 3 + m.threat * 2)
        val report = MissionReport(m.id, m.title, when (result) { "success" -> "Erfolg"; "fail" -> "Fehlschlag"; else -> "Abgebrochen" }, s.clock.day, xp, credits, rep, notes)
        s = s.copy(reports = (s.reports + report).takeLast(40))
        val t = World.passTime(data, s, (1 + m.threat).coerceAtMost(4))
        s = Quests.update(data, t.state).state
        return Result(s, notes + t.messages)
    }
}

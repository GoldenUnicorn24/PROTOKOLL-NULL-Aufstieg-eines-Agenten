package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/** Direktorat-Endgame: Budget, Abteilungen, Personal, Aufsicht. */
object Director {
    val departments = listOf(
        "training" to "Ausbildung",
        "tech" to "Technik",
        "medical" to "Medizin",
        "intel" to "Aufklärung",
        "security" to "Sicherheit"
    )
    val policies = listOf(
        "balanced" to "Ausgewogen",
        "transparent" to "Transparenz gegenüber der Aufsicht",
        "secrecy" to "Strikte Geheimhaltung",
        "people" to "Fürsorge für das Personal"
    )

    fun deptName(id: String) = departments.firstOrNull { it.first == id }?.second ?: id
    fun upgradeCost(level: Int) = 6000 * level * level

    fun upgrade(data: GameData, state: GameState, dept: String): Result {
        val d = state.director ?: return state.fail("Nur für die Direktion.")
        val lvl = d.departments[dept] ?: 1
        if (lvl >= 5) return state.fail("${deptName(dept)} ist voll ausgebaut.")
        val cost = upgradeCost(lvl)
        if (d.budget < cost) return state.fail("Budget reicht nicht ($cost Cr).")
        val nd = d.copy(budget = d.budget - cost, departments = d.departments + (dept to lvl + 1), morale = (d.morale + 3).pct())
        return Result(state.copy(director = nd), listOf("${deptName(dept)} auf Stufe ${lvl + 1} ausgebaut (−$cost Cr)."))
    }

    fun setPolicy(state: GameState, policy: String): Result {
        val d = state.director ?: return state.fail("Nur für die Direktion.")
        return Result(state.copy(director = d.copy(policy = policy)), listOf("Neue Leitlinie: " + (policies.firstOrNull { it.first == policy }?.second ?: policy)))
    }

    fun assignHead(data: GameData, state: GameState, dept: String, npcId: String?): Result {
        val d = state.director ?: return state.fail("Nur für die Direktion.")
        if (npcId != null && Ages.npc(data, state, npcId) < 18) return state.fail("Leitungsfunktionen nur für volljähriges Personal.")
        val heads = if (npcId == null) d.heads - dept else d.heads.filterValues { it != npcId } + (dept to npcId)
        var s = state.copy(director = d.copy(heads = heads))
        if (npcId != null) s = Relations.update(data, s, npcId) { it.copy(loyalty = it.loyalty + 8, respect = it.respect + 5) }
        return Result(s, listOf(if (npcId == null) "${deptName(dept)}: Leitung vakant" else "${Relations.displayName(data, s, npcId)} leitet jetzt ${deptName(dept)}."))
    }

    fun weekly(data: GameData, state: GameState): Result {
        val d = state.director ?: return Result(state)
        val levels = d.departments.values.sum()
        val income = 14000 + (d.oversight - 50) * 80
        val upkeep = 1500 * levels
        val headBonus = d.heads.size * 2
        val (moraleDelta, oversightDelta) = when (d.policy) {
            "transparent" -> 0 to 3
            "secrecy" -> -1 to -3
            "people" -> 3 to 0
            else -> 1 to 0
        }
        val nd = d.copy(
            budget = (d.budget + income - upkeep).coerceAtLeast(0),
            morale = (d.morale + moraleDelta + headBonus / 4 - (if (d.budget < 5000) 3 else 0)).pct(),
            oversight = (d.oversight + oversightDelta).pct(),
            lastWeekReport = "Einnahmen ${income} Cr, Unterhalt ${upkeep} Cr, Moral ${d.morale}, Aufsicht ${d.oversight}"
        )
        val msgs = mutableListOf("Direktorat: +$income / −$upkeep Cr")
        if (nd.oversight < 20) msgs += "Die Regierungsaufsicht droht mit Untersuchungsausschuss!"
        return Result(state.copy(director = nd), msgs)
    }

    /** Ab Einsatzkommandant: Generierte Missionen an Teams delegieren. */
    fun delegate(data: GameData, state: GameState, mission: MissionDefinition, team: List<String>): Result {
        if (state.player.rank < 9) return state.fail("Delegieren ab Rang 9 (Einsatzkommandant).")
        if (team.isEmpty()) return state.fail("Kein Team ausgewählt.")
        if (team.any { Ages.npc(data, state, it) < 18 } && mission.threat >= 3) return state.fail("Minderjährige nicht für Gefahrenstufe ≥ 3.")
        val power = team.sumOf { (data.agentById[it]?.combat ?: 45) + Relations.get(data, state, it).loyalty / 4 } / team.size
        val intel = (state.director?.departments?.get("intel") ?: 1) * 4
        val chance = (40 + power / 2 + intel + team.size * 6 - mission.threat * 10).coerceIn(10, 95)
        val rng = Rng(Rng.mix(state.seed, state.rolls.toLong(), mission.id.hashCode().toLong()))
        val roll = rng.percent()
        var s = state.copy(rolls = state.rolls + 1, board = state.board.filter { it.id != mission.id })
        val msgs = mutableListOf<String>()
        if (roll <= chance) {
            val credits = mission.credits / 2
            s = s.copy(player = s.player.copy(credits = s.player.credits + credits, reputation = s.player.reputation + mission.reputation / 2), completedMissions = s.completedMissions + mission.id)
            team.forEach { id -> s = Relations.update(data, s, id) { it.copy(loyalty = it.loyalty + 3, respect = it.respect + 2) } }
            val xp = Progression.grantXp(data, s, mission.xp / 3)
            s = xp.state
            msgs += "Delegiert: „${mission.title}“ erfolgreich ($roll/$chance). +$credits Cr" ; msgs += xp.messages
        } else {
            team.forEach { id -> s = Relations.update(data, s, id) { it.copy(trust = it.trust - 3) } }
            s = s.copy(player = s.player.copy(reputation = (s.player.reputation - 5).coerceAtLeast(0)))
            msgs += "Delegiert: „${mission.title}“ gescheitert ($roll/$chance). Das Team ist verletzt, aber am Leben. Ruf −5"
        }
        s = s.copy(reports = (s.reports + MissionReport(mission.id, mission.title, if (roll <= chance) "Delegiert – Erfolg" else "Delegiert – Fehlschlag", s.clock.day, 0, 0, 0, msgs)).takeLast(40))
        val t = World.passTime(data, s, 1)
        return Result(t.state, msgs + t.messages)
    }
}

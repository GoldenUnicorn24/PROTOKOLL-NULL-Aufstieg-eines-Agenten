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

    // v0.8: Die frühere Delegation über das alte Wochenboard wurde durch [Delegations]
    // (OpsState.contracts) ersetzt – es gibt nur noch eine Missionsquelle.
}

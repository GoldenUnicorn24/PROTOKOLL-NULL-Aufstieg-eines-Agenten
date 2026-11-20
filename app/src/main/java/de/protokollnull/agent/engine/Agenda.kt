package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v1.0: Direktionsagenda – ein langfristiges, messbares Ziel für jeweils 26 Wochen.
 *
 * Damit bietet die Direktion (Rang 10–12, auch nach dem Finale) wiederkehrende strategische
 * Entscheidungen: Die Wahl kostet sofort politisches Kapital, das Ziel wird am Ende der
 * Amtsperiode genau einmal in der Wochenabrechnung bewertet (Belohnung oder Folgen).
 */
object Agenda {
    const val TERM_WEEKS = 26

    data class Goal(val label: String, val current: Int, val target: Int, val atMost: Boolean = false) {
        val met: Boolean get() = if (atMost) current <= target else current >= target
    }

    data class Def(
        val id: String, val name: String, val description: String, val cost: String, val reward: String, val penalty: String,
        val requiresFlag: String? = null
    )

    val all = listOf(
        Def("transparency", "Offene Division", "Rückhalt der Aufsicht gewinnen, ohne die Moral zu opfern.",
            "Sofort: Moral −3 (mehr Berichtspflichten)", "Budget +15 000 Cr, Regierungsaufsicht +10", "Moral −5"),
        Def("security", "Meridian zurückdrängen", "Den Einfluss von Black Meridian in der Stadt nachhaltig senken.",
            "Sofort: Aufsicht −2 (riskantere Operationen)", "Budget +12 000 Cr, Ruf +15, Meridian-Einfluss −5", "Aufsicht −6"),
        Def("people", "Menschen zuerst", "Ein Team, das zusammenhält: hohe Kohäsion und Moral.",
            "Sofort: Budget −3 000 Cr (Betreuung, Freistellungen)", "Moral +6, Kohäsion +6, Stress im Team −15", "Kohäsion −6"),
        Def("expansion", "Ausbau", "HQ-Module ausbauen und Außenstellen eröffnen.",
            "Sofort: Moral −2 (Baustellen, Umzüge)", "Budget +20 000 Cr", "Aufsicht −5 (Fehlplanung)"),
        Def("academy", "Akademie stärken", "Die neue Ausbildung zum Vorbild machen: Ausbildungsabteilung und Moral.",
            "Sofort: Budget −2 000 Cr (Lehrkräfte)", "Ruf +20, Zivilbevölkerung +8, 1 Fähigkeitspunkt", "Moral −4", requiresFlag = "academy_founded")
    )
    val byId = all.associateBy { it.id }

    fun available(state: GameState): List<Def> =
        if (state.director == null || Directorate.tier(state) < 1) emptyList()
        else all.filter { it.requiresFlag == null || it.requiresFlag in state.flags }

    fun active(state: GameState): AgendaState? = state.lead.agenda

    fun weeksLeft(state: GameState): Int {
        val a = state.lead.agenda ?: return 0
        return (a.endWeek - Clock.week(state.clock.day)).coerceAtLeast(0)
    }

    private fun expansionScore(state: GameState) = state.lead.hqModules.values.sum() + state.lead.sites.values.count { it.open }

    fun goals(data: GameData, state: GameState): List<Goal> {
        val a = state.lead.agenda ?: return emptyList()
        val d = state.director ?: return emptyList()
        return when (a.id) {
            "transparency" -> listOf(Goal("Rückhalt der Aufsicht", d.oversight, 70), Goal("Moral des Personals", d.morale, 55))
            "security" -> listOf(Goal("Einfluss Black Meridian", Ops.faction(state, "syndicate").influence, 35, atMost = true))
            "people" -> listOf(Goal("Teamkohäsion", state.lead.cohesion, 70), Goal("Moral des Personals", d.morale, 70))
            "expansion" -> listOf(Goal("Ausbaustufen + Außenstellen", expansionScore(state), a.baseline + 3))
            "academy" -> listOf(Goal("Ausbildungsabteilung (Stufe)", d.departments["training"] ?: 1, 4), Goal("Moral des Personals", d.morale, 60))
            else -> emptyList()
        }
    }

    fun choose(data: GameData, state: GameState, id: String): Result {
        val def = byId[id] ?: return state.fail("Unbekannte Agenda")
        if (state.director == null || Directorate.tier(state) < 1) return state.fail("Eine Agenda setzt die Direktionsebene (ab Rang 10) voraus.")
        if (state.lead.agenda != null) return state.fail("Es läuft bereits eine Agenda.")
        if (def.requiresFlag != null && def.requiresFlag !in state.flags) return state.fail("Noch nicht möglich.")
        val week = Clock.week(state.clock.day)
        var s = state.copy(lead = state.lead.copy(agenda = AgendaState(id, week, week + TERM_WEEKS, baseline = expansionScore(state))))
        val d = s.director!!
        s = when (id) {
            "transparency" -> s.copy(director = d.copy(morale = (d.morale - 3).pct()))
            "security" -> s.copy(director = d.copy(oversight = (d.oversight - 2).pct()))
            "people" -> Directorate.spend(s, 3000)
            "expansion" -> s.copy(director = d.copy(morale = (d.morale - 2).pct()))
            "academy" -> Directorate.spend(s, 2000)
            else -> s
        }
        s = Career.journal(s, "agenda", "Agenda: ${def.name}", def.description)
        return Result(s, listOf("Agenda „${def.name}“ beschlossen – Bewertung in $TERM_WEEKS Wochen.", def.cost))
    }

    /** Wird aus der Wochenabrechnung aufgerufen; bewertet eine abgelaufene Agenda genau einmal. */
    fun weekly(data: GameData, state: GameState, lines: MutableList<String>, msgs: MutableList<String>): GameState {
        val a = state.lead.agenda ?: return state
        if (Clock.week(state.clock.day) < a.endWeek) return state
        val def = byId[a.id] ?: return state.copy(lead = state.lead.copy(agenda = null))
        val goals = goals(data, state)
        val success = goals.isNotEmpty() && goals.all { it.met }
        var s = state
        val d = s.director
        if (success) {
            s = when (a.id) {
                "transparency" -> Directorate.earn(s, 15000).let { it.copy(factionStanding = it.factionStanding + ("government" to ((it.factionStanding["government"] ?: 0) + 10).coerceIn(-100, 100))) }
                "security" -> Ops.shiftInfluence(Directorate.earn(s, 12000), "syndicate", -5).let { it.copy(player = it.player.copy(reputation = it.player.reputation + 15)) }
                "people" -> {
                    var x = s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion + 6).pct()), director = d?.copy(morale = (d.morale + 6).pct()))
                    x.team.forEach { id -> val l = Life.life(data, x, id); x = x.copy(living = x.living.copy(lives = x.living.lives + (id to l.copy(stress = (l.stress - 15).pct())))) }
                    x
                }
                "expansion" -> Directorate.earn(s, 20000)
                "academy" -> s.copy(player = s.player.copy(reputation = s.player.reputation + 20, skillPoints = s.player.skillPoints + 1),
                    factionStanding = s.factionStanding + ("civilian" to ((s.factionStanding["civilian"] ?: 0) + 8).coerceIn(-100, 100)))
                else -> s
            }
        } else if (d != null) {
            s = when (a.id) {
                "transparency" -> s.copy(director = d.copy(morale = (d.morale - 5).pct()))
                "security" -> s.copy(director = d.copy(oversight = (d.oversight - 6).pct()))
                "people" -> s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion - 6).pct()))
                "expansion" -> s.copy(director = d.copy(oversight = (d.oversight - 5).pct()))
                "academy" -> s.copy(director = d.copy(morale = (d.morale - 4).pct()))
                else -> s
            }
        }
        val summary = goals.joinToString(" · ") { "${it.label} ${it.current}/${it.target}" + if (it.met) " ✓" else " ✗" }
        val result = AgendaResult(a.id, a.startWeek, a.endWeek, success, summary)
        s = s.copy(lead = s.lead.copy(agenda = null, agendaHistory = (s.lead.agendaHistory + result).takeLast(12)))
        val title = "Agenda „${def.name}“ ${if (success) "erfüllt" else "verfehlt"}"
        lines += "$title – $summary"
        msgs += title + if (success) ": ${def.reward}" else ": ${def.penalty}"
        s = Career.journal(s, "agenda", title, if (success) def.reward else def.penalty)
        return s
    }
}

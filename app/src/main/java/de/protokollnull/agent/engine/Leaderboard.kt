package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v1.0: Die Rangliste der Division – die Wandtafel im Flur, über die Alex, Jaden und Thom reden.
 *
 * Wer steht darauf? Du sowie alle Figuren mit Laufbahn (Rekrutinnen und Rekruten, Fachpersonal,
 * Agentinnen und Agenten der Division) und eigene Rekruten. Die Leitungsebene (Michael, Amanda,
 * Percy) und Externe stehen nicht darauf. Die Punkte stammen ausschließlich aus echten Werten der
 * Simulation – nichts wird für die Anzeige erfunden:
 *
 *  - Dienstgrad × 100
 *  - letzte Note (A 40 · B 30 · C 20 · D 10 · E 0)
 *  - Einsatzbilanz: erfolgreiche Einsätze × 3 (höchstens 60)
 *  - Ausbildung (höchstens 30): bei dir Trainingstage der letzten 30 Tage, bei anderen der Fortschritt
 *    zur nächsten Prüfung
 *
 * Die Platzierung wird einmal pro Woche festgehalten, damit ein Trend (▲/▼) entsteht.
 */
object Leaderboard {
    const val PLAYER = "player"

    data class Entry(
        val id: String, val name: String, val rank: Int, val grade: String?, val missions: Int, val training: Int,
        val score: Int, val isPlayer: Boolean, val status: String = LifeStatus.DUTY
    )

    fun gradePoints(g: String?) = when (g) { "A" -> 40; "B" -> 30; "C" -> 20; "D" -> 10; else -> 0 }

    /** Figuren, die auf der Tafel stehen (sichtbar, mit Laufbahn, schon in der Division). */
    fun members(data: GameData, state: GameState): List<String> = Life.ids(data, state).filter { id ->
        val custom = state.customNpcs.firstOrNull { it.id == id }
        val def = data.agentById[id]
        when {
            custom != null -> state.clock.day >= custom.createdDay
            def == null -> false
            id == "kira" -> "kira_joined" in state.flags
            else -> def.factionId == "division" && Life.profile(data, state, id) in setOf("recruit", "staff", "agent") && World.revealed(data, state, id)
        }
    }

    fun playerEntry(data: GameData, state: GameState): Entry {
        val p = state.player
        val grade = Career.latestEvaluation(state)?.grade
        val missions = state.completedMissions.size + state.ops.history.count { it.status == ContractStatus.DONE }
        val training = state.career.trainingDays.count { it > state.clock.day - 30 }.coerceAtMost(30)
        return Entry(PLAYER, p.codename, p.rank, grade, missions, training,
            p.rank * 100 + gradePoints(grade) + (missions * 3).coerceAtMost(60) + training, isPlayer = true)
    }

    fun npcEntry(data: GameData, state: GameState, id: String): Entry {
        val l = Life.life(data, state, id)
        val grade = l.grades.lastOrNull()
        val training = (l.careerXp * 30 / Life.examThreshold(l.rank).coerceAtLeast(1)).coerceIn(0, 30)
        return Entry(id, Relations.displayName(data, state, id), l.rank, grade, l.missions, training,
            l.rank * 100 + gradePoints(grade) + (l.missions * 3).coerceAtMost(60) + training, isPlayer = false, status = l.status)
    }

    /** Sortierte Tafel: Punkte absteigend, bei Gleichstand Dienstgrad, dann Name. */
    fun board(data: GameData, state: GameState): List<Entry> =
        (listOf(playerEntry(data, state)) + members(data, state).map { npcEntry(data, state, it) })
            .sortedWith(compareByDescending<Entry> { it.score }.thenByDescending { it.rank }.thenBy { it.name })

    fun placeOf(data: GameData, state: GameState, id: String = PLAYER): Int =
        board(data, state).indexOfFirst { it.id == id }.let { if (it < 0) -1 else it + 1 }

    /** Wöchentliche Momentaufnahme der eigenen Platzierung (genau einmal pro Woche, auch im Zeitraffer). */
    fun weeklySnapshot(data: GameData, state: GameState): GameState {
        val week = Clock.week(state.clock.day)
        val c = state.career
        if (c.boardWeek >= week) return state
        val place = placeOf(data, state)
        return state.copy(career = c.copy(boardWeek = week, boardPrevPlace = if (c.boardPlace > 0) c.boardPlace else place, boardPlace = place))
    }

    /** Veränderung seit der letzten Woche: positiv = aufgestiegen. */
    fun trend(state: GameState): Int = if (state.career.boardPrevPlace > 0 && state.career.boardPlace > 0) state.career.boardPrevPlace - state.career.boardPlace else 0
}

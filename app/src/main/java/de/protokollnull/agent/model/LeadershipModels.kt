package de.protokollnull.agent.model

import kotlinx.serialization.Serializable

/*
 * v0.8 „Director's Legacy“: Teamführung, Delegation, Direktorat, Standorte,
 * Personalwesen und Privatleben. Alle Felder haben Defaults – Spielstände
 * aus v0.4–v0.7 laden ohne diese Daten.
 */

/** Delegierte Teamarbeit (Training, Vorbereitung, Aufklärung …). */
@Serializable
data class TeamTask(
    val id: String,
    val npcId: String,
    val kind: String,
    val startDay: Int,
    val endDay: Int,
    /** Mentor bzw. Partner (z. B. beim Mentoring). */
    val partnerId: String? = null,
    val done: Boolean = false,
    val result: String = ""
)

object DelegationStatus {
    const val PREPARED = "prepared"
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
    const val ABORTED = "aborted"
    fun label(id: String) = when (id) {
        PREPARED -> "Vorbereitet"; RUNNING -> "Läuft"; DONE -> "Abgeschlossen"; FAILED -> "Gescheitert"; ABORTED -> "Abgebrochen"; else -> id
    }
    fun closed(id: String) = id in setOf(DONE, FAILED, ABORTED)
}

/** Ein an ein Team delegierter v0.7-Auftrag. */
@Serializable
data class Delegation(
    val id: String,
    val contractId: String,
    val title: String,
    val team: List<String>,
    val leaderId: String? = null,
    val status: String = DelegationStatus.PREPARED,
    val preparedDay: Int = 0,
    val startDay: Int = -1,
    val returnDay: Int = -1,
    val delayed: Boolean = false,
    val chance: Int = 0,
    val report: List<String> = emptyList(),
    val settled: Boolean = false
)

@Serializable
data class PerformanceReview(val day: Int, val grade: String, val note: String)

/** Ein Projekt einer Abteilung. */
@Serializable
data class DeptProject(
    val id: String,
    val startDay: Int,
    val weeksDone: Int = 0,
    val finished: Boolean = false
)

/** Zustand einer Außenstelle. */
@Serializable
data class SiteState(
    val level: Int = 1,
    val open: Boolean = true,
    val staff: List<String> = emptyList(),
    val openedDay: Int = 0
)

/** Ein Wochen- oder Monatsbericht mit nachprüfbaren Zahlen. */
@Serializable
data class PeriodReport(
    val day: Int,
    val kind: String,
    val title: String,
    val lines: List<String>,
    val income: Int = 0,
    val expenses: Int = 0,
    val budgetAfter: Int = 0
)

/** Später eintretende Folge einer Entscheidung. */
@Serializable
data class Fallout(
    val day: Int,
    val id: String,
    val text: String,
    val budget: Int = 0,
    val morale: Int = 0,
    val oversight: Int = 0,
    val npcId: String? = null,
    val trust: Int = 0,
    val faction: String? = null,
    val influence: Int = 0
)

@Serializable
data class LeadershipState(
    /** Teamrolle je Figur: recon, tech, medic, comms, support */
    val roles: Map<String, String> = emptyMap(),
    val tasks: List<TeamTask> = emptyList(),
    val delegations: List<Delegation> = emptyList(),
    val cohesion: Int = 50,
    val teamWins: Int = 0,
    val teamLosses: Int = 0,
    /** Teametat für Rang 8–9 (vor dem Organisationsbudget des Direktorats). */
    val teamBudget: Int = 0,
    val reviews: Map<String, PerformanceReview> = emptyMap(),
    /** Mentor je Schützling. */
    val mentors: Map<String, String> = emptyMap(),
    val projects: List<DeptProject> = emptyList(),
    val hqModules: Map<String, Int> = emptyMap(),
    val sites: Map<String, SiteState> = emptyMap(),
    val sectionDept: String? = null,
    val pendingDilemma: String? = null,
    val dilemmaDay: Int = -1,
    val dilemmaHistory: Map<String, Int> = emptyMap(),
    val fallout: List<Fallout> = emptyList(),
    val reports: List<PeriodReport> = emptyList(),
    /** Zuletzt abgerechnete Woche (genau eine Abrechnung pro Woche). */
    val lastFinanceWeek: Int = -1,
    val lastMonthReport: Int = -1,
    val budgetRequest: Int = 0,
    val budgetRequestDay: Int = -1,
    val counter: Int = 0,
    /** Vorbereitungsbonus für den nächsten Einsatz (aus delegierter Vorbereitung). */
    val prepReady: Int = 0,
    /** Absagen: Figur → Tag, ab dem wieder gefragt werden kann. */
    val refusals: Map<String, Int> = emptyMap(),
    val log: List<String> = emptyList()
)

// --- Privatleben -------------------------------------------------------------------

@Serializable
data class PrivateLife(
    /** quarters, small, large, upscale */
    val housing: String = "quarters",
    val furniture: Set<String> = emptySet(),
    /** Zuletzt bezahlte Mietwoche. */
    val lastRentWeek: Int = -1,
    val hobbies: Map<String, Int> = emptyMap(),
    val coverJob: String? = null,
    val coverJobWeeks: Int = 0,
    /** Woche der zuletzt gearbeiteten Schicht. */
    val shiftWeek: Int = -1,
    val jobWarnings: Int = 0,
    val missedRent: Int = 0,
    /** Letzter Tag je Aktivität bzw. Treffen (gegen Wiederholungs-Exploits). */
    val lastActivity: Map<String, Int> = emptyMap(),
    val leisureToday: Int = 0,
    val leisureDay: Int = -1,
    val pendingEvent: String? = null,
    val eventDay: Int = -1,
    val eventsSeen: Map<String, Int> = emptyMap(),
    val meetings: List<String> = emptyList(),
    val log: List<String> = emptyList()
)

package de.protokollnull.agent.model

import kotlinx.serialization.Serializable

/*
 * v0.7 „Shadow Operations“: Missionsdirektor 2.0, Fraktionen, Ermittlungsakten
 * und optionale Einsatzketten. Alle Felder haben Defaults – Spielstände aus
 * v0.4–v0.6 laden ohne diese Daten.
 */

object ContractStatus {
    const val OPEN = "open"
    const val POSTPONED = "postponed"
    const val IN_PROGRESS = "in_progress"
    const val DONE = "done"
    const val FAILED = "failed"
    const val EXPIRED = "expired"
    const val ABORTED = "aborted"
    const val DECLINED = "declined"
    fun label(id: String) = when (id) {
        OPEN -> "Offen"
        POSTPONED -> "Vertagt"
        IN_PROGRESS -> "Läuft (mehrtägig)"
        DONE -> "Erfüllt"
        FAILED -> "Gescheitert"
        EXPIRED -> "Abgelaufen"
        ABORTED -> "Abgebrochen"
        DECLINED -> "Abgelehnt"
        else -> id
    }
    fun closed(id: String) = id in setOf(DONE, FAILED, EXPIRED, ABORTED, DECLINED)
}

/** Eine alternative Vorgehensweise eines Auftrags. */
@Serializable
data class ContractApproach(
    val id: String,
    val name: String,
    val description: String,
    /** Hauptattribut der Proben. */
    val attribute: String,
    val skill: String? = null,
    /** Zuordnung zu Missions.approaches (balanced/stealth/social/direct). */
    val style: String = "balanced",
    /** Mit Taktikbrett? */
    val tactical: Boolean = false,
    val risk: String = ""
)

/** Ein dynamischer Auftrag auf dem Missionsboard. */
@Serializable
data class Contract(
    val id: String,
    val archetype: String,
    val regionId: String,
    val clientId: String,
    val clientNpc: String? = null,
    val targetFaction: String,
    val objectiveId: String,
    val entryId: String,
    val complicationId: String,
    val title: String,
    val briefing: String,
    val approaches: List<ContractApproach>,
    val risks: List<String> = emptyList(),
    val difficulty: Int = 1,
    /** 1 = Routine, 2 = dringend, 3 = kritisch */
    val urgency: Int = 1,
    val postedDay: Int = 0,
    val expiresDay: Int = 7,
    /** Einsatztage (1–3); mehrtägige Aufträge laufen in Phasen. */
    val phases: Int = 1,
    val phase: Int = 0,
    val nextPhaseDay: Int = 0,
    val carriedIntel: Int = 0,
    val minAge: Int = 16,
    val maxTeam: Int = 1,
    val credits: Int = 0,
    val xp: Int = 0,
    val reputation: Int = 0,
    val rewardItems: Map<String, Int> = emptyMap(),
    val mapId: String = "",
    val seed: Long = 0,
    val status: String = ContractStatus.OPEN,
    val postponed: Int = 0,
    val chainId: String? = null,
    val chainStep: Int = 0,
    val caseId: String? = null,
    /** Kompetenzbereiche (Life), die im Team helfen. */
    val skills: List<String> = emptyList(),
    val successConsequence: String = "",
    val failConsequence: String = "",
    val followUp: String? = null
)

@Serializable
data class ContractRecord(
    val id: String,
    val title: String,
    val archetype: String,
    val status: String,
    val day: Int,
    val targetFaction: String,
    val notes: List<String> = emptyList()
)

/** Persistente Haltung und Stärke einer Organisation. */
@Serializable
data class FactionState(
    /** Einfluss in der Stadt 0..100 */
    val influence: Int = 30,
    /** Ressourcen 0..100 */
    val resources: Int = 50,
    /** Fortschritt im wiederkehrenden Konfliktbogen */
    val arc: Int = 0,
    val lastEvent: String = ""
)

/** Ein Hinweis im Ermittlungsjournal. */
@Serializable
data class Clue(
    val id: String,
    val caseId: String,
    val text: String,
    val source: String,
    /** 1 = Gerücht, 2 = Aussage, 3 = Beweis */
    val quality: Int = 1,
    val points: String? = null,
    val people: List<String> = emptyList(),
    val places: List<String> = emptyList(),
    val contradicts: String? = null,
    val day: Int = 0
)

@Serializable
data class CaseState(
    val id: String,
    val openedDay: Int = 0,
    val solved: Boolean = false,
    val solution: String? = null,
    val accused: String? = null,
    val resultText: String = ""
)

@Serializable
data class OpsState(
    val contracts: List<Contract> = emptyList(),
    val lastGenDay: Int = -1,
    val counter: Int = 0,
    val history: List<ContractRecord> = emptyList(),
    val factions: Map<String, FactionState> = emptyMap(),
    val clues: List<Clue> = emptyList(),
    val cases: Map<String, CaseState> = emptyMap(),
    val chains: Map<String, Int> = emptyMap(),
    /** Weltlage: letzte Konsequenzen (gedeckelt). */
    val worldLog: List<String> = emptyList(),
    /** Bereits verbuchte Abschlüsse (gegen doppelte Belohnung). */
    val settled: Set<String> = emptySet()
)

// --- Optionale Einsatzketten (assets/game/ops_chains.json) ----------------------------

@Serializable
data class ChainDecision(
    val id: String,
    val text: String,
    val result: String,
    val effects: Effects? = null,
    val npcEffects: Map<String, Effects> = emptyMap(),
    val factionInfluence: Map<String, Int> = emptyMap(),
    val clue: String? = null
)

@Serializable
data class ChainStep(
    val title: String,
    val archetype: String,
    val objective: String,
    val regionId: String,
    val briefing: String,
    val clientNpc: String? = null,
    val difficulty: Int = 2,
    val decisionTitle: String = "",
    val decisionText: String = "",
    val decisions: List<ChainDecision> = emptyList()
)

@Serializable
data class ChainDefinition(
    val id: String,
    val title: String,
    val summary: String,
    val faction: String,
    val strand: String = "",
    val conditions: Conditions? = null,
    val minAge: Int = 16,
    val steps: List<ChainStep>
)

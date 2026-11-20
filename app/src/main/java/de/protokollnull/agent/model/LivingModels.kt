package de.protokollnull.agent.model

import kotlinx.serialization.Serializable

/*
 * v0.6 „Living Division“: persistente NPC-Leben, strukturierte Erinnerungen,
 * NPC-NPC-Beziehungen und HQ-Ereignisse. Alle Felder haben Defaults, damit
 * Spielstände aus v0.4/v0.5 ohne diese Daten geladen werden können.
 */

/** Aufenthalts- bzw. Dienststatus einer Figur. */
object LifeStatus {
    const val DUTY = "duty"
    const val TRAINING = "training"
    const val FREE = "free"
    const val MISSION = "mission"
    const val INJURED = "injured"
    const val VACATION = "vacation"
    const val ABSENT = "absent"
    const val TRANSFERRED = "transferred"
    val all = listOf(DUTY, TRAINING, FREE, MISSION, INJURED, VACATION, ABSENT, TRANSFERRED)
    fun label(id: String): String = when (id) {
        DUTY -> "Im Dienst"
        TRAINING -> "Im Training"
        FREE -> "Freizeit"
        MISSION -> "Im Einsatz"
        INJURED -> "Verletzt"
        VACATION -> "Urlaub"
        ABSENT -> "Abwesend"
        TRANSFERRED -> "Versetzt"
        else -> id
    }
    /** Figur ist im Hauptquartier anzutreffen. */
    fun inHq(id: String) = id in setOf(DUTY, TRAINING, FREE, INJURED)
}

@Serializable
data class NpcLife(
    val status: String = LifeStatus.DUTY,
    /** Letzter Tag (inklusive) des aktuellen Sonderstatus; -1 = tagesweise Entscheidung. */
    val statusUntil: Int = -1,
    val statusNote: String = "",
    /** Eigener Rang der Figur (1..9), nicht an die Zeit gekoppelt. */
    val rank: Int = 1,
    val careerXp: Int = 0,
    /** Schwerpunkt des eigenen Trainings (Fertigkeits-ID). */
    val focus: String = "",
    /** Zusätzliche Fertigkeitsstufen aus eigenem Training. */
    val skillGain: Map<String, Int> = emptyMap(),
    val skillProgress: Map<String, Int> = emptyMap(),
    /** Sechs trainierbare Attribute wie beim Spieler; alte Saves bleiben durch Defaults gültig. */
    val attributes: Map<String, Int> = emptyMap(),
    val attributeProgress: Map<String, Int> = emptyMap(),
    val trainingDays: Int = 0,
    val missions: Int = 0,
    val missionsFailed: Int = 0,
    val examsPassed: Int = 0,
    val examsFailed: Int = 0,
    val examCooldownUntil: Int = -1,
    /** Monatsnoten A–E (letzte 6). */
    val grades: List<String> = emptyList(),
    val mood: Int = 60,
    val stress: Int = 20,
    val injuries: Int = 0,
    val goal: String = "",
    val lastDecision: String = "",
    val lastDecisionDay: Int = -1,
    /** Laufender Einsatz: Schwierigkeit, Ausgang wird am letzten Tag ermittelt. */
    val missionDifficulty: Int = 0,
    val transferredDay: Int = -1,
    val monthStartXp: Int = 0
)

/** Ein Eintrag im Divisionsleben (Nachrichtenfeed). */
@Serializable
data class LifeEvent(
    val day: Int,
    val npcId: String,
    val type: String,
    val text: String,
    val others: List<String> = emptyList()
)

/** Strukturierte Erinnerung an ein gemeinsames Erlebnis mit dem Spieler. */
@Serializable
data class MemoryEntry(
    val day: Int,
    val type: String,
    val text: String,
    /** 1 = beiläufig … 5 = prägend */
    val weight: Int = 2
)

/** Zusammenfassung älterer Erinnerungen. */
@Serializable
data class MemorySummary(
    val fromDay: Int,
    val toDay: Int,
    val count: Int,
    val text: String,
    val types: Map<String, Int> = emptyMap(),
    val highlight: String = ""
)

@Serializable
data class MemoryBook(
    val entries: List<MemoryEntry> = emptyList(),
    val summaries: List<MemorySummary> = emptyList()
)

/** Ungelöster Konflikt zwischen Spieler und Figur. */
@Serializable
data class Conflict(
    val reason: String,
    val sinceDay: Int,
    /** 1..3 */
    val severity: Int = 1,
    val source: String = ""
)

@Serializable
data class HqEventRecord(val day: Int, val eventId: String, val choiceId: String, val text: String)

@Serializable
data class LivingState(
    val lives: Map<String, NpcLife> = emptyMap(),
    /** NPC-NPC-Beziehung, Schlüssel „a|b“ (alphabetisch), −100..100. */
    val bonds: Map<String, Int> = emptyMap(),
    /** Wie oft zwei NPCs gemeinsam einen Einsatz absolviert haben (einmal pro Einsatz). */
    val sharedMissions: Map<String, Int> = emptyMap(),
    /** Volljährige NPC-Paare, Schlüssel wie [bonds]. */
    val couples: Set<String> = emptySet(),
    val feed: List<LifeEvent> = emptyList(),
    val memories: Map<String, MemoryBook> = emptyMap(),
    val conflicts: Map<String, Conflict> = emptyMap(),
    val milestones: Map<String, Set<String>> = emptyMap(),
    /** Geschenke: NPC -> Item -> letzter Tag. */
    val gifts: Map<String, Map<String, Int>> = emptyMap(),
    val pendingHqEvent: String? = null,
    val pendingHqEventDay: Int = -1,
    val hqHistory: List<HqEventRecord> = emptyList(),
    /** Letzter bereits simulierter Tag. */
    val lastDay: Int = -1,
    /** Zähler für erzählerische Bögen (Percy-Druck, Amanda-Gutachten …). */
    val arcs: Map<String, Int> = emptyMap(),
    /** Ehemalige romantische Interessen, die bewusst beendet wurden. */
    val closedRomances: Set<String> = emptySet()
) {
    companion object {
        fun key(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"
    }
}

// --- HQ-Ereignisse (assets/game/hq_events.json) --------------------------------

/** Änderung am Leben einer Figur durch eine Ereigniswahl. */
@Serializable
data class LifeChange(
    val npcId: String,
    val status: String? = null,
    val days: Int = 0,
    val note: String = "",
    val mood: Int = 0,
    val careerXp: Int = 0,
    val bondWith: String? = null,
    val bond: Int = 0
)

@Serializable
data class HqChoice(
    val id: String,
    val text: String,
    val result: String,
    val effects: Effects? = null,
    /** Beziehungs-Effekte je Figur. */
    val npcEffects: Map<String, Effects> = emptyMap(),
    val life: List<LifeChange> = emptyList(),
    /** Figur → Konfliktgrund. */
    val conflicts: Map<String, String> = emptyMap(),
    val resolveConflicts: List<String> = emptyList(),
    val memories: Map<String, String> = emptyMap(),
    val arcs: Map<String, Int> = emptyMap(),
    val check: CheckDef? = null,
    val failResult: String? = null,
    val failEffects: Effects? = null,
    val failNpcEffects: Map<String, Effects> = emptyMap(),
    val failLife: List<LifeChange> = emptyList(),
    val conditions: Conditions? = null
)

@Serializable
data class HqEventDefinition(
    val id: String,
    val title: String,
    val text: String,
    val npcId: String? = null,
    val involved: List<String> = emptyList(),
    val conditions: Conditions? = null,
    /** Figur → geforderter Status (z. B. „alex“ → „injured“). */
    val requireStatus: Map<String, String> = emptyMap(),
    /** Figuren, die im HQ sein müssen. */
    val requirePresent: List<String> = emptyList(),
    val minDay: Int = 0,
    val minArc: Map<String, Int> = emptyMap(),
    val minPlayerStress: Int? = null,
    val requireConflict: String? = null,
    /** Tage bis zur Wiederholung; 0 = einmalig. */
    val repeatDays: Int = 0,
    val weight: Int = 10,
    val choices: List<HqChoice>
)

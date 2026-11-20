package de.protokollnull.agent.model

import kotlinx.serialization.Serializable

const val CURRENT_SCHEMA = 5

/** Attribute des Spielercharakters (0..100). */
object Attr {
    const val FITNESS = "fitness"
    const val PRECISION = "precision"
    const val INTELLECT = "intellect"
    const val CHARISMA = "charisma"
    const val PERCEPTION = "perception"
    const val WILLPOWER = "willpower"
    val all = listOf(FITNESS, PRECISION, INTELLECT, CHARISMA, PERCEPTION, WILLPOWER)
    fun label(id: String): String = when (id) {
        FITNESS -> "Fitness"
        PRECISION -> "Präzision"
        INTELLECT -> "Intellekt"
        CHARISMA -> "Ausstrahlung"
        PERCEPTION -> "Wahrnehmung"
        WILLPOWER -> "Willenskraft"
        else -> id
    }
}

object Slots {
    val ids = listOf("weapon", "armor", "gadget", "comms", "bag", "accessory")
    fun label(id: String): String = when (id) {
        "weapon" -> "Bewaffnung"
        "armor" -> "Schutz"
        "gadget" -> "Gerät"
        "comms" -> "Kommunikation"
        "bag" -> "Tasche"
        "accessory" -> "Zubehör"
        else -> id
    }
}

@Serializable
data class ItemStack(
    val uid: Long,
    val itemId: String,
    val quantity: Int = 1,
    val condition: Int = 100
)

@Serializable
data class Relation(
    val friendship: Int = 20,
    val trust: Int = 50,
    val loyalty: Int = 20,
    val respect: Int = 30,
    val affection: Int = 0,
    val rivalry: Int = 0,
    val memories: List<String> = emptyList(),
    val lastTalkDay: Int = -1,
    /** 0 = keine, 1 = Interesse, 2 = Paar, 3 = zusammengezogen, 4 = verheiratet, 5 = Familie */
    val romanceStage: Int = 0,
    val romanceSinceDay: Int = -1
)

@Serializable
data class Appearance(
    val avatar: AvatarSpec = AvatarSpec(),
    val style: String = "sachlich"
)

@Serializable
data class PlayerCharacter(
    val firstName: String = "Lena",
    val lastName: String = "Berg",
    val codename: String = "Null",
    val gender: String = "female",
    val appearance: Appearance = Appearance(),
    val background: String = "analyst",
    val strength: String = "perception",
    val weakness: String = "charisma",
    val difficulty: String = "normal",
    val level: Int = 1,
    val xp: Int = 0,
    val skillPoints: Int = 3,
    val rank: Int = 1,
    val reputation: Int = 0,
    val credits: Int = 300,
    val energy: Int = 100,
    val stress: Int = 10,
    val health: Int = 100,
    val wounds: Int = 0,
    val attributes: Map<String, Int> = mapOf(
        Attr.FITNESS to 30, Attr.PRECISION to 30, Attr.INTELLECT to 30,
        Attr.CHARISMA to 30, Attr.PERCEPTION to 30, Attr.WILLPOWER to 30
    ),
    val trainingProgress: Map<String, Int> = emptyMap(),
    val skills: Map<String, Int> = emptyMap(),
    val inventory: List<ItemStack> = emptyList(),
    val storage: List<ItemStack> = emptyList(),
    val equipment: Map<String, Long> = emptyMap(),
    val nextUid: Long = 1,
    val apartment: Boolean = false,
    /** Heilfortschritt der aktuellen Wunde in Tagen (7 = verheilt, Ruhe/Klinik beschleunigt). */
    val woundProgress: Int = 0,
    val coverJob: String? = null,
    /** v0.6: Interessen für Kompatibilität und Geschenke. */
    val interests: List<String> = emptyList()
) {
    val displayName: String get() = "$firstName $lastName"
}

@Serializable
data class CustomNpc(
    val id: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val avatar: AvatarSpec,
    val traits: List<String>,
    val background: String,
    val profession: String,
    val interests: List<String>,
    val skills: Map<String, Int>,
    /** Spieltag des Eintritts in die Division. */
    val createdDay: Int,
    val ageAtCreation: Int = 16,
    val rank: Int = 1,
    // --- v0.6: zweite Hauptfigur
    val values: List<String> = emptyList(),
    val lifeGoal: String = "",
    val careerWish: String = ""
) {
    val name: String get() = "$firstName $lastName"
}

@Serializable
data class GameClock(
    /** Tag 0 = erster Tag in der Division (Spieler ist 16). */
    val day: Int = 0,
    /** 0 Morgen, 1 Vormittag, 2 Mittag, 3 Nachmittag, 4 Abend, 5 Nacht */
    val slot: Int = 0
)

@Serializable
data class TacticalUnit(
    val id: String,
    val name: String,
    val ally: Boolean,
    val x: Int,
    val y: Int,
    val hp: Int,
    val maxHp: Int,
    val ap: Int = 2,
    val accuracy: Int = 60,
    val damage: Int = 3,
    val armor: Int = 0,
    val ability: String? = null,
    val abilityCooldown: Int = 0,
    val guarding: Boolean = false,
    val stunned: Int = 0,
    val isPlayer: Boolean = false,
    // --- v0.7
    /** Gegnerrolle: guard, flanker, overwatch, rusher; Verbündete: Kompetenz-Schwerpunkt */
    val role: String = "",
    /** Schutzperson: muss überleben bzw. evakuiert werden, greift nicht an. */
    val vip: Boolean = false,
    val range: Int = 6,
    val homeX: Int = -1,
    val homeY: Int = -1,
    val evacuated: Boolean = false,
    // --- v1.0
    /** Überwachungsposten hat in dieser Runde bereits auf eine Bewegung reagiert. */
    val reacted: Boolean = false
) {
    val down: Boolean get() = hp <= 0
}

@Serializable
data class TacticalState(
    val width: Int,
    val height: Int,
    val tiles: List<String>,
    val units: List<TacticalUnit>,
    val turn: Int = 1,
    val maxTurns: Int = 14,
    val objective: String = "eliminate",
    val selectedUnit: String? = null,
    val log: List<String> = emptyList(),
    val finished: Boolean = false,
    val won: Boolean = false,
    val focusOrder: Boolean = false,
    val seed: Long = 0,
    val rolls: Int = 0,
    val enemyName: String = "Gegner",
    val nonLethal: Boolean = true,
    // --- v0.7
    val mapId: String = "",
    val environment: String = "",
    val mapName: String = "",
    /** Gesicherte Infopunkte / besuchte Aufklärungspunkte als „x,y“. */
    val secured: Set<String> = emptySet(),
    val requiredSecured: Int = 0,
    val holdTurns: Int = 0,
    val held: Int = 0,
    val stalemate: Int = 0,
    val progressMark: Int = 0
) {
    fun tile(x: Int, y: Int): Char =
        if (y in tiles.indices && x in 0 until tiles[y].length) tiles[y][x] else '#'
}

@Serializable
data class SearchState(
    val spots: List<String>,
    val clues: Set<Int>,
    val searched: Set<Int> = emptySet(),
    val found: Int = 0,
    val actionsLeft: Int,
    val hints: Map<Int, Int> = emptyMap()
)

@Serializable
data class ObserveState(
    val round: Int = 0,
    val rounds: Int,
    val targets: List<String>,
    val correct: List<Int>,
    val hintRevealed: Boolean = false,
    val hits: Int = 0
)

@Serializable
data class MissionRun(
    val missionId: String,
    val seed: Long,
    val stageId: String,
    val generated: MissionDefinition? = null,
    val approach: String = "balanced",
    val team: List<String> = emptyList(),
    val intel: Int = 0,
    val alarm: Int = 0,
    val cover: Int = 3,
    val stressTaken: Int = 0,
    val damageTaken: Int = 0,
    val log: List<String> = emptyList(),
    val search: SearchState? = null,
    val observe: ObserveState? = null,
    val tactical: TacticalState? = null,
    val lastText: String = "",
    val rollCounter: Int = 0,
    val pendingEffects: List<Effects> = emptyList(),
    val result: String? = null
)

@Serializable
data class MissionReport(
    val missionId: String,
    val title: String,
    val result: String,
    val day: Int,
    val xp: Int,
    val credits: Int,
    val reputation: Int,
    val notes: List<String>
)

@Serializable
data class DialogueRun(
    val dialogueId: String,
    val nodeId: String,
    val lastResult: String = "",
    val history: List<String> = emptyList()
)

@Serializable
data class DirectorState(
    val budget: Int = 50000,
    val departments: Map<String, Int> = mapOf(
        "training" to 1, "tech" to 1, "medical" to 1, "intel" to 1, "security" to 1
    ),
    val heads: Map<String, String> = emptyMap(),
    val morale: Int = 60,
    val oversight: Int = 50,
    val policy: String = "balanced",
    val lastWeekReport: String = ""
)

@Serializable
data class Settings(
    val fontScale: Float = 1f,
    val animations: Boolean = true,
    val sfxVolume: Int = 60,
    val musicVolume: Int = 35,
    val highContrast: Boolean = false,
    val haptics: Boolean = true
)

@Serializable
data class LogEntry(val day: Int, val slot: Int, val text: String)

@Serializable
data class GameState(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val seed: Long = 1L,
    val player: PlayerCharacter = PlayerCharacter(),
    val clock: GameClock = GameClock(),
    val phase: String = "prologue",
    val prologueNode: String = "prolog_01",
    val location: String = "dorm",
    val relations: Map<String, Relation> = emptyMap(),
    val flags: Set<String> = emptySet(),
    val completedMissions: Set<String> = emptySet(),
    val failedMissions: Set<String> = emptySet(),
    val completedDialogues: Set<String> = emptySet(),
    val activeMission: MissionRun? = null,
    val activeDialogue: DialogueRun? = null,
    val reports: List<MissionReport> = emptyList(),
    val customNpcs: List<CustomNpc> = emptyList(),
    val team: List<String> = emptyList(),
    val factionStanding: Map<String, Int> = emptyMap(),
    val director: DirectorState? = null,
    val routine: String = "balanced",
    val log: List<LogEntry> = emptyList(),
    val boardWeek: Int = -1,
    val board: List<MissionDefinition> = emptyList(),
    val rolls: Int = 0,
    val lastAutosave: Long = 0,
    val pendingCompanion: CustomNpc? = null,
    // --- v0.5: Zeitplan, Karriere, Ausrüstungsprofile (alle mit Defaults → v0.4-kompatibel)
    val weekPlan: List<String> = listOf("combat", "tech", "recon", "duty", "exam", "social", "rest"),
    val simReports: List<SimReport> = emptyList(),
    val simEventCounts: Map<String, Int> = emptyMap(),
    val career: CareerState = CareerState(),
    val journal: List<JournalEntry> = emptyList(),
    val activeExam: ExamRun? = null,
    val loadouts: List<Loadout> = Loadout.defaults(),
    // --- v0.6: Living Division (Default → v0.4/v0.5-kompatibel)
    val living: LivingState = LivingState(),
    // --- v0.7: Shadow Operations (Default → v0.4–v0.6-kompatibel)
    val ops: OpsState = OpsState(),
    // --- v0.8: Director's Legacy (Default → v0.4–v0.7-kompatibel)
    val lead: LeadershipState = LeadershipState(),
    val life: PrivateLife = PrivateLife()
)

@Serializable
data class SimReport(
    val startDay: Int,
    val endDay: Int,
    val plannedDays: Int,
    val stopReason: String = "",
    val attributeGains: Map<String, Int> = emptyMap(),
    val activityDays: Map<String, Int> = emptyMap(),
    val failedSessions: Int = 0,
    val xp: Int = 0,
    val levelBefore: Int = 1,
    val levelAfter: Int = 1,
    val income: Int = 0,
    val expenses: Int = 0,
    val reputation: Int = 0,
    val examPrep: Int = 0,
    val woundsBefore: Int = 0,
    val woundsAfter: Int = 0,
    val healthBefore: Int = 100,
    val healthAfter: Int = 100,
    val stressBefore: Int = 0,
    val stressAfter: Int = 0,
    val relationChanges: Map<String, Int> = emptyMap(),
    val trainerSessions: Map<String, Int> = emptyMap(),
    val events: List<String> = emptyList()
)

@Serializable
data class Evaluation(val day: Int, val grade: String, val score: Int, val comment: String, val auto: Boolean = false)

@Serializable
data class CareerState(
    /** Prüfungsvorbereitung 0..100, wird bei einem Prüfungsversuch verbraucht. */
    val examPrep: Int = 0,
    val examCooldownUntil: Int = -1,
    /** Auflage nach nicht bestandener Prüfung: benötigte Vorbereitung. */
    val examPrepRequired: Int = 0,
    val failedExams: Int = 0,
    val evaluations: List<Evaluation> = emptyList(),
    val attrSumAtLastEval: Int = 0,
    /** Tage, an denen erfolgreich trainiert wurde (letzte 90 Tage). */
    val trainingDays: List<Int> = emptyList(),
    val sessionsDay: Int = -1,
    val sessionsToday: Int = 0,
    val lastMentorDay: Int = -1,
    val goals: Map<String, Int> = emptyMap(),
    val awards: Set<String> = emptySet()
)

@Serializable
data class JournalEntry(val day: Int, val type: String, val title: String, val text: String)

@Serializable
data class ExamRun(
    val rank: Int,
    val phase: Int = 0,
    val score: Int = 0,
    val notes: List<String> = emptyList(),
    val finished: Boolean = false,
    val passed: Boolean = false
)

@Serializable
data class Loadout(val id: String, val name: String, val items: Map<String, String> = emptyMap()) {
    companion object {
        fun defaults() = listOf(
            Loadout("recon", "Aufklärung"), Loadout("analysis", "Analyse"),
            Loadout("medic", "Erste Hilfe"), Loadout("tactical", "Taktische Unterstützung")
        )
    }
}

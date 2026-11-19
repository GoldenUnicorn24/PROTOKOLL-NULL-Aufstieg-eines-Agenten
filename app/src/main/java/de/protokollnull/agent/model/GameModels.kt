package de.protokollnull.agent.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Inhaltskataloge (aus app/src/main/assets/game/*.json). Neue Felder haben
// Defaults, damit ältere Kataloge weiterhin geladen werden können.
// ---------------------------------------------------------------------------

@Serializable
data class AvatarSpec(
    val skin: Int = 2,
    val hairStyle: Int = 0,
    val hairColor: Int = 0,
    val outfit: Int = 0,
    val eyes: Int = 0,
    val accessory: Int = 0,
    val portraitId: String? = null
)

@Serializable
data class AgentDefinition(
    val id: String,
    val name: String,
    val role: String,
    val factionId: String = "division",
    val ageAtStart: Int = 18,
    val actorName: String? = null,
    val portraitAsset: String? = null,
    val seriesCharacter: Boolean = false,
    val description: String = "",
    val initialTrust: Int = 50,
    val tags: List<String> = emptyList(),
    val bio: String = "",
    val look: String = "",
    val traits: List<String> = emptyList(),
    /** Zeitslot-ID -> Raum-ID (siehe rooms.json). */
    val schedule: Map<String, String> = emptyMap(),
    val skills: Map<String, Int> = emptyMap(),
    val romanceable: Boolean = false,
    val avatar: AvatarSpec? = null,
    /** Teamfähigkeit im Taktikmodus, siehe TacticalAbilities. */
    val teamAbility: String = "focus",
    val combat: Int = 40,
    val gender: String = "male",
    /** Bildausschnitt (BiasAlignment x/y, -1..1) für das Darstellerfoto. */
    val portraitFocus: List<Float> = listOf(0f, -0.5f),
    /** Figur erscheint erst, wenn dieses Flag gesetzt ist (z. B. nach Story-Kontakt). */
    val revealFlag: String? = null,
    // --- v0.6: Lebenssimulation
    val interests: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    val goal: String = "",
    /** recruit | agent | staff | leader | rogue */
    val lifeProfile: String = "agent",
    val startRank: Int = 1
)

@Serializable
data class ItemEffect(val health: Int = 0, val energy: Int = 0, val stress: Int = 0)

@Serializable
data class ItemDefinition(
    val id: String,
    val name: String,
    val category: String,
    val icon: String,
    val weightGrams: Int = 0,
    val rarity: String = "common",
    val maxStack: Int = 1,
    val baseValue: Int = 0,
    val description: String = "",
    /** weapon, armor, gadget, comms, bag, accessory, none */
    val slot: String = "none",
    val minRank: Int = 1,
    val effect: ItemEffect? = null,
    val capacityBonusGrams: Int = 0,
    val buyable: Boolean = true,
    /** Boni auf Attribute (fitness, precision, intellect, charisma, perception, willpower). */
    val bonuses: Map<String, Int> = emptyMap(),
    val armor: Int = 0,
    val damage: Int = 0
)

@Serializable
data class SkillDefinition(
    val id: String,
    val name: String,
    val tree: String,
    val maxRank: Int = 5,
    val prerequisite: String? = null,
    val description: String = "",
    val requires: List<String> = emptyList(),
    val kind: String = "passive",
    val minLevel: Int = 1,
    val attribute: String = "",
    val effect: String = ""
)

@Serializable
data class RankDefinition(
    val rank: Int,
    val name: String,
    val minLevel: Int,
    val minAge: Int = 16,
    val minReputation: Int = 0,
    val description: String = "",
    val supervisorId: String? = null,
    val minSupervisorTrust: Int = 0,
    val requiredFlags: List<String> = emptyList(),
    val requiredMissions: List<String> = emptyList(),
    val examDifficulty: Int = 40,
    /** Was dieser Rang tatsächlich freischaltet (Anzeige in der Karriereübersicht). */
    val unlocks: List<String> = emptyList(),
    val salaryPerWeek: Int = 100,
    val teamSize: Int = 1
)

@Serializable
data class PlaceDefinition(val id: String, val name: String, val type: String, val description: String = "")

@Serializable
data class FactionDefinition(val id: String, val name: String, val stance: String = "neutral", val description: String = "")

@Serializable
data class RoomDefinition(
    val id: String,
    val name: String,
    val floor: Int,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val description: String = "",
    val minRank: Int = 1,
    val minAge: Int = 0,
    val requiredItem: String? = null,
    val requiredFlag: String? = null,
    val actions: List<String> = emptyList()
)

@Serializable
data class FloorDefinition(val floor: Int, val name: String, val description: String = "")

@Serializable
data class HqDefinition(val floors: List<FloorDefinition>, val rooms: List<RoomDefinition>)

// --- Bedingungen & Effekte (Dialoge, Missionen, Story) ---------------------

@Serializable
data class Conditions(
    val minTrust: Int? = null,
    val minFriendship: Int? = null,
    val minAffection: Int? = null,
    val minRespect: Int? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val minRank: Int? = null,
    val minLevel: Int? = null,
    val flagsAll: List<String> = emptyList(),
    val flagsNone: List<String> = emptyList(),
    val minSkill: Map<String, Int> = emptyMap(),
    val minAttribute: Map<String, Int> = emptyMap(),
    val missionsDone: List<String> = emptyList(),
    val hasItem: String? = null,
    // --- v0.6
    val flagsAny: List<String> = emptyList(),
    /** Mindestalter des Gesprächspartners. */
    val minNpcAge: Int? = null,
    val minDay: Int? = null,
    /** NPC-NPC-Beziehung „a|b“ ≥ Wert. */
    val minBond: Map<String, Int> = emptyMap(),
    val maxBond: Map<String, Int> = emptyMap(),
    val noConflict: Boolean = false
)

@Serializable
data class Effects(
    val trust: Int = 0,
    val friendship: Int = 0,
    val loyalty: Int = 0,
    val respect: Int = 0,
    val affection: Int = 0,
    val rivalry: Int = 0,
    val xp: Int = 0,
    val stress: Int = 0,
    val energy: Int = 0,
    val health: Int = 0,
    val credits: Int = 0,
    val reputation: Int = 0,
    val setFlags: List<String> = emptyList(),
    val clearFlags: List<String> = emptyList(),
    val giveItems: Map<String, Int> = emptyMap(),
    val memory: String? = null,
    /** Beziehungs-Effekte auf andere NPCs als den Gesprächspartner: npcId -> Vertrauen. */
    val otherTrust: Map<String, Int> = emptyMap(),
    val attributes: Map<String, Int> = emptyMap(),
    val faction: Map<String, Int> = emptyMap()
)

@Serializable
data class CheckDef(val attribute: String, val skill: String? = null, val difficulty: Int = 40)

@Serializable
data class DialogueChoice(
    val id: String,
    val text: String,
    val nextNode: String? = null,
    val trustDelta: Int = 0,
    val xp: Int = 0,
    val stressDelta: Int = 0,
    val conditions: Conditions? = null,
    val effects: Effects? = null,
    val check: CheckDef? = null,
    val failNode: String? = null,
    val failEffects: Effects? = null
)

@Serializable
data class DialogueNode(
    val id: String,
    val speakerId: String? = null,
    val text: String,
    val choices: List<DialogueChoice> = emptyList()
)

/** Handgeschriebener, deterministisch verzweigter Dialog mit einem NPC oder eine Story-Szene. */
@Serializable
data class DialogueDefinition(
    val id: String,
    val npcId: String? = null,
    val title: String,
    val chapter: String = "",
    val once: Boolean = true,
    val priority: Int = 0,
    val conditions: Conditions? = null,
    val startNode: String,
    val nodes: List<DialogueNode>,
    val completeFlag: String? = null
)

// --- Story (Prolog-Szenen und Kapitel-Struktur) -----------------------------

@Serializable
data class StoryNode(
    val id: String,
    val chapter: String,
    val speakerId: String? = null,
    val text: String,
    val choices: List<DialogueChoice> = emptyList(),
    val age: Int? = null,
    val image: String? = null
)

@Serializable
data class QuestObjective(
    val id: String,
    val text: String,
    val flag: String? = null,
    val missionId: String? = null,
    val dialogueId: String? = null
)

@Serializable
data class QuestDefinition(
    val id: String,
    val chapter: String,
    val title: String,
    val description: String,
    val giverId: String? = null,
    val conditions: Conditions? = null,
    val objectives: List<QuestObjective>,
    val reward: Effects = Effects(),
    val completeFlag: String
)

// --- Missionen ------------------------------------------------------------

@Serializable
data class Outcome(
    val text: String = "",
    /** Stage-ID oder END_SUCCESS / END_FAIL / END_ABORT; null = nächste Stage. */
    val next: String? = null,
    val intel: Int = 0,
    val alarm: Int = 0,
    val cover: Int = 0,
    val stress: Int = 0,
    val health: Int = 0,
    val effects: Effects? = null
)

@Serializable
data class StageOption(
    val id: String,
    val text: String,
    val check: CheckDef? = null,
    val requires: Conditions? = null,
    val success: Outcome = Outcome(),
    val failure: Outcome? = null,
    /** Situative Gegenstandsboni: itemId -> Bonus, wenn ausgerüstet (halber Bonus im Inventar). */
    val itemBonus: Map<String, Int> = emptyMap()
)

@Serializable
data class TacticalSetup(
    val map: List<String>,
    val enemyName: String = "Gegner",
    val enemyHp: Int = 6,
    val enemyAccuracy: Int = 55,
    val objective: String = "eliminate",
    val maxTurns: Int = 14,
    val nonLethal: Boolean = true
)

@Serializable
data class StageDefinition(
    val id: String,
    /** narrative, choice, search, observe, tactical */
    val type: String,
    val title: String = "",
    val text: String = "",
    val speakerId: String? = null,
    val options: List<StageOption> = emptyList(),
    val spots: List<String> = emptyList(),
    val cluesNeeded: Int = 2,
    val actions: Int = 6,
    val rounds: Int = 4,
    val targets: List<String> = emptyList(),
    val intelNeeded: Int = 2,
    val tactical: TacticalSetup? = null,
    val next: String? = null,
    val successText: String = "",
    val failText: String = ""
)

@Serializable
data class MissionDefinition(
    val id: String,
    val title: String,
    val locationId: String,
    val type: String,
    val minRank: Int = 1,
    val minAge: Int = 16,
    val threat: Int = 1,
    val xp: Int = 100,
    val description: String = "",
    val steps: List<String> = emptyList(),
    val storyNodeId: String? = null,
    val factionId: String = "division",
    val chapter: String = "",
    val briefing: String = "",
    val requires: Conditions? = null,
    val giverId: String? = null,
    val credits: Int = 0,
    val reputation: Int = 5,
    val rewardItems: Map<String, Int> = emptyMap(),
    val successEffects: Effects? = null,
    val failEffects: Effects? = null,
    val stages: List<StageDefinition> = emptyList(),
    val witnesses: Int = 0,
    val risk: Int = 1,
    val repeatable: Boolean = false,
    val maxTeam: Int = 1,
    val recommendedItems: List<String> = emptyList(),
    val debriefSuccess: String = "",
    val debriefFail: String = ""
)

@Serializable
data class EventDefinition(
    val id: String,
    val title: String,
    val locationId: String,
    val npcId: String? = null,
    val minAge: Int = 16,
    val description: String,
    val choices: List<DialogueChoice> = emptyList()
)

// ---------------------------------------------------------------------------
// Legacy-Spielstand (Schema 1, Foundation-Version). Nur für die Migration.
// ---------------------------------------------------------------------------

@Serializable
data class LegacyInventoryStack(val itemId: String, val quantity: Int = 1)

@Serializable
data class LegacyPlayerState(
    val name: String = "Ghost",
    val gender: String = "male",
    val ageAtStart: Int = 16,
    val elapsedDays: Int = 0,
    val level: Int = 1,
    val xp: Int = 0,
    val rank: Int = 1,
    val reputation: Int = 0,
    val credits: Int = 750,
    val energy: Int = 100,
    val stress: Int = 10,
    val skillPoints: Int = 3,
    val skills: Map<String, Int> = emptyMap(),
    val relationships: Map<String, Int> = emptyMap(),
    val inventory: List<LegacyInventoryStack> = emptyList()
)

@Serializable
data class LegacyPartnerProfile(
    val id: String,
    val name: String,
    val gender: String,
    val ageAtCreation: Int = 16,
    val playerAgeAtCreation: Int = 18,
    val personality: String = "loyal",
    val specialization: String = "field",
    val romanceEligible: Boolean = true,
    val trust: Int = 15
)

@Serializable
data class LegacyGameSave(
    val schemaVersion: Int = 1,
    val player: LegacyPlayerState = LegacyPlayerState(),
    val currentStoryNode: String = "arrival_001",
    val completedMissions: Set<String> = emptySet(),
    val partner: LegacyPartnerProfile? = null,
    val flags: Set<String> = emptySet()
)

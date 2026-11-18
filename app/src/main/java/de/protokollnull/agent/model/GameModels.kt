package de.protokollnull.agent.model

import kotlinx.serialization.Serializable

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
    val tags: List<String> = emptyList()
)

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
    val description: String = ""
)

@Serializable data class InventoryStack(val itemId: String, val quantity: Int = 1)
@Serializable data class SkillDefinition(
    val id: String, val name: String, val tree: String, val maxRank: Int = 5,
    val prerequisite: String? = null, val description: String = ""
)
@Serializable data class RankDefinition(
    val rank: Int, val name: String, val minLevel: Int,
    val minAge: Int = 16, val minReputation: Int = 0,
    val description: String = ""
)
@Serializable data class PlaceDefinition(val id: String, val name: String, val type: String, val description: String = "")
@Serializable data class FactionDefinition(val id: String, val name: String, val stance: String = "neutral", val description: String = "")
@Serializable data class DialogueChoice(
    val id: String, val text: String, val nextNode: String? = null,
    val trustDelta: Int = 0, val xp: Int = 0, val stressDelta: Int = 0
)
@Serializable data class StoryNode(
    val id: String, val chapter: String, val speakerId: String? = null,
    val text: String, val choices: List<DialogueChoice> = emptyList()
)
@Serializable data class MissionDefinition(
    val id: String, val title: String, val locationId: String, val type: String,
    val minRank: Int = 1, val minAge: Int = 16, val threat: Int = 1,
    val xp: Int = 100, val description: String = "",
    val steps: List<String> = emptyList(), val storyNodeId: String? = null
)
@Serializable data class EventDefinition(
    val id: String, val title: String, val locationId: String, val npcId: String? = null,
    val minAge: Int = 16, val description: String, val choices: List<DialogueChoice> = emptyList()
)
@Serializable data class PlayerState(
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
    val inventory: List<InventoryStack> = listOf(
        InventoryStack("id_card", 1),
        InventoryStack("radio_basic", 1),
        InventoryStack("bandage", 2)
    )
) {
    val age: Int get() = ageAtStart + elapsedDays / 365
}

@Serializable data class PartnerProfile(
    val id: String, val name: String, val gender: String,
    val ageAtCreation: Int = 16, val playerAgeAtCreation: Int = 18,
    val personality: String, val specialization: String,
    val romanceEligible: Boolean = true,
    val trust: Int = 15
)

@Serializable data class GameSave(
    val schemaVersion: Int = 1,
    val player: PlayerState = PlayerState(),
    val currentStoryNode: String = "arrival_001",
    val completedMissions: Set<String> = emptySet(),
    val partner: PartnerProfile? = null,
    val flags: Set<String> = emptySet()
)

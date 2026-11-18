package de.protokollnull.agent.data

import android.content.Context
import de.protokollnull.agent.model.*
import kotlinx.serialization.json.Json

class GameContent(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun read(path: String): String = context.assets.open("game/" + path)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    val agents: List<AgentDefinition> by lazy { json.decodeFromString(read("agents.json")) }
    val items: List<ItemDefinition> by lazy { json.decodeFromString(read("items.json")) }
    val missions: List<MissionDefinition> by lazy { json.decodeFromString(read("missions.json")) }
    val ranks: List<RankDefinition> by lazy { json.decodeFromString(read("ranks.json")) }
    val skills: List<SkillDefinition> by lazy { json.decodeFromString(read("skills.json")) }
    val places: List<PlaceDefinition> by lazy { json.decodeFromString(read("places.json")) }
    val factions: List<FactionDefinition> by lazy { json.decodeFromString(read("factions.json")) }
    val story: List<StoryNode> by lazy { json.decodeFromString(read("story.json")) }
    val events: List<EventDefinition> by lazy { json.decodeFromString(read("events.json")) }

    fun validateReferences(): List<String> {
        val issues = mutableListOf<String>()
        val itemIds = items.map { it.id }.toSet()
        val placeIds = places.map { it.id }.toSet()
        val agentIds = agents.map { it.id }.toSet()
        val storyIds = story.map { it.id }.toSet()
        missions.forEach {
            if (it.locationId !in placeIds) issues += "Mission missing place: " + it.id
            if (it.storyNodeId != null && it.storyNodeId !in storyIds) issues += "Mission missing node: " + it.id
        }
        events.forEach {
            if (it.locationId !in placeIds) issues += "Event missing place: " + it.id
            if (it.npcId != null && it.npcId !in agentIds) issues += "Event missing agent: " + it.id
        }
        story.forEach { node ->
            node.choices.forEach { choice ->
                if (choice.nextNode != null && choice.nextNode !in storyIds) issues += "Story missing destination: " + choice.nextNode
            }
        }
        if ("id_card" !in itemIds || "radio_basic" !in itemIds || "bandage" !in itemIds) issues += "Starting equipment undefined"
        return issues
    }
}

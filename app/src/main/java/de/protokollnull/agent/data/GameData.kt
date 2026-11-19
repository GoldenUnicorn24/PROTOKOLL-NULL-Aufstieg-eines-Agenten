package de.protokollnull.agent.data

import de.protokollnull.agent.model.*
import kotlinx.serialization.json.Json

/**
 * Typisierte, unveränderliche Inhaltskataloge. Plattformunabhängig, damit
 * Unit-Tests die echten JSON-Dateien aus src/main/assets laden können.
 */
class GameData(
    val agents: List<AgentDefinition>,
    val items: List<ItemDefinition>,
    val missions: List<MissionDefinition>,
    val ranks: List<RankDefinition>,
    val skills: List<SkillDefinition>,
    val places: List<PlaceDefinition>,
    val factions: List<FactionDefinition>,
    val story: List<StoryNode>,
    val events: List<EventDefinition>,
    val hq: HqDefinition,
    val dialogues: List<DialogueDefinition>,
    val quests: List<QuestDefinition>,
    val hqEvents: List<HqEventDefinition> = emptyList(),
    /** Dateinamen, die unter assets/ existieren (z. B. "items/radio.svg"). */
    val assetFiles: Set<String> = emptySet(),
    /** Verifizierte Lizenznachweise für Serienstills (show_<id>.jpg). */
    val licensedSeriesStills: Set<String> = emptySet()
) {
    val agentById = agents.associateBy { it.id }
    val itemById = items.associateBy { it.id }
    val missionById = missions.associateBy { it.id }
    val skillById = skills.associateBy { it.id }
    val placeById = places.associateBy { it.id }
    val factionById = factions.associateBy { it.id }
    val storyById = story.associateBy { it.id }
    val roomById = hq.rooms.associateBy { it.id }
    val dialogueById = dialogues.associateBy { it.id }
    val questById = quests.associateBy { it.id }
    val hqEventById = hqEvents.associateBy { it.id }
    fun rank(rank: Int): RankDefinition = ranks.firstOrNull { it.rank == rank } ?: ranks.first()

    /**
     * Porträtpfad (relativ zu assets/). Serienstill nur, wenn Datei vorhanden UND
     * ein überprüfter Lizenzbeleg existiert; sonst das lizenzierte Darstellerfoto.
     */
    fun portraitFor(agentId: String): String? {
        val agent = agentById[agentId] ?: return null
        val show = "cast/show_$agentId.jpg"
        if (show in assetFiles && agentId in licensedSeriesStills) return show
        val asset = agent.portraitAsset
        if (asset != null && asset.endsWith(".jpg") && (assetFiles.isEmpty() || asset in assetFiles)) return asset
        return null
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun load(read: (String) -> String, assetFiles: Set<String> = emptySet()): GameData {
            val licenses = runCatching {
                json.decodeFromString<SeriesStillLicenses>(read("licenses/series_image_licenses.json"))
                    .entries.filter { it.verified && it.license.isNotBlank() && it.proof.isNotBlank() }
                    .map { it.id }.toSet()
            }.getOrDefault(emptySet())
            return GameData(
                agents = json.decodeFromString(read("game/agents.json")),
                items = json.decodeFromString(read("game/items.json")),
                missions = json.decodeFromString(read("game/missions.json")),
                ranks = json.decodeFromString(read("game/ranks.json")),
                skills = json.decodeFromString(read("game/skills.json")),
                places = json.decodeFromString(read("game/places.json")),
                factions = json.decodeFromString(read("game/factions.json")),
                story = json.decodeFromString(read("game/story.json")),
                events = json.decodeFromString(read("game/events.json")),
                hq = json.decodeFromString(read("game/rooms.json")),
                dialogues = json.decodeFromString(read("game/dialogues.json")),
                quests = json.decodeFromString(read("game/quests.json")),
                hqEvents = json.decodeFromString(read("game/hq_events.json")),
                assetFiles = assetFiles,
                licensedSeriesStills = licenses
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class SeriesStillLicense(
    val id: String,
    val file: String,
    val license: String = "",
    val rightsHolder: String = "",
    val proof: String = "",
    val verified: Boolean = false
)

@kotlinx.serialization.Serializable
data class SeriesStillLicenses(val entries: List<SeriesStillLicense> = emptyList())

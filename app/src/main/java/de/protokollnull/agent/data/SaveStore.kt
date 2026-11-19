package de.protokollnull.agent.data

import de.protokollnull.agent.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class SlotInfo(
    val slot: String,
    val title: String,
    val subtitle: String,
    val savedAt: Long
)

/** Versionierte Spielstand-Migration. */
object SaveMigration {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun decode(text: String): GameState = decode(json.parseToJsonElement(text).jsonObject)

    fun decode(obj: JsonObject): GameState {
        val version = obj["schemaVersion"]?.jsonPrimitive?.int ?: 1
        return when {
            version >= 2 -> migrateForward(json.decodeFromJsonElement(GameState.serializer(), obj))
            obj.containsKey("player") && obj["player"] is JsonObject && obj["player"]!!.jsonObject.containsKey("elapsedDays") ->
                fromV1(json.decodeFromJsonElement(LegacyGameSave.serializer(), obj))
            else -> fromV1(json.decodeFromJsonElement(LegacyGameSave.serializer(), obj))
        }
    }

    fun encode(state: GameState): String = json.encodeToString(state)

    private fun migrateForward(s: GameState): GameState {
        // Schema 3 (v0.6): Erinnerungen strukturiert übernehmen
        val withMemories = if (s.schemaVersion < 3) de.protokollnull.agent.engine.Memories.migrate(s) else s
        // Schema 5 (v0.8): bisherige Wohnung (Boolean) und Tarnjob ins Privatleben übernehmen
        val withLife = if (withMemories.schemaVersion < 5) {
            val week = withMemories.clock.day / 7
            withMemories.copy(life = withMemories.life.copy(
                housing = if (withMemories.player.apartment) "small" else withMemories.life.housing,
                lastRentWeek = if (withMemories.player.apartment) week else withMemories.life.lastRentWeek,
                coverJob = withMemories.player.coverJob?.let { "courier" } ?: withMemories.life.coverJob,
                shiftWeek = if (withMemories.player.coverJob != null) week else withMemories.life.shiftWeek
            ))
        } else withMemories
        return withLife.copy(schemaVersion = CURRENT_SCHEMA)
    }

    /** Schema 1 (Foundation) → Schema 2. */
    fun fromV1(old: LegacyGameSave): GameState {
        val p = old.player
        var uid = 1L
        val inv = p.inventory.map { ItemStack(uid++, it.itemId, it.quantity) }
        val relations = p.relationships.mapValues { (_, v) -> Relation(trust = v, friendship = v / 2) }
        val day = p.elapsedDays.coerceAtLeast(0) + (p.ageAtStart - 16).coerceAtLeast(0) * 365
        var flags = old.flags + "migrated_v1"
        val custom = old.partner?.let {
            listOf(CustomNpc(
                id = "custom_1", firstName = it.name, lastName = "", gender = it.gender, avatar = AvatarSpec(),
                traits = listOf(it.personality), background = "Übernommen aus Version 0.1", profession = it.specialization,
                interests = emptyList(), skills = emptyMap(), createdDay = day, ageAtCreation = it.ageAtCreation
            ))
        } ?: emptyList()
        return GameState(
            seed = (p.name.hashCode().toLong() shl 16) xor day.toLong(),
            phase = "game",
            player = PlayerCharacter(
                firstName = p.name, lastName = "", codename = p.name,
                gender = if (p.gender == "male") "male" else "female",
                level = p.level.coerceIn(1, 100), xp = p.xp, skillPoints = p.skillPoints, rank = p.rank.coerceIn(1, 12),
                reputation = p.reputation, credits = p.credits, energy = p.energy, stress = p.stress,
                skills = p.skills, inventory = inv, nextUid = uid
            ),
            clock = GameClock(day = day, slot = 0),
            relations = relations,
            flags = flags + "prologue_done",
            completedMissions = old.completedMissions,
            customNpcs = custom
        )
    }
}

/** 3 manuelle Slots + Autosave, atomar geschrieben (tmp + fsync + rename). */
class SaveRepository(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    val slots = listOf("auto", "slot1", "slot2", "slot3")

    init { dir.mkdirs() }

    private fun file(slot: String) = File(dir, "save_$slot.json")
    private fun infoFile(slot: String) = File(dir, "save_$slot.meta.json")
    private val legacy = File(dir, "protokoll_null_save_v1.json")

    fun exists(slot: String) = file(slot).exists()

    fun load(slot: String): GameState? = runCatching {
        val f = file(slot)
        if (f.exists()) SaveMigration.decode(f.readText()) else null
    }.getOrNull()

    /** Alte Foundation-Saves automatisch in den Autosave übernehmen. */
    fun importLegacyIfPresent(): GameState? {
        if (!legacy.exists() || exists("auto")) return null
        val s = runCatching { SaveMigration.decode(legacy.readText()) }.getOrNull() ?: return null
        save("auto", s, "Übernommener Spielstand")
        legacy.renameTo(File(dir, "protokoll_null_save_v1.migrated"))
        return s
    }

    fun save(slot: String, state: GameState, title: String? = null) {
        val now = System.currentTimeMillis()
        writeAtomic(file(slot), SaveMigration.encode(state.copy(lastAutosave = now)))
        val p = state.player
        val info = SlotInfo(
            slot = slot,
            title = title ?: "${p.codename} · ${p.firstName} ${p.lastName}".trim(),
            subtitle = "Level ${p.level} · Rang ${p.rank} · Tag ${state.clock.day + 1}",
            savedAt = now
        )
        writeAtomic(infoFile(slot), json.encodeToString(info))
    }

    fun info(slot: String): SlotInfo? = runCatching {
        val f = infoFile(slot)
        if (f.exists() && exists(slot)) json.decodeFromString<SlotInfo>(f.readText()) else null
    }.getOrNull()

    fun delete(slot: String) { file(slot).delete(); infoFile(slot).delete() }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

class SettingsStore(dir: File) {
    private val file = File(dir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun load(): Settings = runCatching { json.decodeFromString<Settings>(file.readText()) }.getOrDefault(Settings())
    fun save(s: Settings) { runCatching { file.writeText(json.encodeToString(s)) } }
}

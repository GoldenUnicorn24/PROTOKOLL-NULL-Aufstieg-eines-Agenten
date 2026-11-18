package de.protokollnull.agent.data

import android.content.Context
import de.protokollnull.agent.model.GameSave
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SaveStore(context: Context) {
    private val file = File(context.filesDir, "protokoll_null_save_v1.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): GameSave = runCatching {
        if (file.exists()) json.decodeFromString<GameSave>(file.readText()) else GameSave()
    }.getOrElse { GameSave() }

    fun save(game: GameSave) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(game))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

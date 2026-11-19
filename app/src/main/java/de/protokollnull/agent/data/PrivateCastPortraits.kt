package de.protokollnull.agent.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** User-provided local portraits for the known Nikita cast, never a public repository asset. */
object PrivateCastPortraits {
    private const val PREFS = "private_cast_portraits"
    private val validId = Regex("^[a-z][a-z0-9_]{1,39}$")
    var revision by mutableIntStateOf(0)
        private set

    fun imported(context: Context, npcId: String): File? {
        if (!validId.matches(npcId)) return null
        val imageId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("npc_" + npcId, null)
        return ImportedPortraits.resolve(context, imageId)
    }

    /** Developer-only build overlay: app/src/private/assets/cast/show_<npcId>.jpg */
    fun bundled(context: Context, npcId: String): String? {
        if (!validId.matches(npcId)) return null
        val filename = "show_" + npcId + ".jpg"
        return if (context.assets.list("cast")?.contains(filename) == true) "cast/" + filename else null
    }

    /** Atomically publish an entire validated set after every ZIP image succeeded. */
    fun chooseAll(context: Context, imageIds: Map<String, String>) {
        require(imageIds.isNotEmpty()) { "Das Paket enthält keine Porträts." }
        imageIds.forEach { (npcId, imageId) ->
            require(validId.matches(npcId)) { "Ungültige Agentenkennung." }
            require(ImportedPortraits.resolve(context, imageId) != null) { "Bilddatei fehlt: $npcId" }
        }
        val edits = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        imageIds.forEach { (npcId, imageId) -> edits.putString("npc_" + npcId, imageId) }
        check(edits.commit()) { "Die Zuordnungen konnten nicht gespeichert werden." }
        revision++
    }

    fun clearAll(context: Context, npcIds: Collection<String>) {
        require(npcIds.all(validId::matches)) { "Ungültige Agentenkennung." }
        val edits = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        npcIds.forEach { edits.remove("npc_" + it) }
        check(edits.commit()) { "Die Zuordnungen konnten nicht zurückgesetzt werden." }
        revision++
    }

    fun choose(context: Context, npcId: String, normalizedImageId: String) {
        require(validId.matches(npcId)) { "Ungültige Agentenkennung." }
        require(ImportedPortraits.resolve(context, normalizedImageId) != null) { "Bilddatei fehlt." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("npc_" + npcId, normalizedImageId).apply()
        revision++
    }

    fun clear(context: Context, npcId: String) {
        require(validId.matches(npcId)) { "Ungültige Agentenkennung." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("npc_" + npcId).apply()
        revision++
    }
}

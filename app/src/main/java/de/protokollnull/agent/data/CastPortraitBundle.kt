package de.protokollnull.agent.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** ZIP importer. No network, no writes into assets or savegame JSON. */
internal object CastPortraitBundle {
    private const val MAX_IMAGE = 12 * 1024 * 1024
    private const val MAX_TOTAL = 96 * 1024 * 1024
    private const val MAX_ENTRIES = 60
    private val filename = Regex("^show_([a-z][a-z0-9_]{1,39})\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE)

    data class Result(val imported: List<String>, val skipped: Int)

    suspend fun importZip(context: Context, uri: Uri, allowedIds: Set<String>): Result {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("ZIP-Datei konnte nicht geöffnet werden.")
        return stream.use { importZipStream(context, it, allowedIds) }
    }

    /** Internal for Robolectric tests and stream processing. Commit mappings only on success. */
    suspend fun importZipStream(context: Context, input: InputStream, allowedIds: Set<String>): Result =
        withContext(Dispatchers.IO) {
            require(allowedIds.isNotEmpty()) { "Keine Serienfiguren gefunden." }
            val changes = linkedMapOf<String, String>()
            var entries = 0
            var skipped = 0
            var expanded = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ENTRIES) { "Das ZIP enthält zu viele Dateien." }
                    val path = entry.name.replace('\\', '/')
                    require(!path.startsWith("/") && path.split('/').none { it == ".." || it == "." }) {
                        "Ungültiger Dateipfad im ZIP."
                    }
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val match = filename.matchEntire(path.substringAfterLast('/'))
                    val id = match?.groupValues?.get(1)?.lowercase()
                    if (id == null || id !in allowedIds) {
                        skipped++
                        // Bound ignored entries as well: closeEntry() would otherwise
                        // decompress arbitrarily large unknown files inside ZIP bombs.
                        val sink = ByteArray(32768)
                        var unknownBytes = 0
                        while (true) {
                            val count = zip.read(sink)
                            if (count < 0) break
                            unknownBytes += count
                            expanded += count
                            require(unknownBytes <= MAX_IMAGE && expanded <= MAX_TOTAL) {
                                "Das ZIP enthält zu große Dateien."
                            }
                        }
                        zip.closeEntry()
                        continue
                    }
                    require(id !in changes) { "Porträt doppelt im ZIP: $id" }
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(32768)
                    var fileBytes = 0
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        fileBytes += read
                        expanded += read
                        require(fileBytes <= MAX_IMAGE && expanded <= MAX_TOTAL) { "Das ZIP enthält zu große Bilder." }
                        output.write(buffer, 0, read)
                    }
                    val normalized = ImportedPortraits.importBytes(context, output.toByteArray())
                    changes[id] = normalized
                    zip.closeEntry()
                }
            }
            require(changes.isNotEmpty()) {
                "Keine passenden Bilder gefunden. Erwartet: show_alex.jpg, show_michael.jpg usw."
            }
            PrivateCastPortraits.chooseAll(context, changes)
            Result(changes.keys.toList(), skipped)
        }
}

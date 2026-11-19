package de.protokollnull.agent.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import de.protokollnull.agent.model.GameState
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Saves reference a normalized local image. Replacing a portrait keeps older slots intact. */
internal object ImportedPortraits {
    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val LONG_EDGE = 960
    private val validId = Regex("[a-f0-9]{64}")

    fun resolve(context: Context, id: String?): File? =
        id?.takeIf(validId::matches)?.let { File(context.filesDir, "portraits/$it.jpg").takeIf(File::isFile) }

    private fun references(state: GameState): Set<String> = buildSet {
        state.player.appearance.avatar.portraitId?.let(::add)
        state.pendingCompanion?.avatar?.portraitId?.let(::add)
        state.customNpcs.mapNotNullTo(this) { it.avatar.portraitId }
    }

    fun exportSave(context: Context, state: GameState): String {
        val save = SaveMigration.encode(state)
        val photos = references(state).mapNotNull { id ->
            resolve(context, id)?.let { file ->
                id to JsonPrimitive(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }.toMap()
        if (photos.isEmpty()) return save
        val obj = Json.parseToJsonElement(save).jsonObject
        return JsonObject(obj + ("portraitImages" to JsonObject(photos))).toString()
    }

    fun importSave(context: Context, text: String): GameState {
        val obj = Json.parseToJsonElement(text).jsonObject
        val state = SaveMigration.decode(obj)
        val images = obj["portraitImages"]?.jsonObject ?: return state
        for (id in references(state)) {
            val encoded = images[id]?.jsonPrimitive?.content ?: continue
            require(validId.matches(id) && encoded.length <= 4 * 1024 * 1024)
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(hex(MessageDigest.getInstance("SHA-256").digest(bytes)) == id)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..LONG_EDGE && bounds.outHeight in 1..LONG_EDGE)
            if (resolve(context, id) == null) {
                val directory = File(context.filesDir, "portraits").apply { mkdirs() }
                val pending = File.createTempFile("restore-", ".tmp", directory)
                try {
                    pending.writeBytes(bytes)
                    install(pending, id)
                } finally {
                    pending.delete()
                }
            }
        }
        return state
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun install(staged: File, id: String) {
        Files.move(
            staged.toPath(), File(staged.parentFile, "$id.jpg").toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
        )
    }

    suspend fun importPortrait(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val source = File.createTempFile("portrait-", ".source", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Bild nicht lesbar.")
            input.use { stream ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(32768)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "Das Bild ist größer als 32 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            normalize(context, source)
        } finally {
            source.delete()
        }
    }

    private fun normalize(context: Context, source: File): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Dieses Bildformat kann nicht gelesen werden." }
        val edge = maxOf(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (edge / options.inSampleSize > LONG_EDGE * 2) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(source.path, options) ?: throw IOException("Bild beschädigt.")
        val orientation = try {
            ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
            val scale = (LONG_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            if ((minOf(bitmap.width, bitmap.height) * scale).roundToInt() < 1) {
                throw IllegalArgumentException("Das Bild ist zu schmal für ein Porträt.")
            }
            postScale(scale, scale)
        }
        val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
        if (normalized !== bitmap) bitmap.recycle()
        val directory = File(context.filesDir, "portraits").apply { mkdirs() }
        val output = File.createTempFile("portrait-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestOutputStream(output.outputStream(), digest).use {
                if (!normalized.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw IOException("Speichern fehlgeschlagen.")
            }
            val id = hex(digest.digest())
            install(output, id)
            return id
        } finally {
            normalized.recycle()
            output.delete()
        }
    }
}

package de.protokollnull.agent

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import de.protokollnull.agent.data.CastPortraitBundle
import de.protokollnull.agent.data.PrivateCastPortraits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class CastPortraitBundleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ids = setOf("alex", "michael", "nikita")
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(10, 12, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        bitmap.recycle()
        return out.toByteArray()
    }
    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { z ->
            files.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun oneZipImportsMultipleNamedPhotos() = runBlocking {
        PrivateCastPortraits.clearAll(context, ids)
        try {
            val bytes = png()
            val pack = zip("cast/show_alex.png" to bytes, "show_michael.jpg" to bytes, "README.txt" to byteArrayOf(1))
            val result = CastPortraitBundle.importZipStream(context, ByteArrayInputStream(pack), ids)
            assertEquals(listOf("alex", "michael"), result.imported)
            assertEquals(1, result.skipped)
            assertNotNull(PrivateCastPortraits.imported(context, "alex"))
            assertNotNull(PrivateCastPortraits.imported(context, "michael"))
            assertNull(PrivateCastPortraits.imported(context, "nikita"))
            PrivateCastPortraits.clearAll(context, ids)
            assertNull(PrivateCastPortraits.imported(context, "alex"))
        } finally { PrivateCastPortraits.clearAll(context, ids) }
    }

    @Test fun invalidImageDoesNotReplaceExistingMapping() = runBlocking {
        PrivateCastPortraits.clearAll(context, ids)
        try {
            CastPortraitBundle.importZipStream(context, ByteArrayInputStream(zip("show_alex.png" to png())), ids)
            val original = PrivateCastPortraits.imported(context, "alex")
            val bad = zip("show_michael.jpg" to png(), "show_alex.png" to "not an image".toByteArray())
            try {
                CastPortraitBundle.importZipStream(context, ByteArrayInputStream(bad), ids)
                fail("Corrupted image must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(original, PrivateCastPortraits.imported(context, "alex"))
            assertNull("No partial mapping updates", PrivateCastPortraits.imported(context, "michael"))
        } finally { PrivateCastPortraits.clearAll(context, ids) }
    }

    @Test fun rejectsDuplicateIdentityWithinOnePackage() = runBlocking {
        val bytes = png()
        try {
            CastPortraitBundle.importZipStream(context,
                ByteArrayInputStream(zip("show_alex.png" to bytes, "cast/show_alex.jpg" to bytes)), ids)
            fail("Duplicate identity must fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsTraversalInZipEntry() = runBlocking {
        try {
            CastPortraitBundle.importZipStream(context,
                ByteArrayInputStream(zip("../show_alex.jpg" to png())), ids)
            fail("Path traversal must fail")
        } catch (_: IllegalArgumentException) { }
    }
}

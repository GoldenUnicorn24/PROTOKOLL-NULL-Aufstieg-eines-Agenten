package de.protokollnull.agent

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import de.protokollnull.agent.data.PrivateCastPortraits
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class PrivateCastPortraitsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun localPortraitMayBeSetAndRestoredWithoutTouchingAssets() {
        val image = Bitmap.createBitmap(6, 8, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 90, output))
        image.recycle()
        val bytes = output.toByteArray()
        val id = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val file = File(context.filesDir, "portraits/" + id + ".jpg")
        file.parentFile!!.mkdirs()
        file.writeBytes(bytes)
        try {
            PrivateCastPortraits.clear(context, "alex")
            assertNull(PrivateCastPortraits.imported(context, "alex"))
            PrivateCastPortraits.choose(context, "alex", id)
            assertEquals(file.canonicalFile, PrivateCastPortraits.imported(context, "alex")?.canonicalFile)
            PrivateCastPortraits.clear(context, "alex")
            assertNull(PrivateCastPortraits.imported(context, "alex"))
        } finally {
            PrivateCastPortraits.clear(context, "alex")
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun importedImageMustActuallyExist() {
        PrivateCastPortraits.choose(context, "alex", "0".repeat(64))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathTraversalIsNotACharacterIdentifier() {
        PrivateCastPortraits.clear(context, "../alex")
    }
}

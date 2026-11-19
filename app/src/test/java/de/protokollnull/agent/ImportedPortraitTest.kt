package de.protokollnull.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.protokollnull.agent.data.ImportedPortraits
import de.protokollnull.agent.data.SaveRepository
import de.protokollnull.agent.model.Appearance
import de.protokollnull.agent.model.AvatarSpec
import de.protokollnull.agent.model.GameState
import de.protokollnull.agent.model.PlayerCharacter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImportedPortraitTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun photoSurvivesSourceRemovalAndSlotReloadWithCorrectOrientation() = runBlocking {
        val source = File.createTempFile("camera-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        bitmap.recycle()
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val id = ImportedPortraits.importPortrait(context, Uri.fromFile(source))
        val saves = SaveRepository(File(context.filesDir, "test-saves"))
        val original = GameState(player = PlayerCharacter(appearance = Appearance(avatar = AvatarSpec(portraitId = id))))
        saves.save("slot1", original)
        assertTrue(source.delete())
        saves.save("auto", GameState())

        val savedId = saves.load("slot1")!!.player.appearance.avatar.portraitId
        assertEquals(id, savedId)
        val image = BitmapFactory.decodeFile(ImportedPortraits.resolve(context, savedId)!!.path)
        assertEquals(480, image.width)
        assertEquals(960, image.height)
        image.recycle()

        val export = ImportedPortraits.exportSave(context, original)
        assertTrue(ImportedPortraits.resolve(context, id)!!.delete())
        val imported = ImportedPortraits.importSave(context, export)
        assertEquals(original, imported)
        assertTrue(ImportedPortraits.resolve(context, id)!!.length() > 0)

    }

    @Test
    fun invalidSelectionCreatesNoPortrait() = runBlocking {
        val source = File.createTempFile("broken-", ".jpg", context.cacheDir)
        source.writeText("Dies ist kein Bild.")
        val before = File(context.filesDir, "portraits").listFiles()?.toSet().orEmpty()
        try {
            ImportedPortraits.importPortrait(context, Uri.fromFile(source))
            fail("Beschädigtes Bild akzeptiert")
        } catch (_: IllegalArgumentException) {
            assertEquals(before, File(context.filesDir, "portraits").listFiles()?.toSet().orEmpty())
        }
        assertNull(ImportedPortraits.resolve(context, "../save_auto.json"))
    }
}

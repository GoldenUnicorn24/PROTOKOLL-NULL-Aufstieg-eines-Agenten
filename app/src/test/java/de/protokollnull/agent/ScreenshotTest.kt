package de.protokollnull.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.AgentApp
import de.protokollnull.agent.ui.GameViewModel
import de.protokollnull.agent.ui.Overlay
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendert alle Kernscreens mit echtem Spielzustand (Smoke-Test gegen Abstürze).
 * Mit `./gradlew recordRoborazziDebug` werden die Bilder nach docs/screenshots geschrieben.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test
    fun coreScreens() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GameViewModel(app)
        vm.updateSettings { it.copy(animations = false, sfxVolume = 0) }
        compose.setContent { AgentApp(vm) }
        shot("01_start")

        vm.newGame(); shot("02_charaktereditor")
        vm.startGame(PlayerCharacter(firstName = "Lena", lastName = "Berg", codename = "Null", gender = "female",
            appearance = Appearance(AvatarSpec(skin = 1, hairStyle = 4, hairColor = 1, outfit = 0)), background = "analyst"))
        shot("03_prolog")
        vm.act { d, s -> Prologue.choose(d, s, "watch") }
        vm.act { d, s -> Prologue.skip(d, s) }
        vm.dismissToast(); shot("04_dialog_michael")
        vm.act(silent = true) { d, s -> Dialogues.choose(d, s, "quiet") }
        vm.act(silent = true) { d, s -> Dialogues.choose(d, s, "ok") }
        vm.dismissToast(); shot("05_hq_lageplan")

        vm.setTab(1); shot("06_einsatzboard")
        vm.open(Overlay.Briefing("M001")); shot("07_briefing")
        vm.act { d, s -> Missions.start(d, s, "M001", "balanced", emptyList()) }
        vm.closeOverlays(); vm.dismissToast(); shot("08_mission_entscheidung")
        vm.act(silent = true) { d, s -> Missions.choose(d, s, "help") }
        vm.dismissToast(); shot("09_taktikbrett")
        vm.act(silent = true) { d, s -> Missions.updateTactical(d, s) { t -> Tactical.endTurn(t) } }
        vm.update { it.copy(activeMission = it.activeMission?.copy(result = "success")) }
        shot("10_nachbesprechung")
        vm.act { d, s -> Missions.finish(d, s) }

        vm.update { it.copy(flags = it.flags + "talked_birkhoff_file") }
        vm.act { d, s -> Missions.start(d, s, "M002", "stealth", emptyList()) }
        vm.act(silent = true) { d, s -> Missions.search(d, s, 0) }
        vm.act(silent = true) { d, s -> Missions.search(d, s, 4) }
        vm.dismissToast(); shot("11_untersuchung")
        vm.update { it.copy(activeMission = null) }

        vm.update { s -> Inventory.add(vm.data, s, "medkit", 2).state }
        vm.update { s -> Inventory.add(vm.data, s, "camera", 1).state }
        vm.setTab(2); vm.dismissToast(); shot("12_inventar")
        val st = vm.state.value!!.player.inventory.first { it.itemId == "camera" }
        vm.open(Overlay.Item(st.uid)); shot("13_gegenstand")
        vm.closeOverlays(); vm.open(Overlay.Shop); shot("14_ausruestungskammer")

        vm.setTab(3); shot("15_kontakte")
        vm.open(Overlay.Npc("michael")); shot("16_personalakte")
        vm.setTab(4); shot("17_profil")
        vm.open(Overlay.Skills); shot("18_skilltree")
        vm.closeOverlays(); vm.open(Overlay.Ranks); shot("19_raenge")
        vm.closeOverlays(); vm.open(Overlay.Calendar); shot("20_kalender")

        vm.act { d, s -> World.timeSkip(d, s, 730) }
        vm.dismissToast()
        vm.closeOverlays(); vm.open(Overlay.Recruit); shot("21_rekrutierungseditor")
        vm.update { it.copy(player = it.player.copy(rank = 12), director = DirectorState()) }
        vm.closeOverlays(); vm.open(Overlay.Director); shot("22_direktorat")
        vm.closeOverlays(); vm.open(Overlay.Saves); shot("23_spielstaende")
        assertNotNull(vm.state.value)
    }
}

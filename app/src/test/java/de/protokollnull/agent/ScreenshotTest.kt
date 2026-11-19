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
import coil.ImageLoader
import coil.decode.SvgDecoder
import kotlinx.coroutines.Dispatchers
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
        vm.updateSettings { it.copy(animations = false, sfxVolume = 0, musicVolume = 0) }
        val loader = ImageLoader.Builder(app).components { add(SvgDecoder.Factory()) }
            .dispatcher(Dispatchers.Unconfined).allowHardware(false).crossfade(false).build()
        compose.setContent { AgentApp(vm, loader) }
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

        // v0.5: Zeitplan-Bericht, Karriere, Trainingsplaner, Prüfung, Ausrüstungsprofile
        vm.closeOverlays(); compose.waitForIdle()
        vm.act { d, s -> Simulation.run(d, s, 30) }
        vm.dismissToast(); vm.open(Overlay.Calendar); shot("25_zeitplan_bericht")
        vm.closeOverlays(); vm.open(Overlay.Ranks); shot("26_karriere")
        vm.update { it.copy(location = "training", clock = GameClock(it.clock.day + (8 - Clock.weekday(it.clock.day)) % 7, 1)) }
        vm.closeOverlays(); vm.open(Overlay.Training); shot("27_trainingsplaner")
        vm.closeOverlays()
        vm.update { it.copy(activeExam = ExamRun(rank = 2, phase = 2, score = 41, notes = listOf("Theorie: bestanden (31/58) → +31", "Praxis: schwach (70/52) → +10"))) }
        shot("28_pruefung")
        vm.update { it.copy(activeExam = null) }
        vm.update { s -> Inventory.saveLoadout(vm.data, s, "recon").state }
        vm.setTab(2); vm.dismissToast(); shot("29_ausruestungsprofile")
        vm.act { d, s -> World.timeSkip(d, s, 730) }
        vm.dismissToast()
        // v0.6: Living Division – Status, Personalakte, Beziehung, Umfeld, HQ-Ereignis
        vm.update { s ->
            var x = Relations.update(vm.data, s, "alex") { it.copy(friendship = 62, trust = 58, memories = it.memories + "Morgens vor allen anderen am Schießstand" + "Ein geteilter Kopfhörer im Aufenthaltsraum") }
            x = Bonds.addMilestone(vm.data, x, "alex", "friends")
            x.copy(flags = x.flags + "lf_alex_promoted", living = x.living.copy(pendingHqEvent = "hq_alex_promoted", pendingHqEventDay = x.clock.day))
        }
        vm.closeOverlays(); vm.setTab(3); shot("30_kontakte_status")
        vm.open(Overlay.Npc("alex", "career")); shot("31_personalakte_laufbahn")
        vm.closeOverlays(); vm.open(Overlay.Npc("alex", "relation")); shot("32_beziehung_erinnerungen")
        vm.closeOverlays(); vm.open(Overlay.Npc("alex", "circle")); shot("33_umfeld")
        vm.closeOverlays(); vm.setTab(0); shot("34_hq_division_heute")
        vm.open(Overlay.HqEvent); shot("35_hq_ereignis")
        vm.closeOverlays(); vm.open(Overlay.Recruit); shot("21_rekrutierungseditor")
        vm.update { it.copy(player = it.player.copy(rank = 12), director = DirectorState()) }
        vm.closeOverlays(); vm.open(Overlay.Director); shot("22_direktorat")
        vm.closeOverlays(); vm.open(Overlay.Saves); shot("23_spielstaende")
        vm.closeOverlays()
        vm.update { it.copy(flags = it.flags + setOf("ending_order", "voss_arrested", "kira_rescued", "nikita_ally")) }
        shot("24_epilog")
        assertNotNull(vm.state.value)
    }
}

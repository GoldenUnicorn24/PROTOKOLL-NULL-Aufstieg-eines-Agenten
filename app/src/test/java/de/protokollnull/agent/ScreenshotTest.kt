package de.protokollnull.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
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

    private var vmRef: GameViewModel? = null

    private fun shot(name: String, keepCelebration: Boolean = false) {
        // Feier-Einblendungen (Level/Rang) würden die Kernscreens verdecken
        if (!keepCelebration) vmRef?.dismissCelebration()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test
    fun coreScreens() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GameViewModel(app)
        vmRef = vm
        vm.updateSettings { it.copy(animations = false, sfxVolume = 0, musicVolume = 0) }
        val loader = ImageLoader.Builder(app).components { add(SvgDecoder.Factory()) }
            .dispatcher(Dispatchers.Unconfined).allowHardware(false).crossfade(false).build()
        compose.setContent { AgentApp(vm, loader) }
        shot("01_start")
        // Prevent stale hard-coded version captions on the start screen.
        compose.onNodeWithText("Privater Fan-Prototyp im Nikita-Universum · offline spielbar · v${BuildConfig.VERSION_NAME}").assertExists()

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
        // v0.7: Shadow Operations – Mission Control, Weltlage, Ketten, Akte, Auftragsbriefing, Taktik 2.0
        vm.update { s ->
            var x = Cases.daily(vm.data, s.copy(living = s.living.copy(pendingHqEvent = null)))
            x = Ops.refresh(vm.data, x)
            listOf("hm1", "hm2", "hm4", "hm7").forEach { x = Cases.addClue(x, it, "Observation „Kranich“").first }
            Ops.worldLog(Ops.shiftInfluence(x, "syndicate", -8), "„Datenauswertung: Basalt“ erfüllt – Black Meridian verliert Boden im Bereich Industriehafen.")
        }
        vm.closeOverlays(); vm.setTab(1); shot("36_mission_control")
        vm.open(Overlay.MissionControl("world")); shot("37_weltlage")
        vm.closeOverlays(); vm.open(Overlay.MissionControl("chains")); shot("38_einsatzketten")
        vm.closeOverlays(); vm.open(Overlay.Case("harbor_mole")); shot("39_ermittlungsakte")
        val rescue = Ops.generate(vm.data, vm.state.value!!, vm.state.value!!.clock.day, 990, 4242L, forced = Ops.archetypeById.getValue("rescue"))!!.copy(mapId = "embassy", complicationId = "none")
        vm.update { it.copy(ops = it.ops.copy(contracts = it.ops.contracts + rescue)) }
        vm.closeOverlays(); vm.open(Overlay.Briefing(rescue.id)); shot("40_auftrag_briefing")
        vm.update { s ->
            val def = Ops.toMission(vm.data, s, rescue)
            val tac = def.stages.first { it.id == "tac" }
            s.copy(activeMission = MissionRun(rescue.id, 1L, "tac", generated = def, tactical = Tactical.setup(vm.data, s, tac.tactical!!, emptyList(), 1L, rescue.difficulty)))
        }
        vm.closeOverlays(); shot("41_taktik_botschaft")
        vm.update { it.copy(activeMission = null, ops = it.ops.copy(contracts = it.ops.contracts.filter { c -> c.id != rescue.id })) }
        vm.closeOverlays(); vm.open(Overlay.Recruit); shot("21_rekrutierungseditor")
        vm.update { it.copy(player = it.player.copy(rank = 12), director = DirectorState()) }
        vm.closeOverlays(); vm.open(Overlay.Director); shot("22_direktorat")
        // v0.8: Director's Legacy – Führungscockpit, Delegation, Standorte, Dilemma, Berichte, Privatleben, Personalakte 2.0
        vm.update { s0 ->
            var x = s0.copy(team = listOf("alex", "birkhoff", "thom"), player = s0.player.copy(credits = 8000, energy = 100))
            listOf("alex", "birkhoff", "thom", "sonya", "owen").forEach { id ->
                x = x.copy(living = x.living.copy(lives = x.living.lives + (id to Life.life(vm.data, x, id).copy(status = LifeStatus.DUTY, statusUntil = -1, rank = 5, mood = 80, stress = 15))))
                x = Relations.update(vm.data, x, id) { it.copy(loyalty = 90, trust = 75) }
            }
            x = Command.setRole(vm.data, x, "alex", "support").state
            x = Command.setRole(vm.data, x, "birkhoff", "tech").state
            x = Command.setRole(vm.data, x, "thom", "recon").state
            x.copy(lead = x.lead.copy(cohesion = 64, teamWins = 7, teamLosses = 1))
        }
        vm.closeOverlays(); vm.open(Overlay.Command("team")); shot("42_fuehrungscockpit")
        vm.update { s0 ->
            var x = Ops.refresh(vm.data, s0)
            val c = Ops.generate(vm.data, x, x.clock.day, 991, 5151L, forced = Ops.archetypeById.getValue("data"))!!.copy(id = "SHOT_DEL", difficulty = 2, complicationId = "none", chainId = null, caseId = null, maxTeam = 2)
            x = x.copy(ops = x.ops.copy(contracts = x.ops.contracts + c))
            x = Delegations.prepare(vm.data, x, c.id, listOf("sonya", "owen"), "owen").state
            x
        }
        vm.closeOverlays(); vm.open(Overlay.Command("delegation")); shot("43_delegation")
        vm.update { s0 ->
            var x = Directorate.earn(s0, 20000)
            x = Sites.build(vm.data, x, "safehouse").state
            x = Sites.build(vm.data, x, "analysis").state
            x = x.copy(lead = x.lead.copy(sites = x.lead.sites + ("safehouse" to x.lead.sites.getValue("safehouse").copy(staff = listOf("sonya")))))
            x.copy(lead = x.lead.copy(hqModules = mapOf("training_hall" to 2, "lounge" to 1)))
        }
        vm.closeOverlays(); vm.open(Overlay.Command("sites")); shot("44_standorte")
        vm.update { it.copy(lead = it.lead.copy(pendingDilemma = "leak", dilemmaDay = it.clock.day)) }
        vm.closeOverlays(); vm.open(Overlay.Director); shot("45_direktorat_dilemma")
        vm.update { s0 -> val w = Directorate.weekly(vm.data, s0.copy(lead = s0.lead.copy(pendingDilemma = null, lastFinanceWeek = -1))).state; w.copy(lead = w.lead.copy(pendingDilemma = null)) }
        vm.closeOverlays(); vm.open(Overlay.Command("reports")); shot("46_wochenbericht")
        vm.update { s0 ->
            var x = Home.move(vm.data, s0.copy(player = s0.player.copy(credits = 9000)), "upscale").state
            listOf("bed", "music", "plants", "desk", "table").forEach { f -> x = Home.buyFurniture(vm.data, x, f).state }
            x.copy(life = x.life.copy(hobbies = mapOf("Fotografie" to 3, "Schach" to 2), pendingEvent = null))
        }
        vm.closeOverlays(); vm.open(Overlay.City("home")); shot("47_stadt_privatleben")
        vm.update { it.copy(life = it.life.copy(pendingEvent = Home.events.first { e -> e.npcId == null && e.choices.none { c -> c.needsFurniture != null } }.id, eventDay = it.clock.day)) }
        vm.closeOverlays(); vm.open(Overlay.City("event")); shot("48_alltagsereignis")
        vm.update { it.copy(life = it.life.copy(pendingEvent = null)) }
        vm.closeOverlays(); vm.open(Overlay.Npc("thom", "duty")); shot("49_personalakte_dienst")
        // v1.0: Taktik 3.0 mit Team, Direktionsagenda, Kapitel 4, Wendung, Beförderung, neues Ende
        vm.update { s0 ->
            val setup = TacticalSetup(emptyList(), mapId = "lab", objective = "extract", enemyName = "Wachdienst", enemyHp = 6, enemyAccuracy = 58, vipName = "Kandidat 8")
            s0.copy(activeMission = MissionRun("M032", 3L, "s2", tactical = Tactical.setup(vm.data, s0, setup, listOf("alex", "birkhoff"), 3L, 3)))
        }
        vm.closeOverlays(); shot("50_taktik3_labor")
        vm.update { it.copy(activeMission = null) }
        vm.update { s0 -> Agenda.choose(vm.data, Directorate.ensure(vm.data, s0), "people").state }
        vm.closeOverlays(); vm.open(Overlay.DirectorView("agenda")); shot("51_direktion_agenda")
        vm.update { it.copy(flags = it.flags + setOf("epilogue_seen", "ending_order", "voss_resolved"), completedDialogues = it.completedDialogues + "dlg_final" + "dlg_ch4_intro") }
        vm.closeOverlays(); vm.open(Overlay.Briefing("M030")); shot("52_kapitel4_briefing")
        vm.update { s0 ->
            val base = Ops.refresh(vm.data, s0)
            val c = (1L..60L).asSequence().mapNotNull { seed -> Ops.generate(vm.data, base, base.clock.day, 992, seed, forced = Ops.archetypeById.getValue("intel"))?.copy(id = "SHOT_TWIST$seed", complicationId = "none", chainId = null, caseId = null, difficulty = 3) }
                .first { Twists.forContract(it) != null }
            val def = Ops.toMission(vm.data, base, c)
            base.copy(ops = base.ops.copy(contracts = base.ops.contracts + c), activeMission = MissionRun(c.id, 1L, "twist", generated = def))
        }
        vm.closeOverlays(); shot("53_wendung")
        vm.update { it.copy(activeMission = null, flags = it.flags - setOf("epilogue_seen", "ending_order")) }
        vm.update { it.copy(player = it.player.copy(rank = 11)) }
        vm.act { d, s0 -> Result(Progression.applyPromotion(d, s0, 12)) }
        vm.dismissToast(); shot("54_feier_befoerderung", keepCelebration = true)
        vm.dismissCelebration()
        vm.closeOverlays(); vm.open(Overlay.Saves); shot("23_spielstaende")
        vm.closeOverlays()
        vm.update { it.copy(flags = it.flags + setOf("ending_order", "voss_arrested", "kira_rescued", "nikita_ally")) }
        shot("24_epilog")
        vm.update { it.copy(flags = it.flags - "ending_order" + setOf("ending_academy", "academy_founded", "michael_reckoning", "alex_shadow_done", "alex_ledger_burned")) }
        shot("55_epilog_akademie")
        assertNotNull(vm.state.value)
    }
}

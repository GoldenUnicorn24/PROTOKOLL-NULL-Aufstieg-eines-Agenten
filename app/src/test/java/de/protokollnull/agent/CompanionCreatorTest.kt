package de.protokollnull.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.protokollnull.agent.engine.CompanionAssignment
import de.protokollnull.agent.engine.Prologue
import de.protokollnull.agent.engine.World
import de.protokollnull.agent.ui.AgentApp
import de.protokollnull.agent.ui.GameViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp")
class CompanionCreatorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun quickStartWithDossierLeadsToMichaelsAssignment() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.updateSettings { it.copy(animations = false, sfxVolume = 0, musicVolume = 0) }
        vm.newGame()
        compose.setContent { AgentApp(vm) }
        compose.onNodeWithText("Foto vom Gerät wählen").assertExists()
        compose.onNodeWithText("Einsatzprofil übernehmen und zur Gefährtenwahl").performClick()
        compose.onNodeWithText("Personalakte anlegen").performClick()
        compose.onNodeWithText("Foto vom Gerät wählen").assertExists()
        compose.onNodeWithText("Prolog beginnen").performClick()
        compose.runOnIdle {
            assertNotNull(vm.state.value!!.pendingCompanion)
            assertTrue(vm.state.value!!.customNpcs.isEmpty())
            vm.act { data, state -> Prologue.skip(data, state) }
            vm.update { it.copy(activeDialogue = null) }
            vm.act { data, state -> World.timeSkip(data, state, 730) }
            vm.dismissToast()
            // v1.0: Levelaufstieg-Einblendung liegt über allem und wird per Tippen geschlossen
            vm.dismissCelebration()
        }
        compose.onNodeWithText("Die zweite Unterschrift").assertExists()
        compose.onNodeWithText("„Komm. Ich zeige dir meinen Platz am Fenster.“").performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(CompanionAssignment.awaitingIntroduction(vm.state.value!!))
            assertEquals("Mira", vm.state.value!!.customNpcs.single().firstName)
        }
    }
}

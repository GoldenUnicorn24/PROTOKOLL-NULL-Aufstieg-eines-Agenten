package de.protokollnull.agent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import de.protokollnull.agent.audio.AmbientMusic
import de.protokollnull.agent.ui.AgentApp
import de.protokollnull.agent.ui.GameViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private val music = AmbientMusic()
    private var visible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.parseColor("#F0060C17"))
        )
        super.onCreate(savedInstanceState)
        setContent { AgentApp(vm) }
        lifecycleScope.launch {
            vm.settings.collect { s ->
                music.setVolume(s.musicVolume)
                if (visible) { if (s.musicVolume > 0) music.start(s.musicVolume) else music.stop() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        music.start(vm.settings.value.musicVolume)
    }

    override fun onStop() {
        visible = false
        music.stop()
        super.onStop()
    }
}

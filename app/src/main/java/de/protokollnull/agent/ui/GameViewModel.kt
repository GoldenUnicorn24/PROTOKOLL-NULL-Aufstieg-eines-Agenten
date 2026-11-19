package de.protokollnull.agent.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.data.ImportedPortraits
import de.protokollnull.agent.data.SaveRepository
import de.protokollnull.agent.data.SettingsStore
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Overlay {
    data object Shop : Overlay
    data object Storage : Overlay
    data object Skills : Overlay
    data object Ranks : Overlay
    data object Quests : Overlay
    data object Calendar : Overlay
    data object Recruit : Overlay
    data object Team : Overlay
    data object Director : Overlay
    data object Settings : Overlay
    data object Saves : Overlay
    data object Credits : Overlay
    data object Training : Overlay
    data object Reports : Overlay
    data object HqEvent : Overlay
    data class Case(val id: String) : Overlay
    data class MissionControl(val tab: String) : Overlay
    data class Npc(val id: String, val tab: String = "profile") : Overlay
    data class Briefing(val missionId: String) : Overlay
    data class Item(val uid: Long) : Overlay
    data class Command(val tab: String = "team") : Overlay
    data class City(val tab: String = "home") : Overlay
}

data class Toast(val id: Long, val lines: List<String>, val error: Boolean)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    val data: GameData = GameData.load(
        read = { path -> app.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } },
        assetFiles = listAssets(app)
    )
    private val saves = SaveRepository(app.filesDir)
    private val settingsStore = SettingsStore(app.filesDir)

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _screen = MutableStateFlow("start")
    val screen: StateFlow<String> = _screen.asStateFlow()

    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
    val overlays: StateFlow<List<Overlay>> = _overlays.asStateFlow()

    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var toastCounter = 0L
    private var tone: ToneGenerator? = null

    init {
        saves.importLegacyIfPresent()
    }

    private fun listAssets(app: Application): Set<String> {
        val out = mutableSetOf<String>()
        listOf("cast", "items", "game", "licenses").forEach { dir ->
            runCatching { app.assets.list(dir)?.forEach { out += "$dir/$it" } }
        }
        return out
    }

    // --- Navigation ---------------------------------------------------------

    fun setTab(i: Int) { _tab.value = i; _overlays.value = emptyList() }
    fun open(o: Overlay) { _overlays.value = _overlays.value + o }
    fun back(): Boolean {
        val list = _overlays.value
        if (list.isNotEmpty()) { _overlays.value = list.dropLast(1); return true }
        return false
    }
    fun closeOverlays() { _overlays.value = emptyList() }
    fun dismissToast() { _toast.value = null }

    fun goToStart() {
        _state.value?.let { saves.save("auto", it) }
        _screen.value = "start"
        _overlays.value = emptyList()
    }

    // --- Spielstände --------------------------------------------------------

    fun slotInfo(slot: String) = saves.info(slot)
    fun peek(slot: String): GameState? = saves.load(slot)
    fun hasAutosave() = saves.exists("auto")

    fun newGame() { _screen.value = "creator"; _overlays.value = emptyList() }

    fun startGame(player: PlayerCharacter, companion: CustomNpc? = null) {
        val seed = System.nanoTime() xor player.firstName.hashCode().toLong()
        val base = GameState(seed = seed, player = player, pendingCompanion = companion)
        val withAttrs = applyBackground(base)
        _state.value = withAttrs
        _tab.value = 0
        _screen.value = "game"
        persist()
    }

    /** Hintergrund, Stärke, Schwäche und Schwierigkeit setzen die Startwerte. */
    private fun applyBackground(s: GameState): GameState {
        val p = s.player
        val attrs = p.attributes.toMutableMap()
        when (p.background) {
            "athlete" -> { attrs[Attr.FITNESS] = 38; attrs[Attr.WILLPOWER] = 34 }
            "analyst" -> { attrs[Attr.INTELLECT] = 38; attrs[Attr.PERCEPTION] = 34 }
            "social" -> { attrs[Attr.CHARISMA] = 38; attrs[Attr.WILLPOWER] = 33 }
            "marksman" -> { attrs[Attr.PRECISION] = 38; attrs[Attr.PERCEPTION] = 33 }
        }
        attrs[p.strength] = (attrs[p.strength] ?: 30) + 6
        attrs[p.weakness] = (attrs[p.weakness] ?: 30) - 8
        val credits = when (p.difficulty) { "easy" -> 600; "hard" -> 150; else -> 300 }
        val skills = when (p.background) {
            "athlete" -> mapOf("stamina" to 1)
            "analyst" -> mapOf("analysis" to 1)
            "social" -> mapOf("empathy" to 1)
            else -> mapOf("defense" to 1)
        }
        return s.copy(player = p.copy(attributes = attrs.mapValues { it.value.coerceIn(10, 60) }, credits = credits, skills = skills))
    }

    fun load(slot: String) {
        val s = saves.load(slot)
        if (s == null) { show(listOf("Spielstand konnte nicht geladen werden."), true); return }
        _state.value = MissionGenerator.refreshBoard(data, s)
        _screen.value = "game"
        _overlays.value = emptyList()
        _tab.value = 0
        show(listOf("Spielstand geladen: " + (saves.info(slot)?.title ?: slot)), false)
    }

    fun saveTo(slot: String) {
        val s = _state.value ?: return
        saves.save(slot, s)
        show(listOf("Gespeichert in " + slotName(slot)), false)
    }

    fun deleteSlot(slot: String) { saves.delete(slot) }

    fun slotName(slot: String) = when (slot) { "auto" -> "Autosave"; "slot1" -> "Slot 1"; "slot2" -> "Slot 2"; else -> "Slot 3" }

    fun exportTo(uri: Uri) {
        val s = _state.value ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error("Datei nicht beschreibbar")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(ImportedPortraits.exportSave(app, s)) }
                }
                show(listOf("Spielstand exportiert."), false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                show(listOf("Export fehlgeschlagen."), true)
            }
        }
    }

    fun importFrom(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri) ?: error("Datei nicht lesbar")
                    val text = input.bufferedReader().use { it.readText() }
                    ImportedPortraits.importSave(app, text)
                }
                _state.value = s
                _screen.value = "game"
                _overlays.value = emptyList()
                persist()
                show(listOf("Spielstand importiert (Schema ${s.schemaVersion})."), false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                show(listOf("Import fehlgeschlagen: Datei ungültig."), true)
            }
        }
    }

    private fun persist() { _state.value?.let { runCatching { saves.save("auto", it) } } }

    // --- Einstellungen ------------------------------------------------------

    fun updateSettings(f: (Settings) -> Settings) {
        val n = f(_settings.value)
        _settings.value = n
        settingsStore.save(n)
    }

    private fun beep(error: Boolean) {
        val vol = _settings.value.sfxVolume
        if (vol <= 0) return
        runCatching {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, vol.coerceIn(0, 100))
            tone?.startTone(if (error) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_BEEP, 60)
        }
    }

    override fun onCleared() { runCatching { tone?.release() }; super.onCleared() }

    // --- Aktionen -------------------------------------------------------------

    private fun show(lines: List<String>, error: Boolean) {
        if (lines.isEmpty()) return
        _toast.value = Toast(++toastCounter, lines.take(6), error)
    }

    /** Führt eine Engine-Aktion aus, speichert automatisch und zeigt Meldungen. */
    fun act(silent: Boolean = false, f: (GameData, GameState) -> Result) {
        val s = _state.value ?: return
        val r = runCatching { f(data, s) }.getOrElse { e -> Result(s, listOf("Interner Fehler: ${e.message}"), ok = false) }
        var next = r.state
        if (r.ok) {
            val q = Quests.update(data, next)
            next = q.state
            val msgs = r.messages + q.messages
            if (msgs.isNotEmpty()) {
                val day = next.clock.day; val slot = next.clock.slot
                next = next.copy(log = (next.log + msgs.filter { !it.startsWith("—") }.take(4).map { LogEntry(day, slot, it) }).takeLast(80))
            }
            _state.value = next
            if (!silent || q.messages.isNotEmpty()) show(msgs, false)
            if (msgs.isNotEmpty()) beep(false)
        } else {
            _state.value = next
            show(r.messages, true)
            beep(true)
        }
        persist()
    }

    fun update(f: (GameState) -> GameState) { _state.value = _state.value?.let(f); persist() }
}

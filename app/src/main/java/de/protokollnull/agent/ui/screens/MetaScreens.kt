package de.protokollnull.agent.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import de.protokollnull.agent.BuildConfig
import de.protokollnull.agent.ui.CastPortraitSettings
import de.protokollnull.agent.ui.GameViewModel
import de.protokollnull.agent.ui.Overlay
import de.protokollnull.agent.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StartScreen(vm: GameViewModel) {
    val auto = remember { vm.slotInfo("auto") }
    val autoState = remember { if (auto != null) vm.peek("auto") else null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Kinoartiger Kopfbereich bis unter die Statusleiste
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            de.protokollnull.agent.ui.ScenePanel("hq", Modifier.fillMaxSize(), "", "", cover = true)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x99060C17), Color.Transparent, Color(0xFF060C17)))))
            RadarEmblem(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(64.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("AUFSTIEG EINES AGENTEN", style = PnType.overline.copy(fontSize = 12.sp), color = Pn.Accent)
                Text("PROTOKOLL NULL", style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 46.sp), color = Pn.Text)
                Text("Spionage-RPG · Ausbildung, Einsätze, Beziehungen, Direktion", style = MaterialTheme.typography.bodySmall, color = Pn.Muted)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (auto != null) {
                GlassPanel(onClick = { vm.load("auto") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (autoState != null) Box(Modifier.size(58.dp, 72.dp).clip(RoundedCornerShape(12.dp))) { de.protokollnull.agent.ui.AvatarCanvas(autoState.player.appearance.avatar, Modifier.fillMaxSize()) }
                        Column(Modifier.weight(1f)) {
                            Overline("Fortsetzen")
                            Text(auto.title, style = MaterialTheme.typography.titleMedium)
                            Text(auto.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)).background(Pn.Accent), contentAlignment = Alignment.Center) { PnIcon(PnIcons.Play, Pn.OnAccent, 22.dp) }
                    }
                }
            }
            ActionButton("Neues Spiel", Modifier.fillMaxWidth(), subtitle = "Agent erstellen · Prolog ab 13 Jahren", icon = PnIcons.Plus, secondary = auto != null) { vm.newGame() }
            ActionButton("Spielstand laden", Modifier.fillMaxWidth(), secondary = true, icon = PnIcons.Save) { vm.open(Overlay.Saves) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Einstellungen", Modifier.weight(1f), secondary = true, icon = PnIcons.Gear) { vm.open(Overlay.Settings) }
                ActionButton("Credits", Modifier.weight(1f), secondary = true, icon = PnIcons.Star) { vm.open(Overlay.Credits) }
            }
            Spacer(Modifier.height(4.dp))
            Text("Privater Fan-Prototyp im Nikita-Universum · offline spielbar · v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Eigenes, abstraktes Radar-Emblem (rotierender Sweep). */
@Composable
fun RadarEmblem(modifier: Modifier = Modifier) {
    val anim = animOn()
    val angle = if (anim) rememberInfiniteTransition(label = "radar").animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "a").value else 35f
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        for (i in 1..4) drawCircle(Pn.Accent.copy(alpha = 0.10f + i * 0.03f), r * i / 4f, c, style = Stroke(1.5f))
        drawLine(Pn.Accent.copy(alpha = 0.15f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(Pn.Accent.copy(alpha = 0.15f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        rotate(angle, c) {
            drawArc(Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, Pn.Accent.copy(alpha = 0.28f)), c), -60f, 60f, true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
        }
        listOf(0.62f to 40f, 0.38f to 200f, 0.82f to 290f).forEach { (d, a) ->
            val rad = Math.toRadians(a.toDouble())
            drawCircle(Pn.Accent.copy(alpha = 0.7f), 3.5f, Offset(c.x + (r * d * kotlin.math.cos(rad)).toFloat(), c.y + (r * d * kotlin.math.sin(rad)).toFloat()))
        }
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s by vm.settings.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Einstellungen", "System & Zugänglichkeit", onBack = { vm.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Darstellung", icon = PnIcons.Eye)
            GlassPanel {
                Text("Schriftgröße", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.9f to "S", 1f to "M", 1.15f to "L", 1.3f to "XL").forEach { (v, l) ->
                        PnChip(l, s.fontScale == v, Modifier.weight(1f)) { vm.updateSettings { it.copy(fontScale = v) } }
                    }
                }
                Text("Vorschau: Michael legt dir die Akte hin. „Lies sie gründlich – Einzelheiten retten Leben.“", style = MaterialTheme.typography.bodyMedium, color = Pn.Text.copy(alpha = 0.85f))
                SmallNote("Wirkt zusätzlich zur Schriftgröße des Systems.")
            }
            GlassPanel {
                SettingSwitch("Animationen", "Übergänge, Feier-Einblendungen, Schreibmaschinentext. Aus = reduzierte Bewegung.", s.animations) { v -> vm.updateSettings { it.copy(animations = v) } }
                SettingSwitch("Hoher Kontrast", "Kräftigere Rahmen und hellere Sekundärtexte", s.highContrast) { v -> vm.updateSettings { it.copy(highContrast = v) } }
                SettingSwitch("Haptik", "Vibration bei Meldungen", s.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
            }
            SectionLabel("Audio", icon = PnIcons.Music)
            GlassPanel {
                Text("Musik (prozedural, eigene Komposition): ${s.musicVolume}%", style = MaterialTheme.typography.titleSmall)
                Slider(value = s.musicVolume.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(musicVolume = v.toInt()) } }, valueRange = 0f..100f, steps = 9)
                Text("Effektlautstärke: ${s.sfxVolume}%", style = MaterialTheme.typography.titleSmall)
                Slider(value = s.sfxVolume.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(sfxVolume = v.toInt()) } }, valueRange = 0f..100f, steps = 9)
            }
            SectionLabel("Private Serienporträts", icon = PnIcons.Profile)
            CastPortraitSettings(vm.data.agents.filter { it.seriesCharacter }.map { it.id to it.name })
            SmallNote("Alle Daten bleiben auf diesem Gerät. Das Spiel benötigt keine Internetverbindung.")
        }
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = value, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pn.Accent, checkedThumbColor = Pn.OnAccent))
    }
}

@Composable
fun SavesScreen(vm: GameViewModel, inGame: Boolean) {
    var refresh by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importFrom(uri) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    confirm?.let { (action, slot) ->
        PnDialog(
            title = if (action == "delete") "${vm.slotName(slot)} löschen?" else "${vm.slotName(slot)} überschreiben?",
            text = if (action == "delete") "Der Spielstand wird endgültig entfernt." else "Der vorhandene Spielstand in diesem Slot wird durch den aktuellen ersetzt.",
            confirmLabel = if (action == "delete") "Löschen" else "Überschreiben", danger = action == "delete",
            onConfirm = { if (action == "delete") vm.deleteSlot(slot) else vm.saveTo(slot); refresh++; confirm = null },
            onDismiss = { confirm = null }
        )
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Spielstände", "3 Slots + Autosave", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("auto", "slot1", "slot2", "slot3").forEach { slot ->
                item(key = slot + refresh) {
                    val info = vm.slotInfo(slot)
                    GlassPanel(accent = if (info == null) Pn.Muted else Pn.Accent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(if (slot == "auto") PnIcons.Clock else PnIcons.Save, if (info == null) Pn.Muted else Pn.Accent, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Overline(vm.slotName(slot))
                                Text(info?.title ?: "Leer", style = MaterialTheme.typography.titleSmall, color = if (info == null) Pn.Muted else Pn.Text)
                                if (info != null) Text(info.subtitle + " · " + fmt.format(Date(info.savedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (info != null) ActionButton("Laden", Modifier.weight(1f)) { vm.load(slot) }
                            if (inGame && slot != "auto") ActionButton("Speichern", Modifier.weight(1f), secondary = true) {
                                if (info != null) confirm = "save" to slot else { vm.saveTo(slot); refresh++ }
                            }
                            if (info != null && slot != "auto") ActionButton("Löschen", Modifier.weight(1f), secondary = true, danger = true) { confirm = "delete" to slot }
                        }
                    }
                }
            }
            item {
                GlassPanel {
                    Text("Import / Export", style = MaterialTheme.typography.titleSmall)
                    Text("JSON-Datei mit Versionsangabe. Ältere Spielstände (ab v0.1) werden beim Import automatisch migriert.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (inGame) ActionButton("Exportieren", Modifier.weight(1f), secondary = true) { exportLauncher.launch("protokoll_null_spielstand.json") }
                        ActionButton("Importieren", Modifier.weight(1f), secondary = true) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    }
                }
            }
            if (inGame) item { ActionButton("Zum Hauptmenü", Modifier.fillMaxWidth(), secondary = true, icon = PnIcons.Back) { vm.goToStart() } }
        }
    }
}

@Composable
fun CreditsScreen(vm: GameViewModel) {
    val data = vm.data
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Credits & Rechte", "Transparenz", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GlassPanel {
                    Text("PROTOKOLL NULL – Aufstieg eines Agenten", style = MaterialTheme.typography.titleSmall)
                    Text("Privater, nicht-kommerzieller Fan-Prototyp im Universum der Serie „Nikita“ (The CW, 2010–2013). Figurennamen und Setting gehören den jeweiligen Rechteinhabern. Story, Dialoge, Missionen und Spielsysteme sind eigenständig geschrieben und keine Episodenabschriften. Für eine öffentliche oder kommerzielle Veröffentlichung sind Freigaben oder eigenständige Figuren nötig.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                GlassPanel {
                    Text("Porträts der Serienfiguren", style = MaterialTheme.typography.titleSmall)
                    Text("Verwendet werden ausschließlich CC-lizenzierte Fotos der Darsteller von Wikimedia Commons (Bildausschnitt angepasst). Das sind keine Serienstills und keine Rechte an den Figuren. Offizielle Szenenbilder dienen nur als Stilreferenz (assets/SERIES_LOOK_REFERENCES.json) und werden erst angezeigt, wenn ein überprüfter Lizenznachweis in licenses/series_image_licenses.json vorliegt.", style = MaterialTheme.typography.bodySmall)
                    val stills = data.licensedSeriesStills
                    Text(if (stills.isEmpty()) "Lizenzierte Serienstills: keine" else "Lizenzierte Serienstills: " + stills.joinToString(), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                }
            }
            item {
                GlassPanel {
                    Text("Fotos (CC BY / CC BY-SA, Wikimedia Commons)", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "Michael – Shane West: „Thibault“, CC BY-SA 2.0", "Nikita – Maggie Q: Cameron Yee, CC BY 2.0", "Alex – Lyndsy Fonseca: Cameron Yee, CC BY 2.0",
                        "Weitere Darstellerfotos: Autor, Lizenz und Quelle je Bild in assets/CAST_IMAGE_CREDITS.json"
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                GlassPanel {
                    Text("Gegenstandsbilder & Schriften", style = MaterialTheme.typography.titleSmall)
                    Text("32 Icons von game-icons.net (Lorc, Delapouite u. a.), CC BY 3.0 – creativecommons.org/licenses/by/3.0. Sechs zusätzliche Icons (Drohne, Einsatzbefehl, Markierungskartuschen, Regenerationspaket, PHOENIX-Marke, verschlüsseltes Archiv) sind Originalgrafiken dieses Projekts (CC0). Schriften: Anton, Inter und Anonymous Pro unter SIL Open Font License 1.1 (Lizenztexte in licenses/fonts). Alle Benutzeroberflächen-Icons sind eigene Vektorgrafiken.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                GlassPanel {
                    Text("KI-Hinweis", style = MaterialTheme.typography.titleSmall)
                    Text("Die neun Szenenbilder und die Porträts von Voss, Maya, Ethan, Kira und Helena wurden für dieses Spiel mit OpenAI ImageGen gestaltet. Sie sind offline enthalten. Code, Texte und Originalgrafiken entstanden mit KI-Unterstützung. Eigene Porträts wählst du aus deiner persönlichen Bildersammlung.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SmallNote(text: String) {
    // v1.0: Hinweise in Inter statt Monospace – lesbar auch bei großer Schrift
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
}

@Composable
fun TitleLine(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

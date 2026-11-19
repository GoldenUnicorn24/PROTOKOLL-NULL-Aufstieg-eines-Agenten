package de.protokollnull.agent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.decode.SvgDecoder
import de.protokollnull.agent.engine.Ages
import de.protokollnull.agent.engine.Clock
import de.protokollnull.agent.model.GameState
import de.protokollnull.agent.ui.screens.*
import de.protokollnull.agent.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AgentApp(vm: GameViewModel = viewModel(), imageLoader: ImageLoader? = null) {
    val context = LocalContext.current
    val loader = imageLoader ?: remember(context) { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.crossfade(true).build() }
    val settings by vm.settings.collectAsState()
    val screen by vm.screen.collectAsState()
    val state by vm.state.collectAsState()
    val overlays by vm.overlays.collectAsState()
    val toast by vm.toast.collectAsState()

    ProtokollTheme(settings) {
        CompositionLocalProvider(LocalLoader provides loader) {
            TacticalBackground {
                val s = state
                when {
                    screen == "start" -> StartScreen(vm)
                    screen == "creator" -> CreatorScreen(vm)
                    s == null -> StartScreen(vm)
                    s.phase == "prologue" -> PrologueScreen(vm, s)
                    else -> GameShell(vm, s, overlays)
                }
                // Overlays, die auch im Startmenü erreichbar sind
                if (screen != "game" || s == null) {
                    when (overlays.lastOrNull()) {
                        Overlay.Settings -> FullOverlay { SettingsScreen(vm) }
                        Overlay.Saves -> FullOverlay { SavesScreen(vm, inGame = false) }
                        Overlay.Credits -> FullOverlay { CreditsScreen(vm) }
                        else -> {}
                    }
                }
                ToastHost(toast, onDismiss = vm::dismissToast, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (screen == "game") 92.dp else 24.dp))
            }
        }
    }
    BackHandler(enabled = overlays.isNotEmpty()) { vm.back() }
}

@Composable
fun FullOverlay(content: @Composable () -> Unit) {
    TacticalBackground(Modifier.fillMaxSize().clickable(enabled = true, onClick = {})) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) { content() }
    }
}

@Composable
private fun ToastHost(toast: Toast?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsOn = LocalSettings.current.haptics
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            if (hapticsOn) haptics.performHapticFeedback(if (toast.error) androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress else androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            delay(if (toast.lines.size > 2) 5200 else 3400); onDismiss() }
    }
    AnimatedVisibility(visible = toast != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }, modifier = modifier) {
        val t = toast ?: return@AnimatedVisibility
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (t.error) Color(0xF22A1418) else Color(0xF20E2030))
                .border(1.dp, (if (t.error) Pn.Danger else Pn.Accent).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable(onClick = onDismiss).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            t.lines.forEachIndexed { i, line ->
                Text(line, style = if (i == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = if (t.error) Pn.Danger else if (i == 0) Pn.Text else Pn.Muted, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun GameShell(vm: GameViewModel, s: GameState, overlays: List<Overlay>) {
    val tab by vm.tab.collectAsState()
    val data = vm.data
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Hud(vm, s)
        Box(Modifier.weight(1f)) {
            val anim = animOn()
            AnimatedContent(targetState = tab, transitionSpec = { if (anim) fadeIn() togetherWith fadeOut() else fadeIn(androidx.compose.animation.core.snap()) togetherWith fadeOut(androidx.compose.animation.core.snap()) }, label = "tab") { t ->
                when (t) {
                    0 -> HqScreen(vm, s)
                    1 -> MissionBoardScreen(vm, s)
                    2 -> InventoryScreen(vm, s)
                    3 -> ContactsScreen(vm, s)
                    else -> ProfileScreen(vm, s)
                }
            }
        }
        BottomNav(tab) { vm.setTab(it) }
    }
    // Overlays im Spiel
    overlays.lastOrNull()?.let { o ->
        FullOverlay {
            when (o) {
                Overlay.Shop -> ShopScreen(vm, s)
                Overlay.Storage -> StorageScreen(vm, s)
                Overlay.Skills -> SkillTreeScreen(vm, s)
                Overlay.Ranks -> CareerScreen(vm, s)
                Overlay.Quests -> QuestLogScreen(vm, s)
                Overlay.Calendar -> SimulationScreen(vm, s)
                Overlay.Recruit -> RecruitEditorScreen(vm, s)
                Overlay.Team -> TeamScreen(vm, s)
                Overlay.Director -> DirectorScreen(vm, s)
                Overlay.Settings -> SettingsScreen(vm)
                Overlay.Saves -> SavesScreen(vm, inGame = true)
                Overlay.Credits -> CreditsScreen(vm)
                Overlay.Training -> TrainingPlannerScreen(vm, s)
                Overlay.Reports -> ReportsScreen(vm, s)
                Overlay.HqEvent -> HqEventScreen(vm, s)
                is Overlay.Npc -> NpcDetailScreen(vm, s, o.id, o.tab)
                is Overlay.Briefing -> BriefingScreen(vm, s, o.missionId)
                is Overlay.Item -> ItemDetailScreen(vm, s, o.uid)
            }
        }
    }
    // Laufender Einsatz und Dialoge haben Vorrang
    if (s.activeMission != null) FullOverlay { MissionRunScreen(vm, s) }
    if (s.activeExam != null) FullOverlay { ExamScreen(vm, s) }
    if (s.activeDialogue != null) FullOverlay { DialogueScreen(vm, s) }
    if (s.activeDialogue == null && s.activeMission == null) {
        val introduction = de.protokollnull.agent.engine.CompanionAssignment.awaitingIntroduction(s)
        if (introduction != null) FullOverlay { CompanionIntroduction(vm, s, introduction) }
        else if (de.protokollnull.agent.engine.Endings.pending(s)) FullOverlay { EpilogueScreen(vm, s) }
    }
}

@Composable
private fun Hud(vm: GameViewModel, s: GameState) {
    val p = s.player
    Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xF2060C17), Color(0xD9060C17)))).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp, 52.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.setTab(4) }) { AvatarCanvas(p.appearance.avatar, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PnIcon(PnIcons.Clock, size = 12.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(Clock.dateLabel(s.clock.day).uppercase() + " · " + Clock.slotTimes[s.clock.slot], style = MaterialTheme.typography.labelSmall, color = Pn.Accent, maxLines = 1)
                }
                Text(p.codename.uppercase(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = Pn.Text, maxLines = 1)
                Text("${Ages.player(s)} J. · Lv ${p.level} · ${vm.data.rank(p.rank).name}", style = MaterialTheme.typography.labelMedium, color = Pn.Muted, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill("${p.credits}", Pn.Warn, icon = PnIcons.Coin)
                Spacer(Modifier.height(4.dp))
                Text(Clock.slotNames[s.clock.slot].uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBar("Energie", p.energy, 100, Pn.Accent, Modifier.weight(1f), showValue = false, icon = PnIcons.Bolt)
            StatBar("Stress", p.stress, 100, if (p.stress > 80) Pn.Danger else Pn.Warn, Modifier.weight(1f), showValue = false, icon = PnIcons.Pulse)
            StatBar("Gesundheit", p.health, 100, Pn.Ok, Modifier.weight(1f), showValue = false, icon = PnIcons.Heart)
        }
    }
}

@Composable
private fun BottomNav(tab: Int, onSelect: (Int) -> Unit) {
    val items = listOf("HQ" to PnIcons.Hq, "Einsatz" to PnIcons.Target, "Inventar" to PnIcons.Grid, "Kontakte" to PnIcons.People, "Profil" to PnIcons.Profile)
    Row(
        Modifier.fillMaxWidth().background(Color(0xF5060C17)).drawBehind { drawLine(Pn.Accent.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2f) }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val sel = i == tab
            val bg by androidx.compose.animation.animateColorAsState(if (sel) Color(0x263FD6C4) else Color.Transparent, label = "nav")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg).clickable { onSelect(i) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PnIcon(icon, if (sel) Pn.Accent else Pn.Muted, 22.dp)
                Spacer(Modifier.height(2.dp))
                Text(label, color = if (sel) Pn.Accent else Pn.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Box(Modifier.padding(top = 3.dp).size(width = if (sel) 18.dp else 0.dp, height = 2.dp).background(Pn.Accent))
            }
        }
    }
}

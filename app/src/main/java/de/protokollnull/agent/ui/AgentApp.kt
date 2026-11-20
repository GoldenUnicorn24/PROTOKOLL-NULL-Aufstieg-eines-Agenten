package de.protokollnull.agent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.decode.SvgDecoder
import de.protokollnull.agent.engine.Ages
import de.protokollnull.agent.engine.Alerts
import de.protokollnull.agent.engine.Clock
import de.protokollnull.agent.engine.Progression
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
    val celebration by vm.celebration.collectAsState()
    val busy by vm.busy.collectAsState()
    val tab by vm.tab.collectAsState()

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
                val inShell = screen == "game" && s != null && s.phase != "prologue"
                ToastHost(toast, onDismiss = vm::dismissToast, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (inShell && overlays.isEmpty() && s?.activeMission == null) 84.dp else 20.dp))
                CelebrationHost(celebration, onDismiss = vm::dismissCelebration)
                BusyOverlay(busy)
            }
        }
    }
    // Zurück-Logik: Overlays schließen, laufende Einsätze/Dialoge schützen, sonst zum HQ.
    val s = state
    // Vollbild-Ereignisse (Einsatz, Dialog, Prüfung, Epilog, Gefährten-Ankunft) liegen über allen Overlays –
    // „Zurück“ darf darunter nichts Unsichtbares schließen.
    val shielded = s != null && screen == "game" && s.phase == "game" && (s.activeMission != null || s.activeDialogue != null || s.activeExam != null ||
        de.protokollnull.agent.engine.Endings.pending(s) || de.protokollnull.agent.engine.CompanionAssignment.awaitingIntroduction(s) != null)
    BackHandler(enabled = busy == null && (overlays.isNotEmpty() || shielded || (screen == "game" && tab != 0) || screen == "creator")) {
        when {
            shielded -> vm.notify("Erst abschließen oder oben abbrechen – Fortschritt bleibt gespeichert.")
            overlays.isNotEmpty() -> vm.back()
            screen == "creator" -> vm.goToStart()
            else -> vm.setTab(0)
        }
    }
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
        val tone = if (t.error) Pn.Danger else Pn.Accent
        Row(
            Modifier.padding(horizontal = Sp.gutter).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (t.error) Color(0xF52A1418) else Color(0xF50E2030))
                .border(1.dp, tone.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Meldung schließen", onClick = onDismiss)
                .semantics(mergeDescendants = true) {}
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            PnIcon(if (t.error) PnIcons.Skull else PnIcons.Info, tone, 18.dp)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                t.lines.forEachIndexed { i, line ->
                    Text(line, style = if (i == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        color = if (t.error) Pn.Danger else if (i == 0) Pn.Text else Pn.Muted, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun GameShell(vm: GameViewModel, s: GameState, overlays: List<Overlay>) {
    val tab by vm.tab.collectAsState()
    val alerts = remember(s) { Alerts.nav(vm.data, s) }
    val anim = animOn()
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Hud(vm, s)
        Box(Modifier.weight(1f)) {
            AnimatedContent(targetState = tab, transitionSpec = {
                if (anim) (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { (if (targetState > initialState) 1 else -1) * it / 10 }) togetherWith fadeOut(tween(160))
                else fadeIn(snap()) togetherWith fadeOut(snap())
            }, label = "tab") { t ->
                when (t) {
                    0 -> HqScreen(vm, s)
                    1 -> MissionBoardScreen(vm, s)
                    2 -> InventoryScreen(vm, s)
                    3 -> ContactsScreen(vm, s)
                    else -> ProfileScreen(vm, s)
                }
            }
        }
        BottomNav(tab, alerts) { vm.setTab(it) }
    }
    // Overlays im Spiel – oberstes Element gleitet von rechts herein
    val top = overlays.lastOrNull()
    AnimatedContent(targetState = top, transitionSpec = {
        if (!anim) fadeIn(snap()) togetherWith fadeOut(snap())
        else if (targetState != null && (initialState == null || overlays.size > 1)) (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))) togetherWith fadeOut(tween(150))
        else fadeIn(tween(150)) togetherWith (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(200)))
    }, label = "overlay", contentKey = { it?.toString() }) { o ->
        if (o != null) FullOverlay { OverlayContent(vm, s, o) }
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
private fun OverlayContent(vm: GameViewModel, s: GameState, o: Overlay) {
    when (o) {
        Overlay.Shop -> ShopScreen(vm, s)
        Overlay.Storage -> StorageScreen(vm, s)
        Overlay.Skills -> SkillTreeScreen(vm, s)
        Overlay.Ranks -> CareerScreen(vm, s)
        Overlay.Quests -> QuestLogScreen(vm, s)
        Overlay.Calendar -> SimulationScreen(vm, s)
        Overlay.Recruit -> RecruitEditorScreen(vm, s)
        Overlay.Team -> TeamScreen(vm, s)
        Overlay.Director -> DirectorateScreen(vm, s)
        Overlay.Settings -> SettingsScreen(vm)
        Overlay.Saves -> SavesScreen(vm, inGame = true)
        Overlay.Credits -> CreditsScreen(vm)
        Overlay.Training -> TrainingPlannerScreen(vm, s)
        Overlay.Reports -> ReportsScreen(vm, s)
        Overlay.HqEvent -> HqEventScreen(vm, s)
        is Overlay.Case -> CaseScreen(vm, s, o.id)
        is Overlay.MissionControl -> Column(Modifier.fillMaxSize()) {
            ScreenHeader("Mission Control", "Weltlage · Akten · Ketten", onBack = { vm.back() })
            Box(Modifier.weight(1f)) { MissionControlScreen(vm, s, o.tab, showTitle = false) }
        }
        is Overlay.Npc -> NpcDetailScreen(vm, s, o.id, o.tab)
        is Overlay.Briefing -> BriefingScreen(vm, s, o.missionId)
        is Overlay.Item -> ItemDetailScreen(vm, s, o.uid)
        is Overlay.Command -> CommandScreen(vm, s, o.tab)
        is Overlay.City -> CityScreen(vm, s, o.tab)
        is Overlay.DirectorView -> DirectorateScreen(vm, s, o.tab)
    }
}

/** Kompakte Statusleiste: Identität, Zeit, Credits und Verfassung auf einen Blick. */
@Composable
private fun Hud(vm: GameViewModel, s: GameState) {
    val p = s.player
    val xpReq = Progression.requiredXp(p.level)
    val desc = "${p.codename}, ${Ages.player(s)} Jahre, Level ${p.level}, ${vm.data.rank(p.rank).name}. ${Clock.dateLabel(s.clock.day)}, ${Clock.slotNames[s.clock.slot]}. ${p.credits} Credits. Energie ${p.energy}, Stress ${p.stress}, Gesundheit ${p.health}."
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xF7060C17), Color(0xE0060C17))))
            .drawBehind { drawLine(Pn.Accent.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1f) }
            .padding(horizontal = Sp.gutter, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = desc }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp, 50.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Pn.Accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Profil öffnen") { vm.setTab(4) }
            ) { PlayerPortrait(s, 42.dp, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(Clock.dateLabel(s.clock.day).uppercase() + " · " + Clock.slotTimes[s.clock.slot] + " · " + Clock.slotNames[s.clock.slot].uppercase(),
                    style = PnType.overline.copy(fontSize = 10.sp), color = Pn.Accent, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.codename.uppercase(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 22.sp), color = Pn.Text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text("Lv ${p.level}", style = PnType.data.copy(fontSize = 12.sp), color = Pn.Accent)
                }
                Text("${Ages.player(s)} J. · ${vm.data.rank(p.rank).name}", style = MaterialTheme.typography.labelSmall, color = Pn.Muted, maxLines = 1)
            }
            Pill("${p.credits}", Pn.Warn, icon = PnIcons.Coin)
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HudBar(PnIcons.Bolt, p.energy, if (p.energy < 25) Pn.Danger else Pn.Accent, Modifier.weight(1f))
            HudBar(PnIcons.Pulse, p.stress, if (p.stress > 80) Pn.Danger else Pn.Warn, Modifier.weight(1f))
            HudBar(PnIcons.Heart, p.health, if (p.health < 40) Pn.Danger else Pn.Ok, Modifier.weight(1f))
            HudBar(PnIcons.Star, (p.xp * 100 / xpReq.coerceAtLeast(1)), Pn.Violet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HudBar(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int, color: Color, modifier: Modifier) {
    val anim by androidx.compose.animation.core.animateFloatAsState((value / 100f).coerceIn(0f, 1f), tween(if (animOn()) 600 else 0), label = "hud")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PnIcon(icon, color, 12.dp)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x1FFFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))))
        }
    }
}

@Composable
private fun BottomNav(tab: Int, alerts: Alerts.Nav, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("HQ", PnIcons.Hq, alerts.hq), Triple("Einsatz", PnIcons.Target, alerts.missions), Triple("Inventar", PnIcons.Grid, 0),
        Triple("Kontakte", PnIcons.People, alerts.contacts), Triple("Profil", PnIcons.Profile, alerts.profile)
    )
    Row(
        Modifier.fillMaxWidth().background(Color(0xF7060C17)).drawBehind { drawLine(Pn.Accent.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2f) }
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        items.forEachIndexed { i, (label, icon, badge) ->
            val sel = i == tab
            val bg by animateColorAsState(if (sel) Color(0x263FD6C4) else Color.Transparent, label = "nav")
            Column(
                Modifier.weight(1f).defaultMinSize(minHeight = Sp.touch).clip(RoundedCornerShape(14.dp)).background(bg)
                    .clickable(role = Role.Tab, onClickLabel = label) { onSelect(i) }
                    .semantics { selected = sel; contentDescription = label + if (badge > 0) ", $badge neue Hinweise" else "" }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    PnIcon(icon, if (sel) Pn.Accent else Pn.Muted, 22.dp)
                    if (badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-3).dp)) { BadgeDot(Pn.Warn, 9.dp) }
                }
                Spacer(Modifier.height(2.dp))
                Text(label, color = if (sel) Pn.Accent else Pn.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Box(Modifier.padding(top = 3.dp).size(width = if (sel) 18.dp else 0.dp, height = 2.dp).background(Pn.Accent))
            }
        }
    }
}

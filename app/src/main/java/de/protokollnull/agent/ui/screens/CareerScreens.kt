package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

private fun gradeColor(g: String?) = when (g) { "A" -> Pn.Ok; "B" -> Pn.Accent; "C" -> Pn.Warn; "D", "E" -> Pn.Danger; else -> Pn.Muted }

private fun activityIcon(id: String) = when (id) {
    "combat" -> PnIcons.Fist; "tech" -> PnIcons.Chip; "recon" -> PnIcons.Eye; "social" -> PnIcons.People
    "rest" -> PnIcons.Bed; "duty" -> PnIcons.Radar; "exam" -> PnIcons.Brain; else -> PnIcons.Clock
}

// ---------------------------------------------------------------------------
// Karriereübersicht: Rang, Prüfung, Beurteilung, Freischaltungen, Journal
// ---------------------------------------------------------------------------

@Composable
fun CareerScreen(vm: GameViewModel, s: GameState, initialTab: String = "overview") {
    val data = vm.data
    val check = Progression.nextRankCheck(data, s)
    val c = s.career
    val ev = Career.latestEvaluation(s)
    var tab by remember { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Karriere", "Rang ${s.player.rank}/12 · ${data.rank(s.player.rank).name}", onBack = { vm.back() })
        PnTabs(listOf("overview" to "Übersicht", "board" to "Rangliste", "ranks" to "Ränge & Freigaben", "journal" to "Journal (${s.journal.size})"), tab,
            Modifier.padding(horizontal = Sp.gutter, vertical = 8.dp), badges = if (Progression.examReady(data, s)) setOf("overview") else emptySet()) { tab = it }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                "overview" -> {
                    item {
                        GlassPanel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(64.dp).clip(CircleShape).background(gradeColor(ev?.grade).copy(alpha = 0.18f)).border(2.dp, gradeColor(ev?.grade), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(ev?.grade ?: "–", style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp), color = gradeColor(ev?.grade))
                                }
                                Column(Modifier.weight(1f)) {
                                    Overline("LEISTUNGSBEURTEILUNG", Pn.Accent)
                                    Text(if (ev != null) "vor ${s.clock.day - ev.day} Tagen · ${ev.score}/100" else "Noch keine Beurteilung", style = MaterialTheme.typography.titleSmall)
                                    Text(ev?.comment ?: "Bitte Michael im Ausbildungsbereich um eine Beurteilung.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val b = Career.breakdown(s)
                            Text("Aktueller Stand (letzte 30 Tage)", style = MaterialTheme.typography.labelMedium, color = Pn.Muted)
                            StatBar("Training (${s.career.trainingDays.count { it > s.clock.day - 30 }} Einheiten)", b.training, 36, Pn.Accent, icon = PnIcons.Fist)
                            StatBar("Einsätze", b.missions.coerceAtLeast(0), 30, Pn.Ok, icon = PnIcons.Target)
                            StatBar("Entwicklung der Attribute", b.development, 20, Pn.Violet, icon = PnIcons.Spark)
                            StatBar("Prüfungsvorbereitung", b.prep, 12, Pn.Warn, icon = PnIcons.Brain)
                            Text("Prognose: Note ${Career.grade(b.total)} (${b.total}/100)" + if (b.stress < 0) " · Stress drückt die Wertung" else "", style = MaterialTheme.typography.labelSmall, color = gradeColor(Career.grade(b.total)))
                            SmallNote(if (Career.canRequestEvaluation(s)) "Eine neue Beurteilung kann bei Michael angefordert werden." else "Nächste Beurteilung in ${Career.EVAL_INTERVAL - (s.clock.day - (c.evaluations.lastOrNull()?.day ?: 0))} Tagen (im Zeitplan automatisch monatlich).")
                        }
                    }
                    if (check != null) item {
                        GlassPanel(accent = if (check.ready) Pn.Ok else Pn.Warn) {
                            Overline("NÄCHSTER DIENSTGRAD", Pn.Accent)
                            Text(check.rank.name.uppercase(), style = MaterialTheme.typography.titleLarge)
                            Text(check.rank.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            check.requirements.forEach { (t, ok) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PnIcon(if (ok) PnIcons.Shield else PnIcons.Lock, if (ok) Pn.Ok else Pn.Danger, 14.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(t, color = if (ok) Pn.Ok else Pn.Text, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider(color = Pn.Line)
                            Text("Prüfung in drei Phasen: Theorie · Praxis · Lageentscheidung, danach Urteil von Michael. Bestanden ab ${Career.PASS_SCORE} Punkten.", style = MaterialTheme.typography.bodySmall)
                            StatBar("Prüfungsvorbereitung" + if (c.examPrepRequired > 0) " (Auflage ${c.examPrepRequired})" else "", c.examPrep, 100, Pn.Warn, icon = PnIcons.Brain)
                            if (c.examCooldownUntil > s.clock.day) Pill("Wiederholung in ${c.examCooldownUntil - s.clock.day} Tagen", Pn.Danger, icon = PnIcons.Clock)
                            val lock = Career.examLock(data, s)
                            ActionButton(if (lock == null) "Prüfung beginnen" else "Prüfung gesperrt", Modifier.fillMaxWidth(), enabled = lock == null, subtitle = lock ?: "Energie −8 je Phase · 2 Slots", icon = PnIcons.Star) {
                                vm.act { d, x -> Career.startExam(d, x) }
                            }
                            if (lock != null && s.location != "director" && check.ready) ActionButton("Zum Direktorat gehen", Modifier.fillMaxWidth(), secondary = true, icon = PnIcons.Crown) {
                                vm.act(silent = true) { d, x -> World.moveTo(d, x, "director") }
                            }
                        }
                    }
                    item {
                        SectionLabel("Persönliche Trainingsziele", icon = PnIcons.Flag)
                        Spacer(Modifier.height(6.dp))
                        GoalsPanel(vm, s)
                    }
                    item {
                        SectionLabel("Auszeichnungen", trailing = "${c.awards.size}/${Career.awardTitles.size}", icon = PnIcons.Star)
                        Spacer(Modifier.height(6.dp))
                        PillRow(Career.awardTitles.map { (id, t) -> if (id in c.awards) "★ $t" else "☆ $t" }, Pn.Warn)
                    }
                }
                "ranks" -> items(data.ranks) { r ->
                    val reached = s.player.rank >= r.rank
                    val next = r.rank == s.player.rank + 1
                    GlassPanel(accent = when { reached -> Pn.Accent; next -> Pn.Warn; else -> Pn.Muted }, padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(if (reached) Color(0x333FD6C4) else Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
                                Text("${r.rank}", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = if (reached) Pn.Accent else Pn.Muted)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                Text("Lv ${r.minLevel} · ${r.minAge} J. · Ruf ${r.minReputation} · ${r.salaryPerWeek} Cr/Woche · Team ${r.teamSize}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (reached) Pill("erreicht", Pn.Ok) else if (next) Pill("nächster", Pn.Warn)
                        }
                        r.unlocks.forEach { u ->
                            Row { Text(if (reached) "✓ " else "• ", color = if (reached) Pn.Ok else Pn.Muted, style = MaterialTheme.typography.bodySmall); Text(u, style = MaterialTheme.typography.bodySmall, color = if (reached) Pn.Text else Pn.Muted) }
                        }
                    }
                }
                "board" -> leaderboardItems(vm, s)
                else -> {
                    if (s.journal.isEmpty()) item { SmallNote("Noch keine Einträge. Beurteilungen, Prüfungen, Ziele und Auszeichnungen erscheinen hier.") }
                    items(s.journal.reversed()) { j ->
                        val color = when (j.type) { "promotion", "award" -> Pn.Warn; "exam_failed" -> Pn.Danger; "evaluation" -> Pn.Accent; "goal" -> Pn.Ok; else -> Pn.Muted }
                        GlassPanel(accent = color, padding = 12.dp) {
                            Text(Clock.dateLabel(j.day).uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
                            Text(j.title, style = MaterialTheme.typography.titleSmall)
                            Text(j.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** v1.0: Die Rangliste der Division – berechnet aus echten Laufbahnwerten. */
private fun androidx.compose.foundation.lazy.LazyListScope.leaderboardItems(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val board = Leaderboard.board(data, s)
    val me = board.indexOfFirst { it.isPlayer }
    val trend = Leaderboard.trend(s)
    item {
        GlassPanel(accent = if (me == 0) Pn.Warn else Pn.Accent) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgressRing(board.size - me, board.size, 64.dp, if (me == 0) Pn.Warn else Pn.Accent, "${me + 1}.")
                Column(Modifier.weight(1f)) {
                    Overline("Dein Platz auf der Tafel")
                    Text("Platz ${me + 1} von ${board.size}", style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        trend > 0 -> "▲ $trend seit letzter Woche"
                        trend < 0 -> "▼ ${-trend} seit letzter Woche"
                        s.career.boardWeek >= 0 -> "Unverändert seit letzter Woche"
                        else -> "Erste Wertung – der Trend erscheint nach einer Woche"
                    }, style = MaterialTheme.typography.labelSmall, color = if (trend > 0) Pn.Ok else if (trend < 0) Pn.Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${board[me].score}", style = PnType.metric, color = Pn.Text)
            }
            if (me > 0) {
                val gap = board[me - 1].score - board[me].score
                InfoNote("Bis Platz $me (${board[me - 1].name}) fehlen dir $gap Punkte.", Pn.Accent, PnIcons.Target)
            }
        }
    }
    items(board.size) { i ->
        val e = board[i]
        val tone = when { e.isPlayer -> Pn.Accent; i == 0 -> Pn.Warn; else -> Pn.Muted }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (e.isPlayer) Pn.Accent.copy(alpha = 0.14f) else Color(0x0DFFFFFF))
                .border(1.dp, if (e.isPlayer) Pn.Accent.copy(alpha = 0.6f) else Pn.Hairline, RoundedCornerShape(12.dp))
                .then(if (!e.isPlayer) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.open(Overlay.Npc(e.id, "career")) } else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("${i + 1}", style = PnType.metric.copy(fontSize = 20.sp), color = tone, modifier = Modifier.width(30.dp))
            if (e.isPlayer) PlayerPortrait(s, 34.dp) else NpcPortrait(data, s, e.id, 34.dp)
            Column(Modifier.weight(1f)) {
                Text(e.name + if (e.isPlayer) " (du)" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(data.rank(e.rank).name + " · ${e.missions} " + (if (e.missions == 1) "Einsatz" else "Einsätze") + if (!e.isPlayer && !LifeStatus.inHq(e.status)) " · ${LifeStatus.label(e.status)}" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.size(28.dp).clip(CircleShape).background(gradeColor(e.grade).copy(alpha = 0.18f)).border(1.dp, gradeColor(e.grade), CircleShape), contentAlignment = Alignment.Center) {
                Text(e.grade ?: "–", style = PnType.data.copy(fontSize = 12.sp), color = gradeColor(e.grade))
            }
            Text("${e.score}", style = PnType.data, color = Pn.Text, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
    item {
        SmallNote("Punkte: Dienstgrad × 100 · letzte Note (A 40 bis E 0) · erfolgreiche Einsätze × 3 (höchstens 60) · Ausbildung bis 30 (bei dir Trainingstage der letzten 30 Tage, bei anderen der Fortschritt zur nächsten Prüfung). Die Leitungsebene steht nicht auf der Tafel. Antippen öffnet die Laufbahn.")
    }
}

@Composable
private fun GoalsPanel(vm: GameViewModel, s: GameState) {
    var attr by remember { mutableStateOf(s.player.weakness.takeIf { it in Attr.all } ?: Attr.FITNESS) }
    val cur = s.player.attributes[attr] ?: 30
    val cap = Stats.attributeCap(s.player.level)
    var target by remember(attr) { mutableIntStateOf(s.career.goals[attr] ?: (cur + 5)) }
    GlassPanel(padding = 14.dp) {
        s.career.goals.forEach { (a, t) ->
            val v = s.player.attributes[a] ?: 30
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatBar("${Attr.label(a)}: Ziel $t", v, t, Pn.Ok, Modifier.weight(1f))
                TextButton(onClick = { vm.update { Career.setGoal(it, a, null) } }) { Text("Entfernen", color = Pn.Danger) }
            }
        }
        Text("Neues Ziel", style = MaterialTheme.typography.titleSmall)
        ChipRow(Attr.all.map { it to Attr.label(it) }, attr) { attr = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton("−", secondary = true, enabled = target > cur + 1) { target-- }
            Text("${Attr.label(attr)}: $cur → $target", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
            ActionButton("+", secondary = true, enabled = target < Int.MAX_VALUE / 1024 - 1) { target++ }
        }
        SmallNote("Dein Rating kann durch Training dauerhaft über 100 steigen. Hohe Werte brauchen mehr Ausdauer.")
        ActionButton("Ziel festlegen", Modifier.fillMaxWidth(), secondary = true, icon = PnIcons.Flag) { vm.update { Career.setGoal(it, attr, target) } }
    }
}

// ---------------------------------------------------------------------------
// Mehrphasige Prüfung
// ---------------------------------------------------------------------------

@Composable
fun ExamScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val e = s.activeExam ?: return
    val rankName = data.rank(e.rank).name
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline("BEFÖRDERUNGSPRÜFUNG", Pn.Accent)
        Text(rankName.uppercase(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 36.sp))
        PhaseStepper(listOf("narrative", "search", "observe", "narrative"), e.phase.coerceAtMost(3))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Career.phaseNames.forEachIndexed { i, n -> Pill(n, if (i < e.phase) Pn.Ok else if (i == e.phase) Pn.Accent else Pn.Muted) }
        }
        GlassPanel {
            Text("Punkte: ${e.score} / ${Career.PASS_SCORE}", style = MaterialTheme.typography.titleMedium)
            StatBar("Fortschritt zur Bestehensgrenze", e.score.coerceAtMost(Career.PASS_SCORE), Career.PASS_SCORE, if (e.score >= Career.PASS_SCORE) Pn.Ok else Pn.Warn, showValue = false)
            e.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        when {
            e.finished -> {
                GlassPanel(accent = if (e.passed) Pn.Ok else Pn.Danger) {
                    Text(if (e.passed) "BESTANDEN" else "NICHT BESTANDEN", style = MaterialTheme.typography.titleLarge, color = if (e.passed) Pn.Ok else Pn.Danger)
                    Text(if (e.passed) "Michael nickt knapp. „Glückwunsch, $rankName.“ Neue Aufgaben und Freigaben sind aktiv." else "„Das war noch nichts. Du weißt, was zu tun ist.“ Auflage: Prüfungsvorbereitung ${s.career.examPrepRequired}, Wiederholung in ${Career.EXAM_COOLDOWN} Tagen.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (e.passed) data.rank(e.rank).unlocks.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = Pn.Ok) }
                }
                ActionButton("Zurück ins Direktorat", Modifier.fillMaxWidth()) { vm.update { Career.closeExam(it) } }
            }
            e.phase <= 1 -> {
                val check = Career.phaseCheck(s, e.phase, e.rank)
                GlassPanel {
                    Text(if (e.phase == 0) "PHASE 1 · THEORIE" else "PHASE 2 · PRAXIS", style = MaterialTheme.typography.labelLarge, color = Pn.Accent)
                    Text(if (e.phase == 0) "Ein Prüfungsgremium befragt dich zu Einsatzrecht, Planung und Lagebeurteilung." else when {
                        e.rank <= 4 -> "Parcours und Abwehrübung unter Beobachtung der Ausbilder."
                        e.rank <= 7 -> "Präzisionsprüfung im Simulator mit wechselnden Zielen (Markierungsmunition)."
                        else -> "Führung eines Teams durch ein Lagezentrum-Szenario in Echtzeit."
                    }, style = MaterialTheme.typography.bodyMedium)
                    Text("Probe: ${Attr.label(check.attribute)} · ${data.skillById[check.skill]?.name ?: ""} · Schwierigkeit ${check.difficulty} · Vorbereitungsbonus +${Career.phaseBonus(s, e.phase)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ChoiceButton("Phase ablegen", "Teilpunkte auch bei Misserfolg", Career.phaseChance(data, s), icon = PnIcons.Spark) { vm.act(silent = true) { d, x -> Career.takePhase(d, x) } }
            }
            else -> {
                val sc = Career.scenario(e.rank)
                GlassPanel {
                    Overline("PHASE 3 · LAGEENTSCHEIDUNG", Pn.Accent)
                    Text(sc.title, style = MaterialTheme.typography.titleMedium)
                    Text(sc.text, style = MaterialTheme.typography.bodyMedium)
                }
                sc.options.forEach { o -> ChoiceButton(o.text, null, icon = PnIcons.Chat) { vm.act { d, x -> Career.chooseScenario(d, x, o.id) } } }
                SmallNote("Danach fließen Michaels Vertrauen und deine letzte Leistungsbeurteilung als Urteil ein.")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Zeitplan-Assistent: Plan → Zeitraum → Bericht
// ---------------------------------------------------------------------------

@Composable
fun SimulationScreen(vm: GameViewModel, s: GameState) {
    var tab by remember { mutableStateOf(if (s.simReports.lastOrNull()?.endDay == s.clock.day) "report" else "plan") }
    val plan = Simulation.normalizedPlan(s)
    LaunchedEffect(s.simReports.size) { if (s.simReports.lastOrNull()?.endDay == s.clock.day) tab = "report" }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Zeitplan", Clock.dateLabel(s.clock.day) + " · " + Clock.slotNames[s.clock.slot], onBack = { vm.back() })
        PnTabs((listOf("plan" to "Plan", "run" to "Zeitraum") + if (s.simReports.isNotEmpty()) listOf("report" to "Bericht") else emptyList()), tab,
            Modifier.padding(horizontal = Sp.gutter, vertical = 8.dp)) { tab = it }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                "plan" -> {
                    item { SmallNote("Lege fest, wie dein Agent jeden Wochentag verbringt. Fortschritt entsteht nur aus diesen Tätigkeiten; Beziehungen nur durch tatsächliche Begegnungen.") }
                    items(7) { i ->
                        val act = Simulation.activity(plan[i])
                        GlassPanel(padding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(activityIcon(act.id), size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Simulation.weekdayNames[i].uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                                    Text(act.name, style = MaterialTheme.typography.titleSmall)
                                }
                                Text(if (act.energy > 0) "−${act.energy} E" else "Erholung", style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Simulation.activities.forEach { a ->
                                    FilterChip(selected = a.id == act.id, onClick = { vm.update { Simulation.setPlan(it, i, a.id) } },
                                        label = { Text(a.name) }, leadingIcon = { PnIcon(activityIcon(a.id), if (a.id == act.id) Pn.Accent else Pn.Muted, 16.dp) })
                                }
                            }
                            Text(act.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        val f = Simulation.forecast(s)
                        GlassPanel(accent = if (f.warning != null) Pn.Warn else Pn.Ok) {
                            Overline("WOCHENPROGNOSE", Pn.Accent)
                            KeyValue("Trainingstage", "${f.trainingDays}")
                            KeyValue("Ruhetage", "${f.restDays}")
                            KeyValue("Stress pro Woche", Effs.signed(f.stressPerWeek), if (f.stressPerWeek > 15) Pn.Danger else Pn.Text)
                            f.warning?.let { Text(it, color = Pn.Warn, style = MaterialTheme.typography.bodySmall) }
                            ActionButton("Weiter zum Zeitraum", Modifier.fillMaxWidth(), icon = PnIcons.Chevron) { tab = "run" }
                        }
                    }
                }
                "run" -> {
                    item {
                        GlassPanel {
                            Overline("ZEITRAUM WÄHLEN", Pn.Accent)
                            Text("Die Simulation hält bei wichtigen Ereignissen automatisch an: 18. Geburtstag, neue Story-Ereignisse, kritische Gesundheit, erfüllte Prüfungsauflagen.", style = MaterialTheme.typography.bodySmall)
                            KeyValue("Heute", Clock.dateLabel(s.clock.day))
                            KeyValue("Bis zum 18. Geburtstag", if (Ages.player(s) >= 18) "volljährig" else "${Clock.daysUntilAge(s.clock.day, 18)} Tage")
                            KeyValue("Gesundheit / Wunden", "${s.player.health} / ${s.player.wounds}")
                        }
                    }
                    item {
                        val opts = listOf(1 to "1 Tag", 7 to "1 Woche", 30 to "30 Tage", 90 to "90 Tage", 365 to "1 Jahr")
                        opts.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                row.forEach { (d, l) ->
                                    ActionButton(l, Modifier.weight(1f), secondary = d != 30, icon = PnIcons.Calendar) {
                                        vm.actAsync("Zeitraffer: $d Tage werden simuliert …") { dd, x -> Simulation.run(dd, x, d) }
                                        tab = "report"
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (Ages.player(s) < 18) ActionButton("Bis zum 18. Geburtstag", Modifier.fillMaxWidth(), subtitle = "${Clock.daysUntilAge(s.clock.day, 18)} Tage", icon = PnIcons.Star) {
                            vm.actAsync("Zeitraffer bis zum 18. Geburtstag …") { dd, x -> Simulation.run(dd, x, Clock.daysUntilAge(x.clock.day, 18).coerceAtLeast(1)) }
                            tab = "report"
                        }
                    }
                }
                else -> {
                    val r = s.simReports.lastOrNull()
                    if (r == null) item { SmallNote("Noch kein Bericht.") } else simReport(vm, s, r)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.simReport(vm: GameViewModel, s: GameState, r: SimReport) {
    val data = vm.data
    val days = r.endDay - r.startDay
    item {
        GlassPanel(accent = Pn.Warn) {
            Text("BERICHT · $days TAGE IN DER DIVISION", style = MaterialTheme.typography.labelSmall, color = Pn.Warn)
            Text(Clock.dateLabel(r.startDay) + " – " + Clock.dateLabel(r.endDay), style = MaterialTheme.typography.titleSmall)
            Text(r.stopReason, style = MaterialTheme.typography.bodyMedium, color = if (r.stopReason.startsWith("Zeitraum")) Pn.Text else Pn.Warn)
            if (days < r.plannedDays) SmallNote("Angehalten nach $days von ${r.plannedDays} geplanten Tagen.")
        }
    }
    item {
        GlassPanel {
            Overline("TRAINING", Pn.Accent)
            r.activityDays.forEach { (a, n) -> KeyValue(Simulation.activity(a).name, "$n Tage") }
            if (r.failedSessions > 0) KeyValue("Ohne Ergebnis (Erschöpfung/Stress/niemand da)", "${r.failedSessions}", Pn.Warn)
            if (r.attributeGains.isEmpty()) SmallNote("Keine Attributsteigerung in diesem Zeitraum.")
            r.attributeGains.forEach { (a, g) -> KeyValue(Attr.label(a), Effs.signed(g), if (g > 0) Pn.Ok else Pn.Danger) }
            KeyValue("Erfahrung", "+${r.xp} XP · Level ${r.levelBefore} → ${r.levelAfter}")
            if (r.examPrep > 0) KeyValue("Prüfungsvorbereitung", "+${r.examPrep}")
            r.trainerSessions.forEach { (t, n) -> KeyValue("Mit ${Relations.displayName(data, s, t)}", "$n Einheiten") }
        }
    }
    item {
        GlassPanel {
            Overline("VERFASSUNG & FINANZEN", Pn.Accent)
            KeyValue("Gesundheit", "${r.healthBefore} → ${r.healthAfter}")
            KeyValue("Wunden", "${r.woundsBefore} → ${r.woundsAfter}", if (r.woundsAfter < r.woundsBefore) Pn.Ok else Pn.Text)
            KeyValue("Stress", "${r.stressBefore} → ${r.stressAfter}")
            KeyValue("Einnahmen", "+${r.income} Cr", Pn.Ok)
            KeyValue("Ausgaben", "−${r.expenses} Cr", if (r.expenses > 0) Pn.Danger else Pn.Text)
            KeyValue("Bilanz", Effs.signed(r.income - r.expenses) + " Cr")
            if (r.reputation > 0) KeyValue("Ruf durch Dienst", "+${r.reputation}")
        }
    }
    if (r.relationChanges.isNotEmpty()) item {
        GlassPanel {
            Overline("BEGEGNUNGEN", Pn.Accent)
            r.relationChanges.entries.sortedByDescending { it.value }.forEach { (id, d) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    NpcPortrait(data, s, id, 30.dp, height = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(Relations.displayName(data, s, id), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("Freundschaft " + Effs.signed(d), color = if (d > 0) Pn.Ok else Pn.Danger, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    item {
        GlassPanel(accent = Pn.Muted) {
            Overline("EREIGNISPROTOKOLL", Pn.Muted)
            if (r.events.isEmpty()) SmallNote("Ruhige Zeit – keine besonderen Vorkommnisse.")
            r.events.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------------------
// Trainingsplaner: Ausbilder, Intensität, Ziele, Einheiten
// ---------------------------------------------------------------------------

@Composable
fun TrainingPlannerScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val present = World.npcsIn(data, s, s.location)
    val eligible = present.filter { Progression.partnerLock(data, s, it) == null }
    val trainers = eligible.filter { it in Progression.trainers.keys }
    var trainer by remember { mutableStateOf(trainers.firstOrNull()) }
    var partner by remember { mutableStateOf<String?>(null) }
    var intensity by remember { mutableStateOf("normal") }
    // Jeder anwesende, freie NPC kann Trainingspartner werden – auch Ausbilder,
    // wenn er nicht gleichzeitig den Ausbilderplatz belegt.
    val partners = eligible.filter { it != trainer }
        .sortedByDescending { Relations.get(data, s, it).friendship }
    val chosenPartner = partner?.takeIf { it in partners }
    val partnerBoost = Progression.partnerBoost(data, s, chosenPartner)
    val sessions = if (s.career.sessionsDay == s.clock.day) s.career.sessionsToday else 0
    val cap = Stats.attributeCap(s.player.level)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Trainingsplaner", data.roomById[s.location]?.name ?: "", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Overline("HEUTE", Pn.Accent)
                            Text("$sessions / ${Progression.DAILY_SESSIONS} volle Einheiten", style = MaterialTheme.typography.titleSmall)
                            Text("Danach nur noch geringer Fortschritt (Ermüdung).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Pill("Rating ohne Limit", Pn.Violet)
                    }
                    StatBar("Energie", s.player.energy, 100, Pn.Accent, icon = PnIcons.Bolt)
                    if (s.player.wounds > 0) Text("Verletzt: intensives Körpertraining gesperrt, Heilung ${s.player.woundProgress}/7 Tage.", color = Pn.Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                GlassPanel {
                    Overline("AUSBILDER", Pn.Accent)
                    if (trainers.isEmpty()) SmallNote("Gerade ist hier kein Ausbilder. Michael ist vormittags in der Trainingshalle, nachmittags am Präzisionsstand; Birkhoff und Sonya im Techniklabor.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        (listOf<String?>(null) + trainers).forEach { t ->
                            val sel = t == trainer
                            Column(
                                Modifier.width(84.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) Color(0x263FD6C4) else Color.Transparent)
                                    .border(1.dp, if (sel) Pn.Accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable { trainer = t }.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (t != null) NpcPortrait(data, s, t, 60.dp) else IconTile(PnIcons.Fist, Pn.Muted, 60.dp)
                                Text(if (t == null) "Allein" else Relations.displayName(data, s, t).substringBefore(" "), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                if (t != null) Text((Progression.trainers[t] ?: emptySet()).joinToString("/") { Attr.label(it).take(4) }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Pn.Muted, maxLines = 1)
                            }
                        }
                    }
                    Text("GEMEINSAMES TRAINING", style = MaterialTheme.typography.titleSmall)
                    if (partners.isEmpty()) {
                        SmallNote("Aktuell ist kein freier Trainingspartner hier. Triff Alex beispielsweise vormittags in der Trainingshalle oder nachmittags am Präzisionsstand.")
                    } else {
                        ChipRow(listOf("" to "Allein trainieren") + partners.map { it to Relations.displayName(data, s, it).substringBefore(" ") },
                            chosenPartner ?: "") { partner = it.ifBlank { null } }
                    }
                    if (chosenPartner != null && partnerBoost != null) {
                        val friendship = Relations.get(data, s, chosenPartner).friendship
                        GlassPanel(accent = if (friendship >= 50) Pn.Ok else Pn.Accent, padding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NpcPortrait(data, s, chosenPartner, 48.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Relations.displayName(data, s, chosenPartner), style = MaterialTheme.typography.titleSmall)
                                    Text("${partnerBoost.label} · Freundschaft $friendship/100",
                                        style = MaterialTheme.typography.bodySmall, color = Pn.Muted)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("Lernen ×${partnerBoost.progressMult}", Pn.Ok)
                                Pill("XP ×${partnerBoost.xpMult}", Pn.Accent)
                            }
                            Text("Ihr profitiert BEIDE: Deine Attribute und XP steigen schneller. ${Relations.displayName(data, s, chosenPartner).substringBefore(" ")} verbessert gleichzeitig die eigene Fähigkeit und Karriere.",
                                style = MaterialTheme.typography.bodySmall)
                            if (friendship < 50) SmallNote("Ab 50 Freundschaft: doppelter Fortschritt. Ab 80: alle sechs Attribute wachsen bei BEIDEN mit. Ab 95: noch stärker.")
                            else if (friendship >= 80) SmallNote("Spezialbonus: Auch die fünf nicht trainierten Attribute wachsen bei euch beiden mit.")
                            if (trainer != null && trainer != chosenPartner && Progression.trainerBonus(data, s, trainer, Attr.FITNESS) >= 0) {
                                val trainerBond = Life.bond(s, trainer, chosenPartner)
                                Text("Gemeinsames Training stärkt außerdem die Freundschaft zwischen " +
                                    Relations.displayName(data, s, trainer).substringBefore(" ") + " und " +
                                    Relations.displayName(data, s, chosenPartner).substringBefore(" ") +
                                    " (aktuell " + trainerBond + ").", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        SmallNote("Gemeinsames Training ist freiwillig. Ab Freundschaft 50 lernt ihr besonders schnell, ab 80 wachsen alle sechs Attribute und ab 95 gibt es den höchsten Bonus.")
                    }
                    Text("Intensität", style = MaterialTheme.typography.titleSmall)
                    ChipRow(Progression.intensities.map { it.id to it.name }, intensity) { intensity = it }
                    val iv = Progression.intensities.first { it.id == intensity }
                    SmallNote("Energie ×${iv.energyMult} · Fortschritt ×${iv.progressMult} · Verletzungsrisiko ${iv.injuryRisk} %")
                }
            }
            if (trainer != null && chosenPartner != null) item {
                GlassPanel(accent = Pn.Ok) {
                    Overline("TEAMAKTIVITÄTEN", Pn.Ok)
                    Text("Zusammenhalt aufbauen", style = MaterialTheme.typography.titleMedium)
                    Text("Du, " + Relations.displayName(data, s, trainer) + " und " +
                        Relations.displayName(data, s, chosenPartner) + " unternehmt etwas gemeinsam.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Beide Agenten verbessern ihre Freundschaft untereinander, bekommen Prüfungs-XP und machen eigene Fortschritte. Keine automatische Romanze.",
                        style = MaterialTheme.typography.bodySmall, color = Pn.Muted)
                    Progression.teamActivities.forEach { activity ->
                        ActionButton(activity.label + " · −" + activity.energy + " Energie · 1 Slot",
                            Modifier.fillMaxWidth(),
                            enabled = s.player.energy >= activity.energy && sessions <= Progression.DAILY_SESSIONS,
                            secondary = true, icon = PnIcons.People) {
                            vm.act { d, x -> Progression.teamActivity(d, x, trainer, chosenPartner, activity.id) }
                        }
                    }
                }
            }
            items(Progression.trainings) { t ->
                val iv = Progression.intensities.first { it.id == intensity }
                val cost = (t.energy * iv.energyMult).toInt()
                val cur = s.player.attributes[t.attribute] ?: 30
                val bonus = Progression.trainerBonus(data, s, trainer, t.attribute)
                val goal = s.career.goals[t.attribute]
                GlassPanel(padding = 12.dp, accent = if (bonus > 0 || partnerBoost != null) Pn.Ok else Pn.Accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            Text("${Attr.label(t.attribute)} $cur · Teilfortschritt ${(s.player.trainingProgress[t.attribute] ?: 0) * 2}%" + (goal?.let { " · Ziel $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (bonus > 0) Pill("Ausbilder +$bonus", Pn.Ok)
                        else if (partnerBoost != null) Pill("Team ×${partnerBoost.progressMult}", Pn.Ok)
                    }
                    StatBar("", cur, maxOf(100, cur), Pn.Accent, showValue = false)
                    ActionButton("Trainieren · −$cost Energie · 1 Slot", Modifier.fillMaxWidth(), enabled = s.player.energy >= cost && sessions <= Progression.DAILY_SESSIONS, secondary = true, icon = PnIcons.Fist) {
                        vm.act { d, x -> Progression.train(d, x, t.id, chosenPartner, trainer, intensity) }
                    }
                }
            }
        }
    }
}

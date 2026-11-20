package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

// ---------------------------------------------------------------------------
// Führungscockpit (v0.8)
// ---------------------------------------------------------------------------

@Composable
fun CommandScreen(vm: GameViewModel, s: GameState, initialTab: String = "team") {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Führungscockpit", Directorate.tierLabel(Directorate.tier(s)) + " · " + vm.data.rank(s.player.rank).name, onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PnTabs(listOf("team" to "Team", "tasks" to "Aufgaben", "delegation" to "Delegation", "staff" to "Personal", "sites" to "Standorte", "reports" to "Berichte"), tab) { tab = it }
            }
            if (!Command.isLeader(s) && tab != "reports") item {
                GlassPanel(accent = Pn.Warn) {
                    Text("Teamführung ab Rang 8 (Teamleiter).", style = MaterialTheme.typography.titleSmall)
                    Text("Bis dahin kannst du bereits Training für dein Team anstoßen und Berichte einsehen.", style = MaterialTheme.typography.bodySmall)
                }
            }
            when (tab) {
                "team" -> item { TeamPanel(vm, s) }
                "tasks" -> item { TasksPanel(vm, s) }
                "delegation" -> item { DelegationPanel(vm, s) }
                "staff" -> item { StaffPanel(vm, s) }
                "sites" -> item { SitesPanel(vm, s) }
                else -> item { ReportsPanel(vm, s) }
            }
        }
    }
}

@Composable
private fun StatusPill(st: Command.Status) =
    Pill(st.label, when (st.code) { "ok" -> Pn.Ok; "minor", "rank" -> Pn.Warn; "injured", "transferred" -> Pn.Danger; "mission", "task", "assigned" -> Pn.Violet; else -> Pn.Muted })

@Composable
private fun TeamPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel {
            Overline("TEAMLAGE", Pn.Accent)
            StatBar("Teamkohäsion", s.lead.cohesion, 100, Pn.Ok)
            StatBar("Bereitschaft", Command.teamReadiness(data, s), 100, Pn.Accent)
            KeyValue("Bilanz", "${s.lead.teamWins} Erfolg${if (s.lead.teamWins == 1) "" else "e"} · ${s.lead.teamLosses} Fehlschl${if (s.lead.teamLosses == 1) "ag" else "äge"}")
            KeyValue("Einsatzbonus aus Führung", Effs.signed(Command.missionBonus(data, s, s.team)))
            KeyValue("Vorbereitung bereit", "${s.lead.prepReady}")
            SmallNote("Rollen mit passender Kompetenz (≥ 4) und hohe Kohäsion erleichtern Proben im Einsatz; schlechte Stimmung im Team erschwert sie (höchstens −8).")
        }
        if (s.team.isEmpty()) SmallNote("Noch kein Team. Stelle es im Raum „Lobby“ oder über „Team“ im Profil zusammen.")
        s.team.forEach { id ->
            val st = Command.status(data, s, id)
            val l = Life.life(data, s, id)
            GlassPanel(padding = 12.dp, onClick = { vm.open(Overlay.Npc(id, "duty")) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NpcPortrait(data, s, id, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.titleSmall)
                        Text("${data.rank(l.rank).name} · Stress ${l.stress} · Stimmung ${l.mood}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(st)
                }
                if (Command.isLeader(s)) CompactChips(listOf("none" to "Keine Rolle") + Command.roles.map { it.id to "${it.label} ${Command.roleFit(data, s, id, it.id)}" }, s.lead.roles[id] ?: "none") { r ->
                    vm.act { d, x -> Command.setRole(d, x, id, if (r == "none") null else r) }
                }
            }
        }
        if (Command.isLeader(s) && s.team.size >= 2) {
            val pairs = s.team.flatMapIndexed { i, a -> s.team.drop(i + 1).map { b -> Triple(a, b, Life.bond(s, a, b)) } }.filter { it.third < 0 }.sortedBy { it.third }
            if (pairs.isNotEmpty()) GlassPanel(accent = Pn.Danger) {
                Overline("SPANNUNGEN IM TEAM", Pn.Danger)
                pairs.take(3).forEach { (a, b, v) ->
                    ChoiceButton("Aussprache: ${Relations.displayName(data, s, a).substringBefore(" ")} & ${Relations.displayName(data, s, b).substringBefore(" ")}", "Bindung $v · Empathie-Probe, 15 Energie") {
                        vm.act { d, x -> Command.mediate(d, x, a, b) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var npc by remember { mutableStateOf<String?>(null) }
    var mentor by remember { mutableStateOf<String?>(null) }
    val staff = Command.staff(data, s)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel {
            Overline("AUFGABE VERGEBEN", Pn.Accent)
            Text("Etat: ${Directorate.budget(s)} Cr · Energie ${s.player.energy}", style = MaterialTheme.typography.bodySmall)
            CompactChips(staff.map { it to Relations.displayName(data, s, it).substringBefore(" ") }, npc ?: "") { npc = it; mentor = null }
            npc?.let { id ->
                val st = Command.status(data, s, id, forOps = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); StatusPill(st)
                }
                Command.taskKinds.forEach { k ->
                    val reason = Command.canAssign(data, s, id, k.id, if (k.needsPartner) mentor else null)
                    if (k.needsPartner) {
                        val mentors = staff.filter { it != id && Ages.npc(data, s, it) >= 18 && Life.rank(data, s, it) > Life.rank(data, s, id) }
                        if (mentors.isNotEmpty()) CompactChips(mentors.map { it to "Mentor: " + Relations.displayName(data, s, it).substringBefore(" ") }, mentor ?: "") { mentor = it }
                    }
                    ChoiceButton("${k.label} · ${k.days} T.", reason ?: k.description, enabled = reason == null) {
                        vm.act { d, x -> Command.assign(d, x, id, k.id, if (k.needsPartner) mentor else null) }
                    }
                }
            } ?: SmallNote("Wähle eine Person aus deinem Personal.")
        }
        val active = s.lead.tasks.filter { !it.done }
        if (active.isNotEmpty()) GlassPanel(accent = Pn.Violet) {
            Overline("LAUFENDE AUFGABEN", Pn.Violet)
            active.forEach { t -> Text("${Relations.displayName(data, s, t.npcId).substringBefore(" ")}: ${Command.taskById[t.kind]?.label} bis ${Clock.dateLabel(t.endDay)}", style = MaterialTheme.typography.bodySmall) }
        }
        val done = s.lead.tasks.filter { it.done }.takeLast(6).reversed()
        if (done.isNotEmpty()) GlassPanel(accent = Pn.Muted) {
            Overline("ERGEBNISSE", Pn.Muted)
            done.forEach { Text("• ${it.result}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DelegationPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var pick by remember { mutableStateOf<String?>(null) }
    var team by remember { mutableStateOf(listOf<String>()) }
    var leader by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.player.rank < Command.DELEGATE_RANK) {
            GlassPanel(accent = Pn.Warn) { Text("Delegation echter Aufträge ab Rang 9 (Einsatzkommandant).", style = MaterialTheme.typography.bodyMedium) }
        }
        s.lead.delegations.filter { !DelegationStatus.closed(it.status) }.forEach { d ->
            GlassPanel(accent = if (d.status == DelegationStatus.RUNNING) Pn.Violet else Pn.Warn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Pill(DelegationStatus.label(d.status), if (d.status == DelegationStatus.RUNNING) Pn.Violet else Pn.Warn)
                }
                Text("Team: " + d.team.joinToString { Relations.displayName(data, s, it).substringBefore(" ") } + (d.leaderId?.let { " · Leitung " + Relations.displayName(data, s, it).substringBefore(" ") } ?: ""), style = MaterialTheme.typography.bodySmall)
                Text("Erfolgsaussicht ${d.chance} %" + if (d.status == DelegationStatus.RUNNING) " · Rückkehr ${Clock.dateLabel(d.returnDay)}" else "", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                d.report.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.status == DelegationStatus.PREPARED) ActionButton("Starten", Modifier.weight(1f)) { vm.act { dd, x -> Delegations.start(dd, x, d.id) } }
                    ActionButton(if (d.status == DelegationStatus.PREPARED) "Verwerfen" else "Abbrechen", Modifier.weight(1f), secondary = true,
                        subtitle = if (d.status == DelegationStatus.RUNNING) "Auftraggeber verärgert" else null) { vm.act { dd, x -> Delegations.abort(dd, x, d.id) } }
                }
            }
        }
        if (s.player.rank >= Command.DELEGATE_RANK) {
            SectionLabel("Delegierbare Aufträge")
            val list = Delegations.delegable(data, s)
            if (list.isEmpty()) SmallNote("Keine delegierbaren Aufträge. Ketten und Fallen bleiben Chefsache.")
            list.forEach { c ->
                val open = pick == c.id
                GlassPanel(padding = 12.dp, accent = if (open) Pn.Accent else Pn.Muted, onClick = { pick = if (open) null else c.id; team = emptyList(); leader = null }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("▮".repeat(c.difficulty), color = if (c.difficulty >= 4) Pn.Danger else Pn.Warn, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${Ops.regionLabel(data, c.regionId)} · Frist ${Clock.dateLabel(c.expiresDay)} · Dauer ${Delegations.duration(c)} T. · max. ${Delegations.maxTeam(data, s, c)} Personen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (open) {
                        val staff = Command.staff(data, s)
                        staff.forEach { id ->
                            val st = Command.status(data, s, id)
                            val sel = id in team
                            val usable = st.available && !st.trainingOnly
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) Color(0x223FD6C4) else Color.Transparent).padding(4.dp)) {
                                NpcPortrait(data, s, id, 32.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(Relations.displayName(data, s, id).substringBefore(" "), style = MaterialTheme.typography.bodyMedium)
                                    Text("Passung ${Ops.fit(data, s, c, id)}/10 · Rang ${Life.rank(data, s, id)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (usable) TextButton(onClick = { team = if (sel) team - id else team + id; if (sel && leader == id) leader = null }) { Text(if (sel) "Entfernen" else "Wählen") }
                                else StatusPill(st)
                            }
                        }
                        if (team.isNotEmpty()) CompactChips(listOf("" to "Ohne Leitung") + team.map { it to "Leitung: " + Relations.displayName(data, s, it).substringBefore(" ") }, leader ?: "") { leader = it.ifBlank { null } }
                        val reason = Delegations.validate(data, s, c, team, leader)
                        Text(if (team.isEmpty()) "Team wählen" else "Erfolgsaussicht ${Delegations.chance(data, s, c, team, leader)} %" + (reason?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium, color = if (reason == null) Pn.Ok else Pn.Warn)
                        ActionButton("Delegation vorbereiten", Modifier.fillMaxWidth(), enabled = reason == null) {
                            vm.act { d, x -> Delegations.prepare(d, x, c.id, team, leader) }; pick = null; team = emptyList(); leader = null
                        }
                    }
                }
            }
        }
        val closed = s.lead.delegations.filter { DelegationStatus.closed(it.status) }.takeLast(5).reversed()
        if (closed.isNotEmpty()) {
            SectionLabel("Abgeschlossen")
            closed.forEach { d ->
                GlassPanel(padding = 10.dp, accent = if (d.status == DelegationStatus.DONE) Pn.Ok else Pn.Danger) {
                    Row { Text(d.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Pill(DelegationStatus.label(d.status), if (d.status == DelegationStatus.DONE) Pn.Ok else Pn.Danger) }
                    d.report.takeLast(3).forEach { Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun StaffPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallNote("Personalakte 2.0: Tippen öffnet Kompetenzen, Beurteilung, Weiterbildung, Beförderung und Versetzung.")
        Command.staff(data, s).forEach { id ->
            val st = Command.status(data, s, id)
            val l = Life.life(data, s, id)
            val review = s.lead.reviews[id]
            GlassPanel(padding = 10.dp, onClick = { vm.open(Overlay.Npc(id, "duty")) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NpcPortrait(data, s, id, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.bodyMedium)
                        Text("${data.rank(l.rank).name} · ${Life.focusLabel(l.focus)}" + (review?.let { " · Note ${it.grade}" } ?: "") + (Personnel.assignmentOf(data, s, id)?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(st)
                }
            }
        }
    }
}

/** Abstrakte Stadtkarte (Compose Canvas) mit HQ und Außenstellen. */
@Composable
private fun SiteMap(s: GameState) {
    val pos = mapOf("hq" to Offset(0.5f, 0.5f), "safehouse" to Offset(0.28f, 0.35f), "analysis" to Offset(0.72f, 0.3f), "base" to Offset(0.7f, 0.72f), "harbor" to Offset(0.22f, 0.75f))
    Box(Modifier.fillMaxWidth().aspectRatio(1.8f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A1422))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 12
            var x = 0f
            while (x < size.width) { drawLine(Color(0x14FFFFFF), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color(0x14FFFFFF), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            drawLine(Color(0x334A90C8), Offset(0f, size.height * 0.88f), Offset(size.width * 0.45f, size.height * 0.95f), 18f)
            val hq = Offset(pos.getValue("hq").x * size.width, pos.getValue("hq").y * size.height)
            Sites.outposts.forEach { o ->
                val p = pos[o.id] ?: return@forEach
                val c = Offset(p.x * size.width, p.y * size.height)
                val st = s.lead.sites[o.id]
                val color = when { st == null -> Color(0x558DA2B6); !st.open -> Color(0x88EF6670); else -> Color(0xFF3FD6C4) }
                drawLine(color.copy(alpha = 0.5f), hq, c, 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                drawCircle(color, radius = 12f + (st?.level ?: 0) * 5f, center = c)
            }
            drawCircle(Color(0xFFF2B65C), radius = 20f, center = hq)
        }
        Text("HQ", Modifier.align(Alignment.Center).padding(top = 48.dp), style = MaterialTheme.typography.labelSmall, color = Pn.Warn)
    }
}

@Composable
private fun SitesPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var staffFor by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel {
            Overline("STANDORTE", Pn.Accent)
            SiteMap(s)
            KeyValue(if (s.director != null) "Organisationsbudget" else "Teametat", "${Directorate.budget(s)} Cr")
            SmallNote("Persönliche Credits werden nicht verwendet. Unterhalt wird wöchentlich abgerechnet.")
        }
        SectionLabel("HQ-Ausbau")
        Sites.modules.forEach { m ->
            val lvl = Sites.moduleLevel(s, m.id)
            GlassPanel(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.name} · Stufe $lvl/3", style = MaterialTheme.typography.titleSmall)
                        Text("Raum: ${data.roomById[m.roomId]?.name ?: m.roomId} · Unterhalt ${m.upkeep * lvl} Cr/Wo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(m.effect, style = MaterialTheme.typography.bodySmall)
                if (lvl < 3) ActionButton("Ausbauen · ${Sites.moduleCost(m, lvl)} Cr", Modifier.fillMaxWidth(), secondary = true,
                    enabled = s.player.rank >= m.minRank && Directorate.budget(s) >= Sites.moduleCost(m, lvl) && (lvl == 0 || s.player.rank >= 10),
                    subtitle = if (s.player.rank < m.minRank) "ab Rang ${m.minRank}" else if (lvl >= 1 && s.player.rank < 10) "weitere Stufen ab Rang 10" else null) {
                    vm.act { d, x -> Sites.upgradeModule(d, x, m.id) }
                }
            }
        }
        SectionLabel("Außenstellen")
        Sites.outposts.forEach { o ->
            val st = s.lead.sites[o.id]
            val eff = Sites.siteLevel(data, s, o.id)
            GlassPanel(padding = 12.dp, accent = when { st == null -> Pn.Muted; !st.open -> Pn.Danger; eff == 0 -> Pn.Warn; else -> Pn.Ok }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.name, style = MaterialTheme.typography.titleSmall)
                        Text("${Ops.regionLabel(data, o.region)} · ab Rang ${o.minRank}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pill(when { st == null -> "nicht eröffnet"; !st.open -> "geschlossen"; else -> "Stufe ${st.level} · wirksam $eff" }, when { st == null -> Pn.Muted; !st.open -> Pn.Danger; eff == 0 -> Pn.Warn; else -> Pn.Ok })
                }
                Text(o.description, style = MaterialTheme.typography.bodySmall)
                o.effects.forEachIndexed { i, e -> Text("${if ((st?.level ?: 0) > i) "●" else "○"} $e · Personal ${o.staffNeed[i]}", style = MaterialTheme.typography.labelSmall, color = if (eff > i) Pn.Ok else MaterialTheme.colorScheme.onSurfaceVariant) }
                when {
                    st == null || !st.open -> ActionButton(if (st == null) "Eröffnen · ${o.buildCost} Cr" else "Wiedereröffnen · ${o.buildCost / 4} Cr", Modifier.fillMaxWidth(),
                        enabled = s.player.rank >= o.minRank && Directorate.budget(s) >= (if (st == null) o.buildCost else o.buildCost / 4),
                        subtitle = if (s.player.rank < o.minRank) "ab Rang ${o.minRank}" else "Unterhalt ${o.upkeep} Cr/Wo. je Stufe") { vm.act { d, x -> Sites.build(d, x, o.id) } }
                    else -> {
                        st.staff.forEach { id ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NpcPortrait(data, s, id, 28.dp)
                                Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.act { d, x -> Sites.removeStaff(d, x, o.id, id) } }) { Text("Abziehen") }
                            }
                        }
                        if (staffFor == o.id) {
                            val cands = Command.staff(data, s).filter { Ages.npc(data, s, it) >= 18 && Command.status(data, s, it).let { c -> c.available && !c.trainingOnly } }
                            if (cands.isEmpty()) SmallNote("Niemand verfügbar (volljährig, frei, ohne anderen Posten).")
                            CompactChips(cands.map { it to "${Relations.displayName(data, s, it).substringBefore(" ")} ${Life.competence(data, s, it, o.area)}" }, "") { id -> vm.act { d, x -> Sites.assignStaff(d, x, o.id, id) }; staffFor = null }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("Personal", Modifier.weight(1f), secondary = true, enabled = st.staff.size < 3) { staffFor = if (staffFor == o.id) null else o.id }
                            ActionButton(if (st.level >= 3) "Max." else "Ausbau · ${o.buildCost * st.level}", Modifier.weight(1f), secondary = true,
                                enabled = st.level < 3 && Directorate.budget(s) >= o.buildCost * st.level && (st.level < 2 || s.player.rank >= 11)) { vm.act { d, x -> Sites.upgrade(d, x, o.id) } }
                            ActionButton("Schließen", Modifier.weight(1f), secondary = true) { vm.act { d, x -> Sites.close(d, x, o.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsPanel(vm: GameViewModel, s: GameState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val (income, costs) = Directorate.weeklyLines(vm.data, s)
        if (Command.isLeader(s)) GlassPanel(accent = Pn.Violet) {
            Overline("VORSCHAU NÄCHSTE WOCHE", Pn.Violet)
            income.forEach { KeyValue(it.label, "+${it.amount} Cr", Pn.Ok) }
            costs.forEach { KeyValue(it.label, "−${it.amount} Cr", Pn.Danger) }
            KeyValue("Saldo", Effs.signed(income.sumOf { it.amount } - costs.sumOf { it.amount }) + " Cr")
        }
        if (s.lead.reports.isEmpty()) SmallNote("Noch keine Wochen- oder Monatsberichte. Die erste Abrechnung erfolgt zum Wochenwechsel.")
        s.lead.reports.reversed().forEach { r ->
            GlassPanel(accent = if (r.kind == "month") Pn.Warn else Pn.Muted) {
                Text(r.title, style = MaterialTheme.typography.titleSmall)
                if (r.income > 0 || r.expenses > 0) Text("Einnahmen ${r.income} Cr · Ausgaben ${r.expenses} Cr · Stand ${r.budgetAfter} Cr", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                r.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (s.lead.log.isNotEmpty()) GlassPanel(accent = Pn.Muted) {
            Overline("FÜHRUNGSPROTOKOLL", Pn.Muted)
            s.lead.log.takeLast(10).reversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ---------------------------------------------------------------------------
// Direktorat (v0.8, auf Basis des bestehenden DirectorState)
// ---------------------------------------------------------------------------

@Composable
fun DirectorateScreen(vm: GameViewModel, s: GameState, initialTab: String = "depts") {
    val data = vm.data
    val d = s.director
    val tier = Directorate.tier(s)
    var tab by remember(s.lead.pendingDilemma) { mutableStateOf(if (s.lead.pendingDilemma != null) "decide" else initialTab) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Direktorat", if (d != null) "${Directorate.tierLabel(tier)} · Budget ${d.budget} Cr" else "ab Rang 10", onBack = { vm.back() })
        if (d == null) {
            Column(Modifier.padding(16.dp)) {
                GlassPanel {
                    Text("Sektionsleitung ab Rang 10, stellvertretende Direktion ab 11, Direktion ab 12.", style = MaterialTheme.typography.bodyMedium)
                    Text("Bis dahin verwaltest du als Teamleitung den Teametat im Führungscockpit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassPanel {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(Directorate.tierLabel(tier), Pn.Warn)
                        Pill("max. Stufe ${Directorate.maxLevel(s)}")
                        Pill("Projekte ${Directorate.activeProjects(s).size}/${Directorate.maxProjects(s)}", Pn.Violet)
                    }
                    StatBar("Moral des Personals", d.morale, 100, Pn.Ok)
                    StatBar("Rückhalt der Aufsicht", d.oversight, 100, Pn.Violet)
                    if (d.lastWeekReport.isNotBlank()) SmallNote("Letzte Woche: " + d.lastWeekReport)
                }
                Spacer(Modifier.height(6.dp))
                PnTabs(listOf("depts" to "Abteilungen", "budget" to "Haushalt", "projects" to "Projekte", "policy" to "Leitlinie", "agenda" to "Agenda", "decide" to "Entscheidung"), tab,
                    badges = if (s.lead.pendingDilemma != null) setOf("decide") else emptySet()) { tab = it }
            }
            when (tab) {
                "depts" -> {
                    if (tier == 1) item {
                        GlassPanel(accent = Pn.Warn) {
                            Text("Deine Sektion: " + (s.lead.sectionDept?.let { Director.deptName(it) } ?: "noch nicht gewählt"), style = MaterialTheme.typography.titleSmall)
                            CompactChips(Director.departments, s.lead.sectionDept ?: "") { vm.act { dd, x -> Directorate.chooseSection(dd, x, it) } }
                            SmallNote("Als Sektionsleitung verwaltest du eine Abteilung (bis Stufe 3), ihre Leitung und ihre Projekte.")
                        }
                    }
                    items(Director.departments) { (id, name) -> DeptCard(vm, s, d, id, name) }
                }
                "budget" -> item { BudgetPanel(vm, s) }
                "projects" -> items(Directorate.projects) { p -> ProjectCard(vm, s, p) }
                "policy" -> {
                    item { SmallNote(if (tier >= 2) "Leitlinien wirken jede Woche auf Moral, Aufsicht, Kosten und Einsätze." else "Leitlinien legt die stellvertretende Direktion bzw. Direktion fest.") }
                    items(Director.policies) { (id, name) ->
                        val allowed = id in Directorate.allowedPolicies(s)
                        ChoiceButton(name, Directorate.policyEffects[id] + if (!allowed) " · nicht in deiner Befugnis" else "", enabled = allowed && d.policy != id, selected = d.policy == id) {
                            vm.act { dd, x -> Directorate.setPolicy(dd, x, id) }
                        }
                    }
                }
                "agenda" -> item { AgendaPanel(vm, s) }
                else -> item { DilemmaPanel(vm, s) }
            }
        }
    }
}

/** v1.0: Direktionsagenda – langfristige, messbare Ziele mit echter Bewertung. */
@Composable
private fun AgendaPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val active = Agenda.active(s)
    var confirm by remember { mutableStateOf<String?>(null) }
    confirm?.let { id ->
        val def = Agenda.byId.getValue(id)
        PnDialog("Agenda „${def.name}“ beschließen?", "${def.description}\n\n${def.cost}\nBei Erfolg: ${def.reward}\nBei Verfehlen: ${def.penalty}",
            "Beschließen", onConfirm = { confirm = null; vm.act { d, x -> Agenda.choose(d, x, id) } }, onDismiss = { confirm = null })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (active != null) {
            val def = Agenda.byId[active.id]
            val goals = Agenda.goals(data, s)
            GlassPanel(accent = Pn.Violet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline("Laufende Agenda", Pn.Violet)
                        Text(def?.name ?: active.id, style = MaterialTheme.typography.titleMedium)
                    }
                    ProgressRing(Agenda.TERM_WEEKS - Agenda.weeksLeft(s), Agenda.TERM_WEEKS, 54.dp, Pn.Violet, "${Agenda.weeksLeft(s)} W.", stroke = 4.dp)
                }
                def?.let { Text(it.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                goals.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PnIcon(if (g.met) PnIcons.Check else PnIcons.Target, if (g.met) Pn.Ok else Pn.Warn, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(g.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${g.current} ${if (g.atMost) "≤" else "≥"} ${g.target}", style = PnType.data.copy(fontSize = 12.sp), color = if (g.met) Pn.Ok else Pn.Warn)
                    }
                }
                def?.let { InfoNote("Bei Erfolg: ${it.reward} · Bei Verfehlen: ${it.penalty}", Pn.Violet) }
                SmallNote("Bewertet wird genau einmal in der Wochenabrechnung nach Ablauf der Amtsperiode – auch im Zeitraffer.")
            }
        } else {
            val options = Agenda.available(s)
            if (options.isEmpty()) EmptyState(PnIcons.Flag, "Keine Agenda verfügbar", "Eine Direktionsagenda setzt die Direktionsebene ab Rang 10 voraus.")
            else {
                InfoNote("Wähle ein Ziel für die nächsten ${Agenda.TERM_WEEKS} Wochen. Die Wahl kostet sofort etwas – der Erfolg wird am Ende gemessen.")
                options.forEach { def ->
                    ChoiceButton(def.name, def.description + " · " + def.cost, icon = PnIcons.Flag) { confirm = def.id }
                }
            }
        }
        if (s.lead.agendaHistory.isNotEmpty()) {
            SectionLabel("Bisherige Agenden", trailing = "${s.lead.agendaHistory.count { it.success }}/${s.lead.agendaHistory.size} erfüllt")
            s.lead.agendaHistory.reversed().forEach { r ->
                GlassPanel(padding = 12.dp, accent = if (r.success) Pn.Ok else Pn.Danger) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Agenda.byId[r.id]?.name ?: r.id, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Pill(if (r.success) "erfüllt" else "verfehlt", if (r.success) Pn.Ok else Pn.Danger)
                    }
                    Text("Woche ${r.startWeek + 1}–${r.endWeek + 1} · ${r.summary}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DeptCard(vm: GameViewModel, s: GameState, d: DirectorState, id: String, name: String) {
    val data = vm.data
    val lvl = d.departments[id] ?: 1
    val manage = Directorate.canManage(s, id)
    var pickHead by remember { mutableStateOf(false) }
    GlassPanel(padding = 12.dp, accent = if (manage) Pn.Accent else Pn.Muted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$name · Stufe $lvl/5 · Stärke ${Directorate.power(data, s, id)}", style = MaterialTheme.typography.titleSmall)
                Text("Leitung: " + (d.heads[id]?.let { Relations.displayName(data, s, it) + " (Kompetenz ${Life.competence(data, s, it, Directorate.deptArea[id] ?: "intel")})" } ?: "vakant"),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(when (id) {
            "training" -> "Trainingsfortschritt +6 je Stärke über 1"
            "tech" -> "Projekte für Netz und Feldausrüstung"
            "medical" -> "Behandlung +5 je Stärke"
            "intel" -> "Start-Intel und Delegationschancen"
            else -> "Ab Stärke 3: Alarm in Einsätzen sinkt, Meridian-Offensive kostet nichts"
        }, style = MaterialTheme.typography.bodySmall)
        if (manage) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(if (lvl >= Directorate.maxLevel(s)) "Max. für Befugnis" else "Ausbau · ${Directorate.upgradeCost(lvl)}", Modifier.weight(1f), secondary = true,
                    enabled = lvl < Directorate.maxLevel(s) && d.budget >= Directorate.upgradeCost(lvl)) { vm.act { dd, x -> Directorate.upgrade(dd, x, id) } }
                ActionButton(if (d.heads[id] != null) "Leitung ändern" else "Leitung besetzen", Modifier.weight(1f), secondary = true) { pickHead = !pickHead }
            }
            if (pickHead) {
                val cands = Command.staff(data, s).filter { Ages.npc(data, s, it) >= 18 && Life.rank(data, s, it) >= 3 && (Personnel.assignmentOf(data, s, it) == null || d.heads[id] == it) }
                if (cands.isEmpty()) SmallNote("Niemand geeignet (volljährig, Rang ≥ 3, ohne anderen Posten).")
                CompactChips((if (d.heads[id] != null) listOf("" to "Vakant") else emptyList()) + cands.map { it to "${Relations.displayName(data, s, it).substringBefore(" ")} ${Life.competence(data, s, it, Directorate.deptArea[id] ?: "intel")}" }, d.heads[id] ?: "") {
                    vm.act { dd, x -> Directorate.assignHead(dd, x, id, it.ifBlank { null }) }; pickHead = false
                }
            }
        }
    }
}

@Composable
private fun BudgetPanel(vm: GameViewModel, s: GameState) {
    val (income, costs) = Directorate.weeklyLines(vm.data, s)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel {
            Text("WOCHENRECHNUNG (VORSCHAU)", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            income.forEach { KeyValue(it.label, "+${it.amount} Cr", Pn.Ok) }
            costs.forEach { KeyValue(it.label, "−${it.amount} Cr", Pn.Danger) }
            KeyValue("Saldo", Effs.signed(income.sumOf { it.amount } - costs.sumOf { it.amount }) + " Cr")
            SmallNote("Organisationsbudget und persönliche Credits sind getrennt. Reicht das Budget nicht, pausieren Projekte und die Moral sinkt.")
        }
        if (Directorate.tier(s) in 1..2) GlassPanel(accent = Pn.Violet) {
            Overline("HAUSHALTSANTRAG", Pn.Violet)
            if (s.lead.budgetRequest > 0) Text("In Prüfung: ${s.lead.budgetRequest} Cr – Entscheidung zum Wochenwechsel.", style = MaterialTheme.typography.bodySmall)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3000, 8000, 15000).forEach { a -> ActionButton("$a", Modifier.weight(1f), secondary = true, subtitle = "Cr") { vm.act { d, x -> Directorate.requestBudget(d, x, a) } } }
            }
            SmallNote("Erfolg hängt von Aufsicht, Percys Vertrauen und der Höhe ab. Bewilligung kostet etwas Aufsicht.")
        }
        ReportsPanel(vm, s)
    }
}

@Composable
private fun ProjectCard(vm: GameViewModel, s: GameState, p: Directorate.Project) {
    val running = s.lead.projects.firstOrNull { it.id == p.id && !it.finished }
    val done = "proj_${p.id}" in s.flags
    GlassPanel(padding = 12.dp, accent = when { done -> Pn.Ok; running != null -> Pn.Violet; else -> Pn.Muted }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall)
                Text("${Director.deptName(p.dept)} · ${p.weeks} Wochen · ${p.weeklyCost} Cr/Wo. · ab ${Directorate.tierLabel(p.minTier)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(when { done -> "Abgeschlossen"; running != null -> "Woche ${running.weeksDone}/${p.weeks}"; else -> "Verfügbar" }, when { done -> Pn.Ok; running != null -> Pn.Violet; else -> Pn.Muted })
        }
        Text(p.description, style = MaterialTheme.typography.bodySmall)
        if (running != null) {
            StatBar("Fortschritt", running.weeksDone, p.weeks, Pn.Violet, showValue = false)
            ActionButton("Projekt beenden", Modifier.fillMaxWidth(), secondary = true, subtitle = "Moral −2, Kosten verloren") { vm.act { d, x -> Directorate.cancelProject(d, x, p.id) } }
        } else if (!done) ActionButton("Starten", Modifier.fillMaxWidth(), secondary = true,
            enabled = Directorate.tier(s) >= p.minTier && Directorate.canManage(s, p.dept) && Directorate.activeProjects(s).size < Directorate.maxProjects(s)) {
            vm.act { d, x -> Directorate.startProject(d, x, p.id) }
        }
    }
}

@Composable
private fun DilemmaPanel(vm: GameViewModel, s: GameState) {
    val d = Directorate.pendingDilemma(s)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d == null) SmallNote("Keine Entscheidung offen. Führungsdilemmata entstehen zum Wochenwechsel aus der Lage der Division.")
        else {
            GlassPanel(accent = Pn.Warn) {
                Overline("FÜHRUNGSENTSCHEIDUNG", Pn.Warn)
                Text(d.title, style = MaterialTheme.typography.titleMedium)
                Text(d.text, style = MaterialTheme.typography.bodyMedium)
                if (d.id == "staff_dispute") Directorate.disputeParties(vm.data, s)?.let { (a, b) ->
                    Text("Beteiligt: ${Relations.displayName(vm.data, s, a)} und ${Relations.displayName(vm.data, s, b)}", style = MaterialTheme.typography.labelMedium, color = Pn.Accent)
                }
            }
            d.choices.forEach { c ->
                val detail = listOfNotNull(
                    c.budget.takeIf { it != 0 }?.let { "Budget ${Effs.signed(it)}" }, c.morale.takeIf { it != 0 }?.let { "Moral ${Effs.signed(it)}" },
                    c.oversight.takeIf { it != 0 }?.let { "Aufsicht ${Effs.signed(it)}" }, c.check?.let { "Probe ${Attr.label(it.attribute)}" },
                    c.fallout?.let { "mögliche Spätfolgen" }
                ).joinToString(" · ")
                ChoiceButton(c.text, detail.ifBlank { null }, chance = c.check?.let { Stats.checkChance(vm.data, s, it) }) { vm.act { dd, x -> Directorate.resolveDilemma(dd, x, c.id) } }
            }
        }
        val history = s.lead.dilemmaHistory.entries.sortedByDescending { it.value }.take(5)
        if (history.isNotEmpty()) GlassPanel(accent = Pn.Muted) {
            Overline("FRÜHERE ENTSCHEIDUNGEN", Pn.Muted)
            history.forEach { (k, day) -> Text("${Clock.dateLabel(day)}: ${Directorate.dilemmaById[k.substringBefore("#")]?.title}", style = MaterialTheme.typography.bodySmall) }
        }
        if (s.lead.fallout.isNotEmpty()) SmallNote("Ausstehende Folgen: ${s.lead.fallout.size}")
    }
}

// ---------------------------------------------------------------------------
// Stadt & Privatleben (v0.8)
// ---------------------------------------------------------------------------

@Composable
fun CityScreen(vm: GameViewModel, s: GameState, initialTab: String = "home") {
    val data = vm.data
    var tab by remember(initialTab, s.life.pendingEvent) { mutableStateOf(if (s.life.pendingEvent != null) "event" else initialTab) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Stadt & Privatleben", "${Home.housingName(s.life.housing)} · ${s.player.credits} Cr · ${Clock.slotNames[s.clock.slot]}", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassPanel(padding = 12.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Energie ${s.player.energy}")
                        Pill("Stress ${s.player.stress}", if (s.player.stress > 60) Pn.Danger else Pn.Warn)
                        Pill("Freizeit ${if (s.life.leisureDay == s.clock.day) s.life.leisureToday else 0}/${Home.LEISURE_PER_DAY}", Pn.Violet)
                    }
                    SmallNote("Freizeit kostet Zeit, Energie und Geld – und konkurriert mit Training, Einsätzen und Führungsaufgaben.")
                }
                Spacer(Modifier.height(6.dp))
                PnTabs(listOf("home" to "Wohnung", "city" to "Stadt", "meet" to "Treffen", "job" to "Tarnjob", "event" to "Ereignis"), tab,
                    badges = if (s.life.pendingEvent != null) setOf("event") else emptySet()) { tab = it }
            }
            when (tab) {
                "home" -> item { HousingPanel(vm, s) }
                "city" -> items(Home.activities) { a ->
                    val reason = Home.canDo(data, s, a.id)
                    ChoiceButton(a.name + (a.hobby?.let { h -> " · ${h} ${s.life.hobbies[h] ?: 0}" } ?: ""),
                        reason ?: (a.description + " · " + listOfNotNull(a.cost.takeIf { it > 0 }?.let { "$it Cr" }, a.energy.takeIf { it > 0 }?.let { "Energie −$it" }, "Stress ${Effs.signed(a.stress)}").joinToString(" · ")),
                        enabled = reason == null) { vm.act { d, x -> Home.doActivity(d, x, a.id) } }
                }
                "meet" -> {
                    val list = Home.meetingsFor(data, s)
                    if (list.isEmpty()) item { SmallNote("Noch niemand, mit dem du dich privat treffen kannst.") }
                    items(list) { m ->
                        val reason = Home.canMeet(data, s, m)
                        GlassPanel(padding = 12.dp, accent = if (reason == null) Pn.Accent else Pn.Muted) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NpcPortrait(data, s, m.npcId, 44.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                                    Text(reason ?: m.description, style = MaterialTheme.typography.labelSmall, color = if (reason == null) MaterialTheme.colorScheme.onSurfaceVariant else Pn.Warn)
                                }
                            }
                            if (reason == null) ActionButton("Treffen" + (if (m.cost > 0) " · ${m.cost} Cr" else ""), Modifier.fillMaxWidth(), secondary = true) { vm.act { d, x -> Home.meet(d, x, m.id) } }
                        }
                    }
                    item { SmallNote("Treffen sind freundschaftlich. Romantische Schritte folgen ausschließlich den bestehenden Beziehungsregeln in der Personalakte.") }
                }
                "job" -> item { JobPanel(vm, s) }
                else -> item { LifeEventPanel(vm, s) }
            }
            if (s.life.log.isNotEmpty()) item {
                GlassPanel(accent = Pn.Muted) {
                    Overline("PRIVATES TAGEBUCH", Pn.Muted)
                    s.life.log.takeLast(6).reversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun HousingPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val h = Home.housing(s)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel(accent = Pn.Ok) {
            Overline("DEINE UNTERKUNFT", Pn.Ok)
            Text(h.name, style = MaterialTheme.typography.titleMedium)
            Text(h.description, style = MaterialTheme.typography.bodySmall)
            KeyValue("Miete", if (h.rent > 0) "${h.rent} Cr/Woche" else "keine")
            KeyValue("Schlaf", "Energie +${Home.sleepEnergyBonus(s)} · Stress −${Home.sleepStressBonus(s)}")
            KeyValue("Einrichtung", "${s.life.furniture.size}/${h.slots} Plätze")
            if (s.life.missedRent > 0) Text("Mietrückstand: Mahnung ${s.life.missedRent}/2", style = MaterialTheme.typography.labelMedium, color = Pn.Danger)
        }
        SectionLabel("Unterkünfte")
        Home.housings.forEach { o ->
            val reason = Home.canMove(data, s, o.id)
            ChoiceButton(o.name + if (o.id == s.life.housing) "  ✓" else "", if (o.id == s.life.housing) "aktuell" else (reason ?: "Kaution ${o.deposit} Cr · ${o.rent} Cr/Woche · ${o.slots} Möbelplätze"),
                enabled = reason == null) { vm.act { d, x -> Home.move(d, x, o.id) } }
        }
        if (h.slots > 0) {
            SectionLabel("Einrichtung")
            Home.furniture.forEach { f ->
                val owned = f.id in s.life.furniture
                ChoiceButton(f.name + if (owned) "  ✓" else " · ${f.cost} Cr", f.effect + if (owned) " · Tippen: verkaufen (+${f.cost / 3} Cr)" else "",
                    enabled = owned || (s.life.furniture.size < h.slots && s.player.credits >= f.cost)) {
                    vm.act { d, x -> if (owned) Home.sellFurniture(x, f.id) else Home.buyFurniture(d, x, f.id) }
                }
            }
        }
        if (s.life.hobbies.isNotEmpty()) GlassPanel {
            Overline("HOBBYS", Pn.Accent)
            s.life.hobbies.entries.sortedByDescending { it.value }.forEach { (h2, v) -> StatBar(h2 + if (h2 in s.player.interests) " ★" else "", v.coerceAtMost(10), 10, Pn.Violet) }
            SmallNote("Ab Stufe 3 wird ein Hobby zu einem Interesse – das verbessert die Passung zu Figuren mit denselben Interessen.")
        }
    }
}

@Composable
private fun JobPanel(vm: GameViewModel, s: GameState) {
    val week = Clock.week(s.clock.day)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val job = s.life.coverJob?.let { Home.jobById[it] }
        GlassPanel(accent = if (job != null) Pn.Accent else Pn.Muted) {
            Overline("TARNJOB", Pn.Accent)
            if (job == null) Text("Kein Tarnjob. Eine zivile Identität hilft bei Undercover-Legenden und bringt etwas Geld.", style = MaterialTheme.typography.bodySmall)
            else {
                Text(job.name, style = MaterialTheme.typography.titleMedium)
                KeyValue("Lohn", "${job.pay} Cr/Woche (halb ohne Schicht)")
                KeyValue("Schicht diese Woche", if (s.life.shiftWeek == week) "erledigt" else "offen", if (s.life.shiftWeek == week) Pn.Ok else Pn.Warn)
                KeyValue("Verwarnungen", "${s.life.jobWarnings}/3")
                ActionButton("Schicht arbeiten", Modifier.fillMaxWidth(), enabled = s.life.shiftWeek != week && s.player.energy >= 20, subtitle = "2 Zeitslots · Energie −20") { vm.act { d, x -> Home.workShift(d, x) } }
                ActionButton("Kündigen", Modifier.fillMaxWidth(), secondary = true) { vm.act { d, x -> Home.takeJob(d, x, null) } }
            }
        }
        if (job == null) Home.jobs.forEach { j ->
            ChoiceButton("${j.name} · ${j.pay} Cr/Woche", "ab ${j.minAge} · Voraussetzung: ${j.requirement}", enabled = Ages.player(s) >= j.minAge) { vm.act { d, x -> Home.takeJob(d, x, j.id) } }
        }
    }
}

@Composable
private fun LifeEventPanel(vm: GameViewModel, s: GameState) {
    val e = Home.pending(s)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (e == null) SmallNote("Gerade passiert nichts Besonderes. Alltagsereignisse entstehen bei Freizeitaktivitäten und zu Hause.")
        else {
            GlassPanel(accent = Pn.Warn) {
                Overline("ALLTAG", Pn.Warn)
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                if (e.npcId != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NpcPortrait(vm.data, s, e.npcId, 36.dp); Text(Relations.displayName(vm.data, s, e.npcId), style = MaterialTheme.typography.labelLarge)
                }
                Text(e.text, style = MaterialTheme.typography.bodyMedium)
                SmallNote("Ohne Reaktion verfällt das Ereignis nach drei Tagen.")
            }
            e.choices.forEach { c ->
                val ok = Home.choiceAvailable(s, c)
                ChoiceButton(c.text, if (!ok) "Benötigt: ${Home.furnitureById[c.needsFurniture]?.name}" else listOfNotNull(c.credits.takeIf { it != 0 }?.let { "${Effs.signed(it)} Cr" }, c.stress.takeIf { it != 0 }?.let { "Stress ${Effs.signed(it)}" }).joinToString(" · ").ifBlank { null },
                    enabled = ok) { vm.act { d, x -> Home.resolve(d, x, c.id) } }
            }
        }
    }
}

/** HQ-Karte: offene Führungsentscheidung, laufende Delegationen, Alltagsereignis. */
@Composable
fun LeadershipHqCard(vm: GameViewModel, s: GameState) {
    val dil = Directorate.pendingDilemma(s)
    val running = s.lead.delegations.count { it.status == DelegationStatus.RUNNING }
    val ev = Home.pending(s)
    if (dil == null && running == 0 && ev == null && !Command.isLeader(s)) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dil != null) GlassPanel(accent = Pn.Warn, onClick = { vm.open(Overlay.Director) }) {
            Overline("FÜHRUNGSENTSCHEIDUNG", Pn.Warn)
            Text(dil.title, style = MaterialTheme.typography.titleMedium)
        }
        if (Command.isLeader(s)) GlassPanel(onClick = { vm.open(Overlay.Command("team")) }) {
            Overline("FÜHRUNG", Pn.Accent)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Kohäsion ${s.lead.cohesion}", Pn.Ok)
                if (running > 0) Pill("$running Delegation${if (running == 1) "" else "en"}", Pn.Violet)
                Pill("Etat ${Directorate.budget(s)}", Pn.Warn)
            }
        }
        if (ev != null) GlassPanel(accent = Pn.Rose, onClick = { vm.open(Overlay.City("event")) }) {
            Overline("PRIVAT", Pn.Rose)
            Text(ev.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------------------
// Personalakte 2.0 – Reiter „Dienst“ in der Personendetailansicht
// ---------------------------------------------------------------------------

@Composable
fun PersonnelFile(vm: GameViewModel, s: GameState, id: String) {
    val data = vm.data
    val l = Life.life(data, s, id)
    val st = Command.status(data, s, id)
    val inStaff = id in Command.staff(data, s)
    val age = Ages.npc(data, s, id)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DIENSTSTATUS", style = MaterialTheme.typography.labelSmall, color = Pn.Accent, modifier = Modifier.weight(1f))
                StatusPill(st)
            }
            KeyValue("Rang", data.rank(l.rank).name)
            KeyValue("Schwerpunkt", Life.focusLabel(l.focus))
            KeyValue("Einsätze", "${l.missions} (davon ${l.missionsFailed} gescheitert)")
            KeyValue("Bereitschaft", "${Command.readiness(data, s, id)} %")
            Personnel.assignmentOf(data, s, id)?.let { KeyValue("Posten", it) }
            s.lead.roles[id]?.let { r -> KeyValue("Teamrolle", Command.roleById[r]?.label ?: r) }
            if (age < 18) SmallNote("Minderjährig: nur Training, Mentoring und Ausbildung – keine Leitungsposten und keine gefährlichen Einsätze.")
        }
        GlassPanel {
            Overline("KOMPETENZEN", Pn.Accent)
            Life.focusAreas.forEach { a -> StatBar(Life.focusLabel(a), Life.competence(data, s, id, a), 10, if (a == l.focus) Pn.Accent else Pn.Muted) }
        }
        s.lead.reviews[id]?.let { r ->
            GlassPanel(accent = when (r.grade) { "A", "B" -> Pn.Ok; "C" -> Pn.Warn; else -> Pn.Danger }) {
                Text("LETZTE BEURTEILUNG · ${Clock.dateLabel(r.day)}", style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
                Text("Note ${r.grade}", style = MaterialTheme.typography.titleMedium)
                Text(r.note, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!inStaff) SmallNote("Personalentscheidungen sind nur für Figuren der Division möglich, die deinem Befehl unterstehen.")
        else if (!Command.isLeader(s)) SmallNote("Beurteilungen, Aufgaben und Personalentscheidungen ab Rang 8.")
        else {
            SectionLabel("Personalentscheidungen")
            ChoiceButton("Leistungsbeurteilung", "Alle 30 Tage · wirkt auf Karrierepunkte und Stimmung") { vm.act { d, x -> Personnel.review(d, x, id) } }
            listOf("training", "course", "recovery").forEach { k ->
                val kind = Command.taskById.getValue(k)
                val reason = Command.canAssign(data, s, id, k)
                ChoiceButton("${kind.label} · ${kind.days} T.", reason ?: kind.description, enabled = reason == null) { vm.act { d, x -> Command.assign(d, x, id, k) } }
            }
            if (s.player.rank >= 10) {
                ChoiceButton("Beförderung empfehlen", "Ab 70 % der nötigen Karrierepunkte: Prüfung wird sofort zugelassen") { vm.act { d, x -> Personnel.recommend(d, x, id) } }
                ChoiceButton("Versetzen (60 Tage)", "Loyalität −10 · Kohäsion sinkt, falls im Team · Begleiter:innen entscheiden selbst") { vm.act { d, x -> Personnel.transfer(d, x, id) } }
            }
            ActionButton("Im Führungscockpit öffnen", Modifier.fillMaxWidth(), secondary = true) { vm.open(Overlay.Command("tasks")) }
        }
    }
}

package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

/** v0.7 Mission Control: Aufträge, Kampagne, Ketten, Weltlage, Akten, Berichte. */
@Composable
fun MissionControlScreen(vm: GameViewModel, s: GameState, initialTab: String = "board", showTitle: Boolean = true) {
    val data = vm.data
    var tab by remember { mutableStateOf(initialTab) }
    // Sicherheitsnetz für sehr alte Spielstände: Auftragslage einmalig erzeugen (sonst passiert das beim Tageswechsel/Laden)
    LaunchedEffect(s.clock.day, s.phase) { if (s.phase == "game" && s.ops.lastGenDay < 0) vm.update { Ops.refresh(data, it) } }
    val readyStory = data.missions.count { it.id !in s.completedMissions && Missions.lockReason(data, s, it) == null }
    val openCases = s.ops.cases.values.count { !it.solved }
    LazyColumn(contentPadding = PaddingValues(start = Sp.gutter, end = Sp.gutter, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            if (showTitle) {
                Overline("Einsatzzentrale")
                Text("MISSION CONTROL", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
            }
            PnTabs(listOf("board" to "Aufträge", "story" to "Kampagne", "chains" to "Ketten", "world" to "Weltlage", "journal" to "Akten", "reports" to "Berichte"), tab,
                badges = buildSet { if (readyStory > 0) add("story"); if (s.ops.contracts.any { it.urgency >= 3 }) add("board"); if (openCases > 0) add("journal") }) { tab = it }
        }
        when (tab) {
            "board" -> boardItems(vm, s)
            "story" -> data.missions.groupBy { it.chapter }.forEach { (chapter, list) ->
                item(key = "h_$chapter") {
                    val done = list.count { it.id in s.completedMissions }
                    Column {
                        SectionLabel(chapter.ifBlank { "Aufträge" }, trailing = "$done/${list.size}", icon = PnIcons.Flag)
                        Spacer(Modifier.height(4.dp))
                        StatBar("Fortschritt", done, list.size, if (done == list.size) Pn.Ok else Pn.Accent, showValue = false)
                    }
                }
                items(list, key = { it.id }) { m -> StoryMissionCard(vm, s, m) }
            }
            "chains" -> items(data.chains, key = { it.id }) { c -> ChainCard(vm, s, c) }
            "world" -> item { WorldPanel(vm, s) }
            "journal" -> {
                val cases = Cases.all.filter { s.ops.cases.containsKey(it.id) }
                if (cases.isEmpty()) item { EmptyState(PnIcons.Search, "Keine offenen Akten", "Fälle öffnen sich mit der Zeit und durch Hinweise aus erfüllten Aufträgen.") }
                items(cases, key = { it.id }) { c -> CaseCard(vm, s, c) }
            }
            else -> {
                if (s.reports.isEmpty()) item { EmptyState(PnIcons.Archive, "Noch keine Einsatzberichte", "Nach jeder Nachbesprechung erscheint hier der Bericht mit Belohnungen und Folgen.") }
                items(s.reports.reversed()) { r -> ReportCard(r) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.boardItems(vm: GameViewModel, s: GameState) {
    item { BoardFilters(vm, s) }
}

data class BoardFilter(val region: String = "all", val type: String = "all", val tag: String = "all")

fun filterContracts(s: GameState, f: BoardFilter): List<Contract> = s.ops.contracts.filter { c ->
    (f.region == "all" || c.regionId == f.region) && (f.type == "all" || c.archetype == f.type) && when (f.tag) {
        "urgent" -> c.urgency >= 2
        "easy" -> c.difficulty <= 2
        "hard" -> c.difficulty >= 4
        "team" -> c.maxTeam > 1
        "short" -> c.expiresDay - s.clock.day <= 2
        "multi" -> c.phases > 1
        "chain" -> c.chainId != null
        else -> true
    }
}.sortedWith(compareBy({ -it.urgency }, { it.expiresDay }, { it.id }))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardFilters(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var f by remember { mutableStateOf(BoardFilter()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val staff = Life.presenceSummary(data, s)
        val avail = Missions.teamCandidates(data, s, MissionDefinition("probe", "", "hq", "x", threat = 1)).size
        var open by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Aufträge", "${s.ops.contracts.size}", Modifier.weight(1f), PnIcons.Target)
            StatTile("Team bereit", "$avail", Modifier.weight(1f), PnIcons.People, Pn.Ok)
            StatTile("Ausfälle", "${(staff[LifeStatus.INJURED]?.size ?: 0) + (staff[LifeStatus.MISSION]?.size ?: 0)}", Modifier.weight(1f), PnIcons.Cross, Pn.Warn)
        }
        GlassPanel(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { PnIcon(PnIcons.Filter, size = 15.dp); Spacer(Modifier.width(6.dp)); Overline("Filter") }
            CompactChips(listOf("all" to "Alles", "urgent" to "Dringend", "short" to "Frist ≤ 2 T.", "multi" to "Mehrtägig", "team" to "Teamauftrag", "easy" to "Gefahr ≤ 2", "hard" to "Gefahr ≥ 4", "chain" to "Kette"), f.tag) { f = f.copy(tag = it) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 4.dp)) {
                val active = listOfNotNull(f.region.takeIf { it != "all" }?.let { Ops.regionLabel(data, it) }, f.type.takeIf { it != "all" }?.let { Ops.archetypeById[it]?.label })
                Text(if (active.isEmpty()) "Ort & Einsatzart filtern" else "Filter: " + active.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Pn.Accent, modifier = Modifier.weight(1f))
                Text(if (open) "▲" else "▼", color = Pn.Accent)
            }
            if (open) {
                CompactChips(listOf("all" to "Alle Orte") + Ops.regions.filter { r -> s.ops.contracts.any { it.regionId == r } }.map { it to Ops.regionLabel(data, it) }, f.region) { f = f.copy(region = it) }
                CompactChips(listOf("all" to "Alle Arten") + Ops.archetypes.filter { a -> s.ops.contracts.any { it.archetype == a.id } }.map { it.id to it.label }, f.type) { f = f.copy(type = it) }
            }
        }
        val list = filterContracts(s, f)
        if (list.isEmpty()) EmptyState(PnIcons.Radar, if (s.ops.contracts.isEmpty()) "Keine offenen Aufträge" else "Kein Treffer", if (s.ops.contracts.isEmpty()) "Neue Aufträge treffen täglich ein – zum Beispiel nach dem Schlafen oder im Zeitplan." else "Kein Auftrag passt zu diesen Filtern.",
            if (s.ops.contracts.isEmpty()) null else "Filter zurücksetzen", if (s.ops.contracts.isEmpty()) null else ({ f = BoardFilter() }))
        list.forEach { c -> ContractCard(vm, s, c) }
        if (Ages.player(s) < 18) SmallNote("Unter 18: nur Observation, Aufklärung aus der Distanz, Verhandlung, Datenauswertung und Informationsbeschaffung ohne Taktikbrett, Gefahrenstufe ≤ 2.")
        val history = s.ops.history.takeLast(8).reversed()
        if (history.isNotEmpty()) {
            SectionLabel("Verlauf", trailing = "${s.ops.history.size}")
            history.forEach { h ->
                GlassPanel(padding = 10.dp, accent = statusTone(h.status)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Pill(ContractStatus.label(h.status), statusTone(h.status))
                    }
                    Text(Clock.dateLabel(h.day) + (h.notes.firstOrNull()?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

fun statusTone(status: String): Color = when (status) {
    ContractStatus.DONE -> Pn.Ok
    ContractStatus.FAILED, ContractStatus.EXPIRED -> Pn.Danger
    ContractStatus.IN_PROGRESS, ContractStatus.POSTPONED -> Pn.Warn
    else -> Pn.Muted
}

@Composable
fun ContractCard(vm: GameViewModel, s: GameState, c: Contract) {
    val data = vm.data
    val arch = Ops.archetypeById[c.archetype]
    val left = c.expiresDay - s.clock.day
    val lock = Ops.lockReason(data, s, c)
    val tone = when { c.urgency >= 3 -> Pn.Danger; c.chainId != null -> Pn.Violet; c.urgency == 2 -> Pn.Warn; else -> Pn.Accent }
    GlassPanel(accent = tone, onClick = { vm.open(Overlay.Briefing(c.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (c.clientNpc != null && Relations.isKnownNpc(data, s, c.clientNpc) && ("met_${c.clientNpc}" in s.flags || s.relations.containsKey(c.clientNpc))) NpcPortrait(data, s, c.clientNpc, 44.dp)
            else IconTile(PnIcons.forMission(Ops.typeFor(c.archetype)), tone, 44.dp)
            Column(Modifier.weight(1f)) {
                Text((arch?.label ?: c.archetype).uppercase() + " · " + Ops.regionLabel(data, c.regionId), style = MaterialTheme.typography.labelSmall, color = tone, maxLines = 1)
                Text(c.title.substringAfter(": ").ifBlank { c.title }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            Text("▮".repeat(c.difficulty) + "▯".repeat((5 - c.difficulty).coerceAtLeast(0)), color = if (c.difficulty >= 4) Pn.Danger else Pn.Warn, style = MaterialTheme.typography.labelSmall)
        }
        Text(c.briefing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (left <= 0) "Frist heute" else "Frist $left T.", if (left <= 1) Pn.Danger else Pn.Muted)
            if (c.urgency >= 2) Pill(if (c.urgency == 3) "Kritisch" else "Dringend", if (c.urgency == 3) Pn.Danger else Pn.Warn)
            if (c.phases > 1) Pill("Tag ${c.phase + 1}/${c.phases}", Pn.Violet)
            Pill("${c.credits} Cr", Pn.Warn)
        }
        if (c.status != ContractStatus.OPEN || lock != null) Text(lock ?: ContractStatus.label(c.status), style = MaterialTheme.typography.labelSmall, color = Pn.Warn)
    }
}

@Composable
private fun StoryMissionCard(vm: GameViewModel, s: GameState, m: MissionDefinition) {
    val data = vm.data
    val lock = Missions.lockReason(data, s, m)
    val done = m.id in s.completedMissions
    GlassPanel(accent = when { done -> Pn.Ok; lock != null -> Pn.Muted; m.threat >= 4 -> Pn.Danger; else -> Pn.Accent }, onClick = if (lock == null) ({ vm.open(Overlay.Briefing(m.id)) }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(PnIcons.forMission(m.type), when { done -> Pn.Ok; lock != null -> Pn.Muted; else -> Pn.Accent }, 40.dp)
            Column(Modifier.weight(1f)) {
                Text((MissionGenerator.typeLabels[m.type] ?: m.type).uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                Text(m.title, style = MaterialTheme.typography.titleSmall)
            }
            Pill(when { done -> "Erledigt"; lock != null -> "Gesperrt"; else -> "Bereit" }, when { done -> Pn.Ok; lock != null -> Pn.Muted; else -> Pn.Accent })
        }
        if (lock != null && !done) Text(lock, style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
    }
}

@Composable
private fun ChainCard(vm: GameViewModel, s: GameState, c: ChainDefinition) {
    val data = vm.data
    val p = Chains.progress(s, c.id)
    val done = p >= c.steps.size
    val active = s.ops.contracts.firstOrNull { it.chainId == c.id }
    GlassPanel(accent = if (done) Pn.Ok else if (active != null) Pn.Violet else Pn.Muted, onClick = active?.let { { vm.open(Overlay.Briefing(it.id)) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.strand.uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Violet)
                Text(c.title, style = MaterialTheme.typography.titleMedium)
            }
            Pill("$p/${c.steps.size}", if (done) Pn.Ok else Pn.Violet)
        }
        Text(c.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            c.steps.forEachIndexed { i, st -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < p) Pn.Ok else if (i == p && active != null) Pn.Violet else Color(0x22FFFFFF))) }
        }
        Text(when {
            done -> "Abgeschlossen"
            active != null -> "Nächster Schritt auf dem Board: ${c.steps[p].title}"
            Ages.player(s) < c.minAge -> "Ab ${c.minAge} Jahren"
            !Conds.met(c.conditions, data, s) -> "Noch nicht freigeschaltet (Story-Fortschritt)"
            else -> "Wird angeboten, sobald die Beteiligten verfügbar sind"
        }, style = MaterialTheme.typography.labelSmall, color = if (active != null) Pn.Violet else Pn.Muted)
    }
}

@Composable
private fun WorldPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("syndicate", "phoenix", "independent", "government", "civilian").forEach { id ->
            if (id == "phoenix" && "phoenix_truth" !in s.flags) return@forEach
            val f = Ops.faction(s, id)
            val att = Ops.attitude(s, id)
            GlassPanel(padding = 12.dp, accent = if (att <= -15) Pn.Danger else if (att >= 15) Pn.Ok else Pn.Muted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Ops.factionName(data, id), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Pill(Ops.attitudeLabel(att), if (att <= -15) Pn.Danger else if (att >= 15) Pn.Ok else Pn.Muted)
                }
                StatBar("Haltung zur Division", att + 100, 200, if (att >= 0) Pn.Ok else Pn.Danger, showValue = false)
                StatBar("Einfluss in der Stadt", f.influence, 100, Pn.Warn)
                StatBar("Ressourcen", f.resources, 100, Pn.Violet)
                if (f.lastEvent.isNotBlank()) Text("Konfliktbogen: ${f.lastEvent}", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            }
        }
        GlassPanel {
            Overline("WELTLAGE · LETZTE ENTWICKLUNGEN", Pn.Accent)
            if (s.ops.worldLog.isEmpty()) SmallNote("Noch keine Entwicklungen.")
            s.ops.worldLog.takeLast(10).reversed().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CaseCard(vm: GameViewModel, s: GameState, c: Cases.CaseDef) {
    val st = s.ops.cases[c.id]
    val known = Cases.known(s, c.id)
    GlassPanel(accent = if (st?.solved == true) Pn.Ok else Pn.Warn, onClick = { vm.open(Overlay.Case(c.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AKTE · " + Ops.factionName(vm.data, c.faction).uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Warn)
                Text(c.title, style = MaterialTheme.typography.titleMedium)
            }
            Pill(if (st?.solved == true) (if (st.accused == c.truth) "Aufgeklärt" else "Falsche Spur") else "${known.size}/${c.clues.size} Hinweise", if (st?.solved == true) Pn.Ok else Pn.Warn)
        }
        Text(c.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ermittlungsakte: Hinweise, Quellen, Widersprüche, Verbindungen und Lösungswege. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaseScreen(vm: GameViewModel, s: GameState, caseId: String) {
    val data = vm.data
    val c = Cases.byId[caseId] ?: run { LaunchedEffect(Unit) { vm.back() }; return }
    val st = s.ops.cases[caseId]
    val clues = Cases.known(s, caseId)
    val refuted = clues.mapNotNull { it.contradicts }.toSet()
    val scores = Cases.scores(s, caseId)
    var pick by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(c.title, "Ermittlungsakte · ${Ops.factionName(data, c.faction)}", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPanel(accent = Pn.Warn) {
                Text(c.summary, style = MaterialTheme.typography.bodyMedium)
                if (st?.solved == true) {
                    KeyValue("Ergebnis", if (st.accused == c.truth) "Aufgeklärt" else "Falsche Spur", if (st.accused == c.truth) Pn.Ok else Pn.Danger)
                    Text(st.resultText, style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionLabel("Verdächtige")
            c.suspects.forEach { sus ->
                val score = scores[sus] ?: 0
                GlassPanel(padding = 12.dp, accent = if (pick == sus) Pn.Accent else Pn.Muted, onClick = if (st?.solved != true) ({ pick = if (pick == sus) null else sus }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sus, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Pill("Beweislage $score", if (score >= 5) Pn.Danger else Pn.Muted)
                    }
                    StatBar("Belastung", score.coerceAtMost(10), 10, Pn.Danger, showValue = false)
                    if (pick == sus && st?.solved != true) Cases.methods(s, caseId, sus).forEach { (m, ok) ->
                        ChoiceButton(m.label, m.requirement, enabled = ok) { vm.act { d, x -> Cases.solve(d, x, caseId, sus, m.id) }; pick = null }
                    }
                }
            }
            SectionLabel("Hinweise", trailing = "${clues.size}/${c.clues.size}")
            if (clues.isEmpty()) SmallNote("Noch keine Hinweise. Erfüllte Aufträge der Arten Daten, Information, Observation, Verhandlung, Aufklärung und Undercover liefern neue Spuren.")
            clues.forEach { cl ->
                val isRefuted = cl.id in refuted
                GlassPanel(padding = 12.dp, accent = when { isRefuted -> Pn.Muted; cl.quality >= 3 -> Pn.Ok; cl.quality == 2 -> Pn.Accent; else -> Pn.Warn }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(Cases.qualityLabel(cl.quality), when (cl.quality) { 3 -> Pn.Ok; 2 -> Pn.Accent; else -> Pn.Warn })
                        Spacer(Modifier.width(6.dp))
                        if (isRefuted) Pill("Widerlegt", Pn.Muted)
                        Spacer(Modifier.weight(1f))
                        Text(Clock.dateLabel(cl.day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(cl.text, style = MaterialTheme.typography.bodyMedium, color = if (isRefuted) Pn.Muted else Pn.Text)
                    Text("Quelle: ${cl.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    cl.contradicts?.let { other -> clues.firstOrNull { it.id == other }?.let { Text("Widerspricht: „${it.text.take(60)}…“", style = MaterialTheme.typography.labelSmall, color = Pn.Danger) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cl.people.forEach { Pill(it, Pn.Violet) }
                        cl.places.forEach { Pill(it, Pn.Muted, icon = PnIcons.City) }
                    }
                }
            }
            SmallNote("Lösungswege: Beweislage (gewichtete, nicht widerlegte Hinweise), Konfrontation (Probe) oder eine Falle, die als Einsatz auf dem Board erscheint. Eine falsche Beschuldigung hat bleibende Folgen.")
        }
    }
}

/** Zusatzbereich im Briefing für dynamische Aufträge. */
@Composable
fun ContractBriefingPanel(vm: GameViewModel, s: GameState, c: Contract) {
    val data = vm.data
    val comp = Ops.complicationById[c.complicationId]
    val entry = Ops.entryById[c.entryId]
    GlassPanel(accent = if (c.urgency >= 3) Pn.Danger else Pn.Warn) {
        Overline("AUFTRAG", Pn.Warn)
        KeyValue("Auftraggeber", c.clientNpc?.let { Relations.displayName(data, s, it) } ?: Ops.clientById[c.clientId]?.name ?: c.clientId)
        KeyValue("Gegenseite", Ops.factionName(data, c.targetFaction))
        KeyValue("Dringlichkeit", when (c.urgency) { 3 -> "Kritisch"; 2 -> "Dringend"; else -> "Routine" }, if (c.urgency >= 3) Pn.Danger else Pn.Text)
        KeyValue("Frist", Clock.dateLabel(c.expiresDay) + " (noch ${(c.expiresDay - s.clock.day).coerceAtLeast(0)} T.)")
        KeyValue("Dauer", if (c.phases > 1) "${c.phases} Einsatztage · aktuell Tag ${c.phase + 1}" else "1 Einsatztag")
        entry?.let { Text("Einstieg: ${it.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        comp?.takeIf { it.id != "none" }?.let { Text("Auflage „${it.label}“: ${it.text}", style = MaterialTheme.typography.bodySmall, color = Pn.Warn) }
        c.caseId?.let { KeyValue("Ermittlung", Cases.byId[it]?.title ?: it, Pn.Violet) }
    }
    SectionLabel("Vorgehensweisen")
    c.approaches.forEach { a ->
        GlassPanel(padding = 12.dp, accent = if (a.tactical) Pn.Danger else Pn.Accent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Pill(Attr.label(a.attribute), Pn.Muted)
                if (a.tactical) { Spacer(Modifier.width(4.dp)); Pill("Taktik · " + (TacticalMaps.byId[c.mapId]?.name ?: ""), Pn.Danger) }
            }
            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.risk.isNotBlank()) Text("Risiko: ${a.risk}", style = MaterialTheme.typography.labelSmall, color = Pn.Warn)
        }
    }
    GlassPanel(accent = Pn.Violet) {
        Overline("RISIKEN & KONSEQUENZEN", Pn.Violet)
        c.risks.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        Text("Bei Erfolg: ${c.successConsequence}", style = MaterialTheme.typography.bodySmall, color = Pn.Ok)
        Text("Bei Misserfolg oder Fristablauf: ${c.failConsequence}", style = MaterialTheme.typography.bodySmall, color = Pn.Danger)
        if (c.rewardItems.isNotEmpty()) Text("Sachbelohnung: " + c.rewardItems.keys.joinToString { data.itemById[it]?.name ?: it }, style = MaterialTheme.typography.bodySmall)
    }
    if (c.status == ContractStatus.OPEN || c.status == ContractStatus.POSTPONED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Vertagen", Modifier.weight(1f), enabled = c.status == ContractStatus.OPEN && c.urgency < 3, secondary = true, subtitle = "+3 Tage, −20 %") { vm.act { d, x -> Ops.postpone(d, x, c.id) } }
        ActionButton("Ablehnen", Modifier.weight(1f), secondary = true, subtitle = if (c.urgency >= 3) "Haltung −1" else "ohne Folgen") { vm.act { d, x -> Ops.decline(d, x, c.id) }; vm.back() }
    }
}

/** Nicht verfügbare Figuren mit Begründung (Life-Status, Alter). */
@Composable
fun UnavailableTeamNote(vm: GameViewModel, s: GameState, m: MissionDefinition, candidates: List<String>) {
    val data = vm.data
    val pool = data.agents.filter { it.factionId == "division" && it.id !in setOf("percy", "amanda", "helena") && World.revealed(data, s, it.id) }.map { it.id } +
        s.customNpcs.filter { s.clock.day >= it.createdDay }.map { it.id }
    val out = pool.filter { it !in candidates }.mapNotNull { id ->
        val reason = Ops.unavailableReason(data, s, id)
            ?: if (Ages.npc(data, s, id) < 18 && (m.minAge >= 18 || m.threat >= 3)) "unter 18 – nur sichere Aufgaben" else if (Relations.get(data, s, id).trust < 30) "Vertrauen < 30" else null
        reason?.let { id to it }
    }
    if (out.isNotEmpty()) GlassPanel(padding = 10.dp, accent = Pn.Muted) {
        Overline("NICHT VERFÜGBAR", Pn.Muted)
        out.forEach { (id, r) -> Text("${Relations.displayName(data, s, id)} – $r", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** HQ-Kurzlage: dringende Aufträge, Weltlage, offene Akten. */
@Composable
fun OpsHqCard(vm: GameViewModel, s: GameState) {
    if (s.ops.lastGenDay < 0) return
    val urgent = s.ops.contracts.count { it.urgency >= 2 }
    val running = s.ops.contracts.count { it.status == ContractStatus.IN_PROGRESS }
    val openCases = s.ops.cases.values.count { !it.solved }
    GlassPanel(accent = if (urgent > 0) Pn.Warn else Pn.Accent, onClick = { vm.open(Overlay.MissionControl("world")) }) {
        Overline("SHADOW OPERATIONS", Pn.Accent)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("${s.ops.contracts.size} Aufträge")
            if (urgent > 0) Pill("$urgent dringend", Pn.Warn)
            if (running > 0) Pill("$running laufend", Pn.Violet)
            if (openCases > 0) Pill("$openCases Akten", Pn.Muted)
        }
        StatBar("Einfluss Black Meridian", Ops.faction(s, "syndicate").influence, 100, Pn.Danger)
        s.ops.worldLog.lastOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
    }
}

/** Kompakte, horizontal scrollbare Auswahlchips (spart Platz im Hochformat); die Auswahl scrollt in Sicht. */
@Composable
fun CompactChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val idx = options.indexOfFirst { it.first == selected }
    LaunchedEffect(idx) { if (idx > 0) state.scrollToItem((idx - 1).coerceAtLeast(0)) }
    androidx.compose.foundation.lazy.LazyRow(state = state, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.size) { i ->
            val (id, label) = options[i]
            PnChip(label, id == selected) { onSelect(id) }
        }
    }
}

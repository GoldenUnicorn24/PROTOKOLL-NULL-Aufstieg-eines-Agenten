package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

@Composable
fun MissionBoardScreen(vm: GameViewModel, s: GameState) = MissionControlScreen(vm, s)

@Composable
fun ReportCard(r: MissionReport) {
    GlassPanel(accent = if (r.result.contains("Erfolg")) Pn.Ok else if (r.result.contains("Fehl")) Pn.Danger else Pn.Muted) {
        Row { Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Pill(r.result, if (r.result.contains("Erfolg")) Pn.Ok else Pn.Danger) }
        Text(Clock.dateLabel(r.day) + " · +${r.xp} XP · ${r.credits} Cr · Ruf ${Effs.signed(r.reputation)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        r.notes.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun MissionCard(vm: GameViewModel, s: GameState, m: MissionDefinition) {
    val data = vm.data
    val lock = Missions.lockReason(data, s, m)
    val done = m.id in s.completedMissions
    val failed = m.id in s.failedMissions && !done
    GlassPanel(accent = when { done -> Pn.Ok; lock != null -> Pn.Muted; m.threat >= 4 -> Pn.Danger; else -> Pn.Accent }, onClick = if (lock == null) ({ vm.open(Overlay.Briefing(m.id)) }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(PnIcons.forMission(m.type), when { done -> Pn.Ok; lock != null -> Pn.Muted; m.threat >= 4 -> Pn.Danger; else -> Pn.Accent }, 44.dp)
            Column(Modifier.weight(1f)) {
                Text((MissionGenerator.typeLabels[m.type] ?: m.type).uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Accent, maxLines = 1)
                Text(m.title, style = MaterialTheme.typography.titleMedium)
            }
            Text("▮".repeat(m.threat) + "▯".repeat((5 - m.threat).coerceAtLeast(0)), color = if (m.threat >= 4) Pn.Danger else Pn.Warn, style = MaterialTheme.typography.labelSmall)
        }
        Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(data.placeById[m.locationId]?.name ?: m.locationId, Pn.Muted, icon = PnIcons.City)
            Pill("${m.xp} XP")
            if (m.credits > 0) Pill("${m.credits} Cr", Pn.Warn)
        }
        when {
            done -> Pill("Abgeschlossen", Pn.Ok, icon = PnIcons.Shield)
            failed -> Pill("Gescheitert – erneut möglich", Pn.Danger, icon = PnIcons.Skull)
            lock != null -> Row(verticalAlignment = Alignment.CenterVertically) { PnIcon(PnIcons.Lock, Pn.Muted, 14.dp); Spacer(Modifier.width(6.dp)); Text(lock, style = MaterialTheme.typography.labelSmall, color = Pn.Muted) }
            else -> Pill("Briefing öffnen", Pn.Accent, icon = PnIcons.Chevron)
        }
    }
}

@Composable
fun BriefingScreen(vm: GameViewModel, s: GameState, missionId: String) {
    val data = vm.data
    val m = Missions.definition(data, s, missionId)
    if (m == null) { LaunchedEffect(Unit) { vm.back() }; return }
    var approach by remember { mutableStateOf("balanced") }
    val candidates = Missions.teamCandidates(data, s, m)
    val maxTeam = minOf(m.maxTeam, Progression.teamSize(data, s))
    var team by remember { mutableStateOf(s.team.filter { it in candidates }.take(maxTeam)) }
    val lock = Missions.lockReason(data, s, m)
    val contract = if (Ops.isContract(m.id)) Ops.contract(s, m.id) else null
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(m.title, "Briefing · " + (MissionGenerator.typeLabels[m.type] ?: m.type), onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScenePanel(m.locationId, Modifier.fillMaxWidth().aspectRatio(2.5f), "Einsatzgebiet",
                data.placeById[m.locationId]?.name ?: m.locationId)
            GlassPanel {
                if (m.giverId != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NpcPortrait(data, s, m.giverId, 56.dp)
                    Column { Text("Auftraggeber", style = MaterialTheme.typography.labelSmall, color = Pn.Accent); Text(Relations.displayName(data, s, m.giverId), style = MaterialTheme.typography.titleSmall) }
                }
                Text(m.description, style = MaterialTheme.typography.bodyMedium)
                if (m.briefing.isNotBlank()) Text(m.briefing, style = MaterialTheme.typography.bodySmall, color = Pn.Accent)
                if (contract == null) {
                    KeyValue("Fraktion", data.factionById[m.factionId]?.name ?: m.factionId)
                    KeyValue("Gefahrenstufe", "${m.threat}/5")
                    KeyValue("Zeugen", m.witnesses.toString())
                    KeyValue("Phasen", m.stages.joinToString(" → ") { MissionGenerator.typeLabels[it.type] ?: when (it.type) { "choice" -> "Entscheidung"; "search" -> "Suche"; "observe" -> "Beobachtung"; "tactical" -> "Taktik"; else -> it.type } })
                } else KeyValue("Gefahrenstufe", "${m.threat}/5")
                KeyValue("Belohnung", "${m.xp} XP · ${m.credits} Cr · Ruf +${m.reputation}")
            }
            if (contract != null) ContractBriefingPanel(vm, s, contract)
            SectionLabel(if (contract != null) "Einsatzstil" else "Vorgehen")
            Missions.approaches.forEach { a -> ChoiceButton(a.name + if (approach == a.id) "  ✓" else "", a.description) { approach = a.id } }
            SectionLabel("Team", trailing = "${team.size}/$maxTeam")
            if (maxTeam <= 0 || candidates.isEmpty()) SmallNote(if (candidates.isEmpty()) "Keine verfügbaren Teammitglieder (Vertrauen ≥ 30, bei Gefahr ≥ 3 nur Volljährige)." else "Einzeleinsatz.")
            else candidates.forEach { id ->
                val sel = id in team
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel) Color(0x223FD6C4) else Color(0x10FFFFFF))
                        .clickable { team = if (sel) team - id else if (team.size < maxTeam) team + id else team }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NpcPortrait(data, s, id, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.titleSmall)
                        val unit = Tactical.allyUnit(data, s, id, 0, 0)
                        val life = Life.life(data, s, id)
                        Text("Fähigkeit: " + (Tactical.abilities[unit.ability]?.name ?: "–") + " · Treffer ${unit.accuracy} · LP ${unit.maxHp}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Life.focusLabel(life.focus) + " · " + data.rank(life.rank).name + (contract?.let { " · Passung ${Ops.fit(data, s, it, id)}/10" } ?: ""), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                    }
                    if (sel) Pill("Im Team")
                }
            }
            UnavailableTeamNote(vm, s, m, candidates)
            SectionLabel("Ausrüstung")
            GlassPanel {
                val carried = s.player.inventory.map { it.itemId }.toSet()
                m.recommendedItems.forEach { id ->
                    val def = data.itemById[id]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ItemIcon(def?.icon ?: "notebook", 36.dp)
                        Text(def?.name ?: id, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Pill(if (id in carried) "dabei +5" else "fehlt", if (id in carried) Pn.Ok else Pn.Muted)
                    }
                }
                if (m.recommendedItems.isEmpty()) SmallNote("Keine besondere Ausrüstung empfohlen.")
                SmallNote("Ausgerüstete Gegenstände geben Attributboni; empfohlene Gegenstände im Inventar +5 auf Proben.")
            }
        }
        Box(Modifier.padding(16.dp)) {
            ActionButton(if (lock != null) lock else "Einsatz starten", Modifier.fillMaxWidth(), enabled = lock == null, subtitle = "Energie ≥ 25 · Gesundheit ≥ 40") {
                vm.act { d, st -> Missions.start(d, st, m.id, approach, team) }
                vm.closeOverlays()
            }
        }
    }
}

@Composable
fun MissionRunScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val run = s.activeMission ?: return
    val m = Missions.currentMission(data, s) ?: return
    if (run.result != null) { DebriefScreen(vm, s, m, run); return }
    val st = Missions.currentStage(data, s) ?: return
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(m.title, (st.title.ifBlank { "Einsatz" }), trailing = {
            TextButton(onClick = { vm.act { d, x -> Missions.abort(d, x) } }) { Text("Abbrechen", color = Pn.Danger) }
        })
        val idx = m.stages.indexOfFirst { it.id == st.id }
        PhaseStepper(m.stages.map { it.type }, idx, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Intel ${run.intel}", Pn.Accent, icon = PnIcons.Chip)
            Pill("Alarm ${run.alarm}/6", if (run.alarm >= 4) Pn.Danger else Pn.Warn, icon = PnIcons.Skull)
            if (m.type == "undercover" || m.id == "M007" || run.cover != 3) Pill("Tarnung ${run.cover}", if (run.cover <= 1) Pn.Danger else Pn.Ok, icon = PnIcons.Profile)
        }
        when (st.type) {
            "search" -> SearchStage(vm, s, st, run)
            "observe" -> ObserveStage(vm, s, st, run)
            "tactical" -> TacticalScreen(vm, s, st, run)
            else -> ChoiceStage(vm, s, st, run)
        }
    }
}

@Composable
private fun StageHeader(vm: GameViewModel, s: GameState, st: StageDefinition, run: MissionRun) {
    if (run.lastText.isNotBlank()) Text(run.log.lastOrNull() ?: "", style = MaterialTheme.typography.bodySmall, color = Pn.Accent)
    if (st.speakerId != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NpcPortrait(vm.data, s, st.speakerId, 64.dp)
        Text(Relations.displayName(vm.data, s, st.speakerId), style = MaterialTheme.typography.titleMedium, color = Pn.Accent)
    }
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon(PnIcons.forStage(st.type), size = 16.dp); Spacer(Modifier.width(6.dp))
            Text(when (st.type) { "search" -> "UNTERSUCHUNG"; "observe" -> "OBSERVATION"; "tactical" -> "TAKTIK"; else -> "LAGE" }, style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
        }
        TypewriterText(Dialogues.format(st.text, s), MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChoiceStage(vm: GameViewModel, s: GameState, st: StageDefinition, run: MissionRun) {
    val data = vm.data
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StageHeader(vm, s, st, run)
        if (st.options.isEmpty()) ActionButton("Weiter", Modifier.fillMaxWidth()) { vm.act(silent = true) { d, x -> Missions.continueNarrative(d, x) } }
        st.options.forEach { o ->
            val ok = Conds.met(o.requires, data, s)
            val ib = Missions.itemBonus(data, s, o)
            val itemHint = if (o.itemBonus.isNotEmpty()) " · " + o.itemBonus.keys.joinToString("/") { data.itemById[it]?.name ?: it } + (if (ib > 0) " +$ib" else " (fehlt)") else ""
            val detail = o.check?.let { Attr.label(it.attribute) + (it.skill?.let { sk -> " · " + (data.skillById[sk]?.name ?: sk) } ?: "") + itemHint }
            ChoiceButton(o.text, detail ?: if (!ok) Conds.describe(o.requires, data).joinToString() else null, Missions.optionChance(data, s, o), enabled = ok) {
                vm.act(silent = true) { d, x -> Missions.choose(d, x, o.id) }
            }
        }
        LogPanel(run)
    }
}

@Composable
private fun SearchStage(vm: GameViewModel, s: GameState, st: StageDefinition, run: MissionRun) {
    val se = run.search ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StageHeader(vm, s, st, run)
        Row { Pill("Funde ${se.found}/${st.cluesNeeded}", Pn.Ok); Spacer(Modifier.width(8.dp)); Pill("Aktionen ${se.actionsLeft}", Pn.Warn) }
        se.spots.chunked(3).forEachIndexed { row, spots ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                spots.forEachIndexed { col, name ->
                    val i = row * 3 + col
                    val searched = i in se.searched
                    val hit = searched && i in se.clues
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.weight(1f).height(84.dp).clip(shape)
                            .background(when { hit -> Color(0x445CD89C); searched -> Color(0x14FFFFFF); else -> Color(0x223FD6C4) })
                            .border(1.dp, if (hit) Pn.Ok else Pn.Line, shape)
                            .clickable(enabled = !searched) { vm.act(silent = true) { d, x -> Missions.search(d, x, i) } }.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = if (searched && !hit) Pn.Muted else Pn.Text, maxLines = 2)
                            if (hit) Text("◆ Hinweis", style = MaterialTheme.typography.labelSmall, color = Pn.Ok)
                            else if (searched) Text(se.hints[i]?.let { "Nähe: $it" } ?: "leer", style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
                        }
                    }
                }
            }
        }
        SmallNote("Misslungene Suchen geben mit einer Wahrnehmungsprobe Hinweise, wie viele Spuren in benachbarten Feldern liegen.")
        LogPanel(run)
    }
}

@Composable
private fun ObserveStage(vm: GameViewModel, s: GameState, st: StageDefinition, run: MissionRun) {
    val ob = run.observe ?: return
    val signal = remember(run.seed, run.stageId, ob.round, ob.correct, ob.targets) { ObservationSignal.forRound(run) } ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StageHeader(vm, s, st, run)
        Row { Pill("Runde ${(ob.round + 1).coerceAtMost(ob.rounds)}/${ob.rounds}"); Spacer(Modifier.width(8.dp)); Pill("Treffer ${ob.hits}/${st.intelNeeded}", Pn.Ok) }
        GlassPanel {
            Text("Signalabgleich", style = MaterialTheme.typography.titleMedium)
            Text("Die Aufzeichnung wiederholt sich im Kreis. Welcher Kanal liefert die nächsten zwei Zeichen?", style = MaterialTheme.typography.bodyMedium)
            Text("TAKTFOLGE", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            Text(signal.reference.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
            Text("NEUER UMLAUF", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            Text(signal.fragment.joinToString(" · ") + " · ? · ?", style = MaterialTheme.typography.titleSmall)
            ob.targets.forEachIndexed { i, t ->
                ChoiceButton(t, signal.channels[i].joinToString(" → ")) { vm.act(silent = true) { d, x -> Missions.observe(d, x, i) } }
            }
            ActionButton(if (ob.hintRevealed) "Hinweis angefordert" else "Hinweis anfordern (Wahrnehmung)", Modifier.fillMaxWidth(), enabled = !ob.hintRevealed, secondary = true) {
                vm.act(silent = true) { d, x -> Missions.observeHint(d, x) }
            }
        }
        LogPanel(run)
    }
}

@Composable
fun LogPanel(run: MissionRun) {
    GlassPanel(accent = Pn.Muted, padding = 12.dp) {
        Text("EINSATZPROTOKOLL", style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
        run.log.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun DebriefScreen(vm: GameViewModel, s: GameState, m: MissionDefinition, run: MissionRun) {
    val color = when (run.result) { "success" -> Pn.Ok; "fail" -> Pn.Danger; else -> Pn.Muted }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NACHBESPRECHUNG", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
        Text(m.title.uppercase(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp))
        Pill(when (run.result) { "success" -> "Auftrag erfüllt"; "fail" -> "Fehlgeschlagen"; else -> "Abgebrochen" }, color)
        GlassPanel(accent = color) {
            Text(when (run.result) { "success" -> m.debriefSuccess; "fail" -> m.debriefFail; else -> "Du brichst den Einsatz ab." }.ifBlank { "Einsatz beendet." }, style = MaterialTheme.typography.bodyLarge)
            KeyValue("Gesammelte Intel", run.intel.toString())
            KeyValue("Alarmstufe", "${run.alarm}/6", if (run.alarm >= 4) Pn.Danger else Pn.Text)
            KeyValue("Erlittener Schaden", "${run.damageTaken}")
            KeyValue("Team", if (run.team.isEmpty()) "allein" else run.team.joinToString { Relations.displayName(vm.data, s, it) })
        }
        LogPanel(run)
        ActionButton("Bericht abschließen", Modifier.fillMaxWidth(), subtitle = "Belohnungen & Konsequenzen anwenden") { vm.act { d, x -> Missions.finish(d, x) } }
    }
}

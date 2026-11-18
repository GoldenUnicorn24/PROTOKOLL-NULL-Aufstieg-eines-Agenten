package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

@Composable
fun ContactsScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var filter by remember { mutableStateOf("all") }
    val ids = (data.agents.map { it.id } + s.customNpcs.map { it.id }).filter { id -> World.revealed(data, s, id) }
    val shown = when (filter) {
        "friends" -> ids.filter { Relations.get(data, s, it).friendship >= 50 }
        "rivals" -> ids.filter { Relations.get(data, s, it).rivalry >= 20 }
        "team" -> ids.filter { it in s.team }
        else -> ids
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("PERSONALAKTEN", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            Text("KONTAKTE & BEZIEHUNGEN", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            ChipRow(listOf("all" to "Alle", "friends" to "Freunde", "rivals" to "Rivalen", "team" to "Team"), filter) { filter = it }
        }
        items(shown, key = { it }) { id ->
            val r = Relations.get(data, s, id)
            val def = data.agentById[id]
            val loc = World.npcLocation(data, s, id)?.let { data.roomById[it]?.name } ?: "außer Haus"
            GlassPanel(padding = 12.dp, onClick = { vm.open(Overlay.Npc(id)) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NpcPortrait(data, s, id, 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.titleSmall)
                        Text((def?.role ?: "Rekrut/in · " + Social.professionLabel(s.customNpcs.firstOrNull { it.id == id }?.profession ?: "")) + " · ${Ages.npc(data, s, id)} J.", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                        StatBar("Vertrauen", r.trust, 100, showValue = false)
                        StatBar("Freundschaft", r.friendship, 100, Pn.Ok, showValue = false)
                        Text("◉ $loc" + if (Dialogues.available(data, s, id).isNotEmpty()) " · Neues Gespräch!" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun NpcDetailScreen(vm: GameViewModel, s: GameState, id: String) {
    val data = vm.data
    val def = data.agentById[id]
    val custom = s.customNpcs.firstOrNull { it.id == id }
    val r = Relations.get(data, s, id)
    val here = World.npcLocation(data, s, id) == s.location
    var tab by remember { mutableStateOf("profile") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Relations.displayName(data, s, id), def?.role ?: "Eigene Rekrutierung", onBack = { vm.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NpcPortrait(data, s, id, 130.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyValue("Alter", "${Ages.npc(data, s, id)}")
                    KeyValue("Fraktion", data.factionById[def?.factionId ?: "division"]?.name ?: "Division")
                    if (custom != null) KeyValue("Rang", data.rank(Social.customRank(s, custom)).name)
                    KeyValue("Aufenthalt", World.npcLocation(data, s, id)?.let { data.roomById[it]?.name } ?: "außer Haus")
                    if (def?.actorName != null && data.portraitFor(id) != null) Text("Foto: ${def.actorName} (CC, Wikimedia Commons)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (Romance.romanceable(data, s, id) && r.romanceStage > 0) Pill(Romance.stageName(data, s, id, r.romanceStage), Pn.Violet)
                }
            }
            ChipRow(listOf("profile" to "Akte", "relation" to "Beziehung", "talk" to "Interaktion"), tab) { tab = it }
            when (tab) {
                "profile" -> {
                    GlassPanel {
                        Text(def?.let { d -> d.bio.ifBlank { d.description } } ?: (custom?.background ?: ""), style = MaterialTheme.typography.bodyMedium)
                        val look = def?.look
                        if (!look.isNullOrBlank()) { Text(if (def?.seriesCharacter == true) "Serien-Look (Referenz)" else "Erscheinungsbild", style = MaterialTheme.typography.titleSmall); Text(look, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        val traits = def?.traits ?: custom?.traits ?: emptyList()
                        if (traits.isNotEmpty()) ChipRow(traits.map { it to it }, "") {}
                        if (custom != null) {
                            KeyValue("Startberuf", Social.professionLabel(custom.profession))
                            KeyValue("Interessen", custom.interests.joinToString())
                            custom.skills.forEach { (k, v) -> KeyValue(data.skillById[k]?.name ?: k, "$v/5") }
                        }
                    }
                }
                "relation" -> GlassPanel {
                    StatBar("Freundschaft", r.friendship, 100, Pn.Ok)
                    StatBar("Vertrauen", r.trust, 100)
                    StatBar("Loyalität", r.loyalty, 100, Pn.Violet)
                    StatBar("Respekt", r.respect, 100, Pn.Warn)
                    if (Romance.romanceable(data, s, id)) StatBar("Zuneigung", r.affection, 100, Color(0xFFFF8FB1))
                    if (r.rivalry > 0) StatBar("Rivalität", r.rivalry, 100, Pn.Danger)
                    Text("Erinnerungen", style = MaterialTheme.typography.titleSmall)
                    if (r.memories.isEmpty()) SmallNote("Noch keine gemeinsamen Erinnerungen.")
                    r.memories.reversed().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                else -> {
                    if (!here) GlassPanel(accent = Pn.Warn) { Text("${Relations.displayName(data, s, id)} ist nicht hier. Gehe in den Raum „${World.npcLocation(data, s, id)?.let { data.roomById[it]?.name } ?: "—"}“, um zu interagieren.", style = MaterialTheme.typography.bodySmall) }
                    val dialogs = Dialogues.available(data, s, id)
                    dialogs.forEach { d ->
                        ChoiceButton("Gespräch: " + d.title, d.chapter, enabled = here) { vm.back(); vm.act(silent = true) { dd, x -> Dialogues.start(dd, x, d.id) } }
                    }
                    Social.interactions(data, s, id).forEach { i ->
                        ChoiceButton(i.label, if (i.enabled) i.description else i.reason, enabled = here && i.enabled) { vm.act { dd, x -> Social.interact(dd, x, id, i.id) } }
                    }
                    val step = Romance.nextStep(data, s, id)
                    if (step != null) {
                        SectionLabel("Beziehung")
                        ChoiceButton(step.label, step.requirement, enabled = here && step.available) { vm.act { dd, x -> Romance.advance(dd, x, id) } }
                        if (Romance.teen(data, s, id)) SmallNote("Altersgerechte Teen-Beziehung: gemeinsame Zeit, Vertrauen, Gespräche – keine sexualisierten Inhalte.")
                    } else if (Romance.romanceable(data, s, id) && !Romance.allowed(data, s, id)) {
                        SmallNote("Romantische Optionen sind gesperrt, solange eine der beiden Figuren minderjährig ist und die andere volljährig. Freundschaft und Mentoring sind möglich.")
                    }
                    if (id in Missions.teamCandidates(data, s, MissionDefinition(id = "x", title = "", locationId = "hq", type = "x"))) {
                        val inTeam = id in s.team
                        ChoiceButton(if (inTeam) "Aus dem Stammteam entfernen" else "Ins Stammteam aufnehmen", "Stammteam: ${s.team.size}/${Progression.teamSize(data, s)}") {
                            vm.update { st -> st.copy(team = if (inTeam) st.team - id else if (st.team.size < Progression.teamSize(data, st)) st.team + id else st.team) }
                        }
                    }
                }
            }
        }
    }
}


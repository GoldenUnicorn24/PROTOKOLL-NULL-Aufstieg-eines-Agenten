package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import de.protokollnull.agent.data.PrivateCastPortraits
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

fun statusColor(status: String): Color = when (status) {
    LifeStatus.MISSION -> Pn.Warn
    LifeStatus.INJURED -> Pn.Danger
    LifeStatus.VACATION, LifeStatus.ABSENT -> Pn.Muted
    LifeStatus.TRANSFERRED -> Pn.Violet
    LifeStatus.TRAINING -> Pn.Ok
    LifeStatus.FREE -> Pn.Rose
    else -> Pn.Accent
}

@Composable
fun ContactsScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var filter by remember { mutableStateOf("all") }
    val ids = Life.presenceSummary(data, s).values.flatten().let { known -> (data.agents.map { it.id } + s.customNpcs.map { it.id }).filter { it in known } }
    val status = { id: String -> s.living.lives[id]?.status ?: LifeStatus.DUTY }
    val shown = when (filter) {
        "hq" -> ids.filter { LifeStatus.inHq(status(it)) }
        "away" -> ids.filter { !LifeStatus.inHq(status(it)) }
        "friends" -> ids.filter { Relations.get(data, s, it).friendship >= 50 }
        "rivals" -> ids.filter { Relations.get(data, s, it).rivalry >= 20 || it in s.living.conflicts }
        "team" -> ids.filter { it in s.team }
        else -> ids
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("PERSONALAKTEN", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            Text("KONTAKTE & BEZIEHUNGEN", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            PresenceStrip(vm, s)
            Spacer(Modifier.height(6.dp))
            ChipRow(listOf("all" to "Alle", "hq" to "Im HQ", "away" to "Abwesend", "friends" to "Freunde", "rivals" to "Spannungen", "team" to "Team", "feed" to "Divisionsleben"), filter) { filter = it }
        }
        if (filter == "feed") {
            val feed = s.living.feed.reversed().filter { World.revealed(data, s, it.npcId) || it.npcId == "player" }.take(60)
            if (feed.isEmpty()) item { SmallNote("Noch keine Neuigkeiten. Das Leben in der Division beginnt, sobald die Ausbildung läuft.") }
            items(feed.size) { i -> FeedRow(vm, s, feed[i]) }
        } else items(shown, key = { it }) { id -> ContactCard(vm, s, id) }
    }
}

@Composable
fun PresenceStrip(vm: GameViewModel, s: GameState) {
    val groups = Life.presenceSummary(vm.data, s)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val inHq = groups.filterKeys { LifeStatus.inHq(it) && it != LifeStatus.INJURED }.values.sumOf { it.size }
        Pill("Im HQ $inHq", Pn.Accent)
        groups[LifeStatus.MISSION]?.let { Pill("Einsatz ${it.size}", Pn.Warn) }
        groups[LifeStatus.INJURED]?.let { Pill("Verletzt ${it.size}", Pn.Danger) }
        val away = listOf(LifeStatus.VACATION, LifeStatus.ABSENT, LifeStatus.TRANSFERRED).sumOf { groups[it]?.size ?: 0 }
        if (away > 0) Pill("Abwesend $away", Pn.Muted)
    }
}

@Composable
private fun FeedRow(vm: GameViewModel, s: GameState, e: LifeEvent) {
    val data = vm.data
    GlassPanel(padding = 10.dp, accent = feedColor(e.type), onClick = if (e.npcId != "player") ({ vm.open(Overlay.Npc(e.npcId)) }) else null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.npcId != "player") NpcPortrait(data, s, e.npcId, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(Clock.dateLabel(e.day), style = MaterialTheme.typography.labelSmall, color = feedColor(e.type))
                Text(e.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun feedColor(type: String): Color = when (type) {
    "promoted", "mission_ok", "friends", "couple", "skill" -> Pn.Ok
    "injured", "mission_fail", "exam_failed", "rivals", "breakup" -> Pn.Danger
    "transferred", "vacation" -> Pn.Violet
    "hq" -> Pn.Warn
    else -> Pn.Accent
}

@Composable
private fun ContactCard(vm: GameViewModel, s: GameState, id: String) {
    val data = vm.data
    val r = Relations.get(data, s, id)
    val def = data.agentById[id]
    val st = s.living.lives[id]?.status ?: LifeStatus.DUTY
    val loc = World.npcLocation(data, s, id)?.let { data.roomById[it]?.name } ?: "außer Haus"
    GlassPanel(padding = 12.dp, accent = statusColor(st), onClick = { vm.open(Overlay.Npc(id)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NpcPortrait(data, s, id, 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Pill(LifeStatus.label(st), statusColor(st))
                }
                Text((def?.role ?: ("Rekrut/in · " + Social.professionLabel(s.customNpcs.firstOrNull { it.id == id }?.profession ?: ""))) +
                    " · ${Ages.npc(data, s, id)} J. · ${data.rank(Life.rank(data, s, id)).name}", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                StatBar("Vertrauen", r.trust, 100, showValue = false)
                StatBar("Freundschaft", r.friendship, 100, Pn.Ok, showValue = false)
                val extra = buildList {
                    if (id in s.living.conflicts) add("⚠ Konflikt")
                    if (Dialogues.available(data, s, id).isNotEmpty()) add("Neues Gespräch!")
                    Life.partnerOf(s, id)?.let { add("♥ " + Relations.displayName(data, s, it).substringBefore(" ")) }
                }
                Text("◉ $loc" + if (extra.isNotEmpty()) " · " + extra.joinToString(" · ") else "", style = MaterialTheme.typography.labelSmall, color = if (id in s.living.conflicts) Pn.Danger else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NpcDetailScreen(vm: GameViewModel, s: GameState, id: String, initialTab: String = "profile") {
    val data = vm.data
    val def = data.agentById[id]
    val custom = s.customNpcs.firstOrNull { it.id == id }
    val r = Relations.get(data, s, id)
    val room = World.npcLocation(data, s, id)?.let { data.roomById[it] }
    val here = room?.id == s.location
    var tab by remember(id, initialTab) { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Relations.displayName(data, s, id), def?.role ?: "Eigene Rekrutierung", onBack = { vm.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NpcPortrait(data, s, id, 130.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyValue("Alter", "${Ages.npc(data, s, id)}")
                    KeyValue("Fraktion", data.factionById[def?.factionId ?: "division"]?.name ?: "Division")
                    KeyValue("Rang", data.rank(Life.rank(data, s, id)).name)
                    KeyValue("Status", Life.statusLine(data, s, id), statusColor(s.living.lives[id]?.status ?: LifeStatus.DUTY))
                    KeyValue("Aufenthalt", World.npcLocation(data, s, id)?.let { data.roomById[it]?.name } ?: "außer Haus")
                    val device = LocalContext.current
                    if (def?.actorName != null && data.portraitFor(id) != null &&
                        PrivateCastPortraits.imported(device, id) == null && PrivateCastPortraits.bundled(device, id) == null
                    ) Text("Foto: ${def.actorName} (CC, Wikimedia Commons)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (Romance.romanceable(data, s, id) && r.romanceStage > 0) Pill(Romance.stageName(data, s, id, r.romanceStage), Pn.Violet)
                }
            }
            ChipRow(listOf("profile" to "Akte", "duty" to "Dienst", "career" to "Laufbahn", "relation" to "Beziehung", "circle" to "Umfeld", "talk" to "Interaktion"), tab) { tab = it }
            when (tab) {
                "profile" -> {
                    GlassPanel {
                        Text(def?.let { d -> d.bio.ifBlank { d.description } } ?: (custom?.background ?: ""), style = MaterialTheme.typography.bodyMedium)
                        val look = def?.look
                        if (!look.isNullOrBlank()) { Text(if (def?.seriesCharacter == true) "Serien-Look (Referenz)" else "Erscheinungsbild", style = MaterialTheme.typography.titleSmall); Text(look, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        val traits = def?.traits ?: custom?.traits ?: emptyList()
                        if (traits.isNotEmpty()) ChipRow(traits.map { it to it }, "") {}
                        val interests = Life.interests(data, s, id)
                        if (interests.isNotEmpty()) KeyValue("Interessen", interests.joinToString())
                        val values = Life.values(data, s, id)
                        if (values.isNotEmpty()) KeyValue("Werte", values.joinToString())
                        val goal = Life.life(data, s, id).goal
                        if (goal.isNotBlank()) KeyValue("Lebensziel", goal)
                        if (custom != null) {
                            KeyValue("Startberuf", Social.professionLabel(custom.profession))
                            if (custom.careerWish.isNotBlank()) KeyValue("Berufswunsch", CompanionOptions.careerWishes.firstOrNull { it.first == custom.careerWish }?.second ?: custom.careerWish)
                            custom.skills.forEach { (k, v) -> KeyValue(data.skillById[k]?.name ?: k, "$v/5") }
                        }
                    }
                }
                "duty" -> PersonnelFile(vm, s, id)
                "career" -> CareerFile(vm, s, id)
                "relation" -> RelationFile(vm, s, id)
                "circle" -> CircleFile(vm, s, id)
                else -> {
                    if (!here) GlassPanel(accent = Pn.Warn) {
                        if (room == null) SmallNote("Zurzeit außer Haus. Schau in einem späteren Zeitslot wieder vorbei.")
                        else {
                            val lock = World.roomLocked(data, s, room)
                            ActionButton("In den Raum gehen", Modifier.fillMaxWidth(), secondary = true,
                                subtitle = lock ?: room.name, enabled = lock == null) {
                                vm.act(silent = true) { d, st -> World.moveTo(d, st, room.id) }
                            }
                        }
                    }
                    val dialogs = Dialogues.available(data, s, id)
                    dialogs.forEach { d ->
                        ChoiceButton("Gespräch: " + d.title, d.chapter, enabled = here) { vm.back(); vm.act(silent = true) { dd, x -> Dialogues.start(dd, x, d.id) } }
                    }
                    var confirm by remember { mutableStateOf<String?>(null) }
                    Social.interactions(data, s, id).forEach { i ->
                        ChoiceButton(i.label, if (i.enabled) i.description else i.reason, enabled = here && i.enabled) {
                            if (i.id == "stay_friends") confirm = "stay_friends" else vm.act { dd, x -> Social.interact(dd, x, id, i.id) }
                        }
                    }
                    val step = Romance.nextStep(data, s, id)
                    if (step != null) {
                        SectionLabel("Beziehung")
                        ChoiceButton(step.label, step.requirement, enabled = here && step.available) {
                            if (step.id == "confess") confirm = "confess" else vm.act { dd, x -> Romance.advance(dd, x, id) }
                        }
                    }
                    confirm?.let { what ->
                        val name = Relations.displayName(data, s, id).substringBefore(" ")
                        val others = Romance.otherInterests(data, s, id).map { Relations.displayName(data, s, it).substringBefore(" ") }
                        AlertDialog(
                            onDismissRequest = { confirm = null },
                            title = { Text(if (what == "confess") "Feste Beziehung mit $name?" else if (r.romanceStage >= 2) "Beziehung beenden?" else "Nur Freunde bleiben?") },
                            text = { Text(when {
                                what == "confess" -> "Das ist eine bewusste Entscheidung. " + (if (others.isNotEmpty()) "Die Annäherung an ${others.joinToString()} endet damit respektvoll." else "Es gibt keine anderen offenen Annäherungen.") + " Eine Probe auf Ausstrahlung entscheidet, ob $name schon bereit ist."
                                r.romanceStage >= 2 -> "$name wird verletzt sein. Es entsteht ein Konflikt, den ihr später in einer Aussprache klären könnt."
                                else -> "Ihr bleibt Freunde. Romantische Schritte sind frühestens in 90 Tagen wieder möglich."
                            }) },
                            confirmButton = { TextButton(onClick = {
                                confirm = null
                                if (what == "confess") vm.act { dd, x -> Romance.advance(dd, x, id) } else vm.act { dd, x -> Social.interact(dd, x, id, "stay_friends") }
                            }) { Text("Entscheiden") } },
                            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } }
                        )
                    }
                    if (Romance.romanceable(data, s, id) && Romance.allowed(data, s, id) && Romance.teen(data, s, id)) {
                        SmallNote("Altersgerechte, freiwillige Beziehung ab 16 (maximal zwei Jahre Abstand): Gespräche, Dates, Zuneigung. Zusammenziehen und Heirat erst als Erwachsene, keine sexualisierten Inhalte.")
                    } else if (Romance.romanceable(data, s, id) && !Romance.allowed(data, s, id)) {
                        SmallNote("Romantik derzeit nicht verfügbar. Solange eine Figur minderjährig ist, müssen beide mindestens 16 sein und höchstens zwei Jahre auseinanderliegen.")
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


@Composable
private fun CareerFile(vm: GameViewModel, s: GameState, id: String) {
    val data = vm.data
    val l = Life.life(data, s, id)
    val cap = Life.rankCap(data, s, id)
    GlassPanel {
        Text("Laufbahn", style = MaterialTheme.typography.titleSmall)
        KeyValue("Rang", data.rank(l.rank).name)
        if (cap > l.rank) StatBar("Prüfungsreife", l.careerXp.coerceAtMost(Life.examThreshold(l.rank)), Life.examThreshold(l.rank), Pn.Warn)
        else SmallNote(if (cap == 0) "Führungsebene – kein Prüfungsweg." else "Höchster Rang für diese Laufbahn erreicht" + if (Ages.npc(data, s, id) < 18) " (bis zur Volljährigkeit)." else ".")
        if (l.examCooldownUntil > s.clock.day) SmallNote("Nächster Prüfungsversuch frühestens am " + Clock.dateLabel(l.examCooldownUntil))
        KeyValue("Schwerpunkt", Life.focusLabel(l.focus))
        KeyValue("Einsätze", "${l.missions} erfolgreich · ${l.missionsFailed} gescheitert")
        KeyValue("Prüfungen", "${l.examsPassed} bestanden · ${l.examsFailed} nicht bestanden")
        KeyValue("Trainingstage", "${l.trainingDays}")
        KeyValue("Verletzungen", "${l.injuries}")
        if (l.grades.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Monatsnoten", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            l.grades.forEach { g -> Pill(g, when (g) { "A", "B" -> Pn.Ok; "C" -> Pn.Accent; "D" -> Pn.Warn; else -> Pn.Danger }) }
        }
        if (l.lastDecision.isNotBlank()) KeyValue("Letzte Entscheidung", l.lastDecision)
    }
    GlassPanel {
        Text("Kompetenzen", style = MaterialTheme.typography.titleSmall)
        Life.focusAreas.forEach { a -> StatBar(Life.focusLabel(a), Life.competence(data, s, id, a), 10, if (a == l.focus) Pn.Warn else Pn.Accent) }
    }
    val events = s.living.feed.filter { it.npcId == id || id in it.others }.takeLast(8).reversed()
    if (events.isNotEmpty()) GlassPanel {
        Text("Chronik", style = MaterialTheme.typography.titleSmall)
        events.forEach { e -> Text(Clock.dateLabel(e.day) + " · " + e.text, style = MaterialTheme.typography.bodySmall, color = feedColor(e.type)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationFile(vm: GameViewModel, s: GameState, id: String) {
    val data = vm.data
    val r = Relations.get(data, s, id)
    val compat = Bonds.compatibility(data, s, id)
    GlassPanel {
        StatBar("Freundschaft", r.friendship, 100, Pn.Ok)
        StatBar("Vertrauen", r.trust, 100)
        StatBar("Loyalität", r.loyalty, 100, Pn.Violet)
        StatBar("Respekt", r.respect, 100, Pn.Warn)
        if (Romance.romanceable(data, s, id)) StatBar("Zuneigung", r.affection, 100, Pn.Rose)
        if (r.rivalry > 0) StatBar("Rivalität", r.rivalry, 100, Pn.Danger)
        if (Romance.romanceable(data, s, id) && r.romanceStage > 0) KeyValue("Beziehungsstufe", Romance.stageName(data, s, id, r.romanceStage), Pn.Rose)
    }
    GlassPanel(accent = Pn.Violet) {
        StatBar("Passung", compat.score, 100, Pn.Violet)
        compat.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    s.living.conflicts[id]?.let { c ->
        GlassPanel(accent = Pn.Danger) {
            Text("Ungelöster Konflikt", style = MaterialTheme.typography.titleSmall, color = Pn.Danger)
            Text(c.reason + " · seit " + Clock.dateLabel(c.sinceDay) + " · Schwere ${c.severity}/3", style = MaterialTheme.typography.bodySmall)
            SmallNote("Belastet die Freundschaft jede Woche. Eine Aussprache (Interaktion) oder ein wirklich passendes Geschenk kann helfen.")
        }
    }
    val ms = Bonds.milestones(s, id)
    if (ms.isNotEmpty()) GlassPanel {
        Text("Meilensteine", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ms.forEach { Pill(Bonds.milestoneNames[it] ?: it, if (it in setOf("rivals", "breakup")) Pn.Danger else Pn.Ok) }
        }
    }
    val book = Memories.book(s, id)
    GlassPanel {
        Text("Erinnerungen", style = MaterialTheme.typography.titleSmall)
        if (book.entries.isEmpty() && book.summaries.isEmpty()) SmallNote("Noch keine gemeinsamen Erinnerungen.")
        book.entries.reversed().forEach { m ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("●".repeat(m.weight.coerceIn(1, 5)), style = MaterialTheme.typography.labelSmall, color = Pn.Warn)
                Column(Modifier.weight(1f)) {
                    Text(m.text, style = MaterialTheme.typography.bodySmall)
                    Text((if (m.day >= 0) Clock.dateLabel(m.day) + " · " else "") + Memories.typeLabel(m.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (book.summaries.isNotEmpty()) {
            Text("Früher", style = MaterialTheme.typography.labelLarge, color = Pn.Accent)
            book.summaries.reversed().forEach { sm ->
                val range = if (sm.fromDay >= 0) Clock.dateLabel(sm.fromDay) + " – " + Clock.dateLabel(sm.toDay) else "Vor v0.6"
                Text("$range: ${sm.count} Erinnerungen (${sm.text})" + if (sm.highlight.isNotBlank()) " · prägend: „${sm.highlight}“" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CircleFile(vm: GameViewModel, s: GameState, id: String) {
    val data = vm.data
    val others = Life.ids(data, s).filter { it != id && World.revealed(data, s, it) && s.customNpcs.none { c -> c.id == it && s.clock.day < c.createdDay } }
        .map { it to Life.bond(s, id, it) }.filter { it.second != 0 }.sortedByDescending { it.second }
    GlassPanel {
        Text("Umfeld in der Division", style = MaterialTheme.typography.titleSmall)
        Life.partnerOf(s, id)?.let { KeyValue("Partner/in", Relations.displayName(data, s, it), Pn.Rose) }
        if (others.isEmpty()) SmallNote("Noch keine engeren Kontakte – das ändert sich mit der Zeit.")
        val shown = (others.take(5) + others.takeLast(3).filter { it.second < 0 }).distinct()
        shown.forEach { (o, v) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                NpcPortrait(data, s, o, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(Relations.displayName(data, s, o), style = MaterialTheme.typography.bodyMedium)
                    Text(when { v >= 50 -> "Gute Freunde"; v >= 20 -> "Verstehen sich"; v > -20 -> "Neutral"; v > -40 -> "Reibereien"; else -> "Spannungen" },
                        style = MaterialTheme.typography.labelSmall, color = if (v >= 20) Pn.Ok else if (v <= -20) Pn.Danger else Pn.Muted)
                }
                Text(Effs.signed(v), style = MaterialTheme.typography.labelLarge, color = if (v >= 0) Pn.Ok else Pn.Danger)
            }
        }
    }
}

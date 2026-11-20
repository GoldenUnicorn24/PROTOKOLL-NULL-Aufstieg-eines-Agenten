package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import de.protokollnull.agent.model.LifeStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.GameState
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

@Composable
fun HqScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val current = data.roomById[s.location] ?: data.hq.rooms.first()
    var floor by remember { mutableIntStateOf(current.floor) }
    LaunchedEffect(s.location) { floor = data.roomById[s.location]?.floor ?: 0 }
    val pendingStory = Dialogues.pendingStory(data, s)
    val hqEvent = HqEvents.pending(data, s)
    val lifeEvent = Home.pending(s)
    val dilemma = Directorate.pendingDilemma(s)
    val quest = Quests.active(data, s).firstOrNull()

    LazyColumn(contentPadding = PaddingValues(start = Sp.gutter, end = Sp.gutter, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Overline("Lagezentrum · ${data.hq.floors.firstOrNull { it.floor == current.floor }?.name?.substringBefore(" ·") ?: "HQ"}")
                Text(greeting(s.clock.slot, s.player.codename), style = MaterialTheme.typography.titleLarge)
            }
        }
        // Wartende Entscheidungen zuerst – klar priorisiert
        if (pendingStory != null) item {
            Banner("Eingehende Nachricht: ${pendingStory.title}", "Tippen, um zu reagieren", Pn.Warn, PnIcons.Chat) {
                vm.act(silent = true) { d, st -> Dialogues.start(d, st, pendingStory.id) }
            }
        }
        if (hqEvent != null) item {
            val left = HqEvents.EXPIRE_DAYS - (s.clock.day - s.living.pendingHqEventDay)
            Banner("HQ-Ereignis: " + HqEvents.fill(data, s, hqEvent.title, hqEvent.npcId), "Reagieren · verfällt in $left Tag" + if (left == 1) "" else "en", Pn.Warn, PnIcons.People) { vm.open(Overlay.HqEvent) }
        }
        if (dilemma != null) item { Banner("Führungsentscheidung: ${dilemma.title}", "Im Direktorat entscheiden", Pn.Violet, PnIcons.Crown) { vm.open(Overlay.Director) } }
        if (lifeEvent != null) item { Banner("Privat: ${lifeEvent.title}", "Im Privatleben reagieren", Pn.Rose, PnIcons.Home) { vm.open(Overlay.City("event")) } }
        if (quest != null) item { QuestCard(vm, s, quest) }
        item { QuickActions(vm, s) }
        item { DivisionSnapshot(vm, s) }
        item {
            SectionLabel("Lageplan", trailing = "Raum antippen, um hinzugehen", icon = PnIcons.Radar)
            Spacer(Modifier.height(8.dp))
            PnTabs(data.hq.floors.map { f -> f.floor.toString() to (if (f.floor == 0) "Ebene 0" else "Ebene ${f.floor}") }, floor.toString(),
                badges = data.hq.floors.filter { f -> f.floor != current.floor && data.hq.rooms.any { it.floor == f.floor && World.npcsIn(data, s, it.id).any { id -> Dialogues.available(data, s, id).isNotEmpty() } } }.map { it.floor.toString() }.toSet()
            ) { floor = it.toInt() }
        }
        item { FloorMap(vm, s, floor) }
        item { RoomPanel(vm, s) }
    }
}

private fun greeting(slot: Int, name: String) = when (slot) {
    0 -> "Guten Morgen, $name"
    1, 2 -> "Lage am Vormittag, $name"
    3 -> "Nachmittagsbriefing, $name"
    4 -> "Guten Abend, $name"
    else -> "Nachtschicht, $name"
}

@Composable
private fun QuestCard(vm: GameViewModel, s: GameState, quest: de.protokollnull.agent.model.QuestDefinition) {
    val done = quest.objectives.count { Quests.objectiveDone(s, it) }
    GlassPanel(onClick = { vm.open(Overlay.Quests) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Aktueller Auftrag · ${quest.chapter}")
                Text(quest.title, style = MaterialTheme.typography.titleMedium)
            }
            ProgressRing(done, quest.objectives.size, 46.dp, Pn.Accent, "$done/${quest.objectives.size}", stroke = 4.dp)
        }
        quest.objectives.forEach { o ->
            val ok = Quests.objectiveDone(s, o)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PnIcon(if (ok) PnIcons.Check else PnIcons.Chevron, if (ok) Pn.Ok else Pn.Accent.copy(alpha = 0.7f), 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(o.text, style = MaterialTheme.typography.bodySmall, color = if (ok) Pn.Ok else Pn.Text)
            }
        }
    }
}

/** Schnellzugriffe auf die häufigsten Systeme – nur, was der Rang tatsächlich freischaltet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(vm: GameViewModel, s: GameState) {
    val actions = buildList {
        add(Triple("Zeitplan", PnIcons.Calendar) { vm.open(Overlay.Calendar) })
        add(Triple("Training", PnIcons.Fist) { vm.open(Overlay.Training) })
        add(Triple("Lage", PnIcons.Radar) { vm.open(Overlay.MissionControl("world")) })
        add(Triple("Karriere", PnIcons.Medal) { vm.open(Overlay.Ranks) })
        add(Triple("Stadt", PnIcons.City) { vm.open(Overlay.City("city")) })
        add(Triple("Questlog", PnIcons.Book) { vm.open(Overlay.Quests) })
        if (Command.isLeader(s)) add(Triple("Kommando", PnIcons.People) { vm.open(Overlay.Command("team")) })
        else add(Triple("Team", PnIcons.People) { vm.open(Overlay.Team) })
        if (s.director != null) add(Triple("Direktorat", PnIcons.Crown) { vm.open(Overlay.Director) })
        else add(Triple("Skills", PnIcons.Spark) { vm.open(Overlay.Skills) })
    }
    BoxWithConstraints {
        val cols = if (maxWidth < 340.dp) 3 else 4
        val w = (maxWidth - 8.dp * (cols - 1)) / cols
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (label, icon, go) ->
                Column(
                    Modifier.width(w).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0x1A3FD6C4), Color(0x083FD6C4))))
                        .border(1.dp, Pn.Line, RoundedCornerShape(14.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = go).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PnIcon(icon, Pn.Accent, 22.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = Pn.Text)
                }
            }
        }
    }
}

/** Kurzlage: Division, Aufträge, Führung – kompakt statt drei voller Karten. */
@Composable
private fun DivisionSnapshot(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val groups = Life.presenceSummary(data, s)
    val inHq = groups.filterKeys { LifeStatus.inHq(it) && it != LifeStatus.INJURED }.values.sumOf { it.size }
    val urgent = s.ops.contracts.count { it.urgency >= 2 }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Im HQ", "$inHq", Modifier.weight(1f), PnIcons.People, Pn.Accent) { vm.setTab(3) }
        StatTile("Aufträge", "${s.ops.contracts.size}" + if (urgent > 0) " · $urgent!" else "", Modifier.weight(1f), PnIcons.Target, if (urgent > 0) Pn.Warn else Pn.Accent) { vm.setTab(1) }
        if (Command.isLeader(s)) StatTile("Kohäsion", "${s.lead.cohesion}", Modifier.weight(1f), PnIcons.Shield, Pn.Ok) { vm.open(Overlay.Command("team")) }
        else StatTile("Wunden", "${s.player.wounds}", Modifier.weight(1f), PnIcons.Cross, if (s.player.wounds > 0) Pn.Danger else Pn.Ok) { vm.setTab(4) }
    }
    val latest = s.living.feed.lastOrNull { World.revealed(data, s, it.npcId) && it.type != "mission_start" }
    val world = s.ops.worldLog.lastOrNull()
    if (latest != null || world != null) {
        Spacer(Modifier.height(8.dp))
        GlassPanel(padding = 12.dp, onClick = { vm.setTab(3) }) {
            Overline("Heute in der Division")
            if (latest != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (latest.npcId != "player") NpcPortrait(data, s, latest.npcId, 32.dp)
                Text(latest.text, style = MaterialTheme.typography.bodySmall, color = feedColor(latest.type), modifier = Modifier.weight(1f))
            }
            if (world != null) Text("Weltlage: $world", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun FloorMap(vm: GameViewModel, s: GameState, floor: Int) {
    val data = vm.data
    val fdef = data.hq.floors.firstOrNull { it.floor == floor }
    val rooms = data.hq.rooms.filter { it.floor == floor }
    val cell = 100.dp
    GlassPanel(padding = 12.dp) {
        Text(fdef?.name ?: "", style = MaterialTheme.typography.titleSmall)
        Text(fdef?.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth().height(cell * 3 + 16.dp)) {
            val colW = (maxWidth - 8.dp) / 2
            rooms.forEach { r ->
                val locked = World.roomLocked(data, s, r)
                val here = s.location == r.id
                val npcs = World.npcsIn(data, s, r.id)
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier.offset(x = colW * r.x + (if (r.x > 0) 8.dp else 0.dp), y = cell * r.y + 8.dp * r.y)
                        .size(width = colW * r.w + (if (r.w > 1) 8.dp else 0.dp), height = cell * r.h + (if (r.h > 1) 8.dp else 0.dp))
                        .clip(shape)
                        .background(
                            if (here) Brush.linearGradient(listOf(Color(0x553FD6C4), Color(0x22182839)))
                            else Brush.linearGradient(listOf(Color(0x33182839), Color(0x22182839)))
                        )
                        .border(if (here) 2.dp else 1.dp, if (locked != null) Color(0x33E8636C) else if (here) Pn.Accent else Pn.Line, shape)
                        .clickable { vm.act(silent = true) { d, st -> World.moveTo(d, st, r.id) } }
                        .padding(10.dp)
                ) {
                    Column {
                        PnIcon(PnIcons.forRoom(r.id), if (locked != null) Pn.Muted else if (here) Pn.Accent else Pn.Text.copy(alpha = 0.8f), 22.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(r.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = if (locked != null) Pn.Muted else Pn.Text, maxLines = 2, lineHeight = 15.sp)
                        if (locked != null) Row(verticalAlignment = Alignment.CenterVertically) { PnIcon(PnIcons.Lock, Pn.Danger, 12.dp); Spacer(Modifier.width(3.dp)); Text("gesperrt", style = MaterialTheme.typography.labelSmall, color = Pn.Danger) }
                    }
                    if (here) Box(Modifier.align(Alignment.BottomStart)) { PulseDot(size = 7.dp) }
                    if (npcs.isNotEmpty()) Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        npcs.take(3).forEach { id -> NpcPortrait(data, s, id, 22.dp, height = 22.dp) }
                        if (npcs.size > 3) Pill("+${npcs.size - 3}", Pn.Violet)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val room = data.roomById[s.location] ?: return
    val npcs = World.npcsIn(data, s, room.id)
    val encounter = World.encounter(data, s, room.id)
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(PnIcons.forRoom(room.id), size = 46.dp)
            Column {
                Overline("Du bist hier · " + (data.hq.floors.firstOrNull { it.floor == room.floor }?.name?.substringBefore(" ·") ?: ""))
                Text(room.name.uppercase(), style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(room.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (npcs.isNotEmpty()) {
            Overline("Anwesend · antippen für Personalakte", Pn.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                npcs.forEach { id ->
                    val hasTalk = Dialogues.available(data, s, id).isNotEmpty()
                    Column(Modifier.width(78.dp).clickable { vm.open(Overlay.Npc(id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            NpcPortrait(data, s, id, 70.dp)
                            if (hasTalk) Pill("!", Pn.Warn, Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                        Text(Relations.displayName(data, s, id).substringBefore(" "), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        } else Text("Niemand hier – gerade ${Clock.slotNames[s.clock.slot]}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (encounter != null) {
        Spacer(Modifier.height(12.dp))
        GlassPanel(accent = Pn.Violet) {
            Overline("Begegnung", Pn.Violet)
            Text(encounter.title, style = MaterialTheme.typography.titleMedium)
            Text(encounter.description, style = MaterialTheme.typography.bodyMedium)
            encounter.choices.forEach { c -> ChoiceButton(c.text, if (c.xp > 0) "+${c.xp} XP · 1 Slot" else null) { vm.act { d, st -> World.resolveEncounter(d, st, encounter.id, c.id) } } }
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionLabel("Aktionen", icon = PnIcons.Bolt)
    Spacer(Modifier.height(8.dp))
    room.actions.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            row.forEach { a ->
                val info = World.actionInfo(a)
                ActionButton(info.label, Modifier.weight(1f), secondary = true, subtitle = info.hint, icon = actionIcon(a)) { roomAction(vm, s, a) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private fun actionIcon(a: String) = when (a) {
    "sleep", "rest" -> PnIcons.Bed; "storage" -> PnIcons.Box; "timeskip" -> PnIcons.Calendar; "eat" -> PnIcons.Food
    "socialize" -> PnIcons.People; "training" -> PnIcons.Fist; "range" -> PnIcons.Target; "treat" -> PnIcons.Cross
    "shop", "repair" -> PnIcons.Bag; "missions" -> PnIcons.Radar; "tech_lab" -> PnIcons.Chip; "therapy" -> PnIcons.Brain
    "promotion" -> PnIcons.Star; "director_desk" -> PnIcons.Crown; "research" -> PnIcons.Archive; "team" -> PnIcons.People
    "cafe" -> PnIcons.Food; "apartment" -> PnIcons.Hq; "cover_job" -> PnIcons.City; "city" -> PnIcons.City; "command" -> PnIcons.People; "study" -> PnIcons.Brain; "leaderboard" -> PnIcons.Chart
    else -> PnIcons.Chevron
}

private fun roomAction(vm: GameViewModel, s: GameState, a: String) {
    when (a) {
        "sleep" -> vm.act { d, st -> World.sleep(d, st) }
        "rest" -> vm.act { d, st -> World.rest(d, st) }
        "storage" -> vm.open(Overlay.Storage)
        "timeskip", "routine" -> vm.open(Overlay.Calendar)
        "eat" -> vm.act { d, st -> World.eat(d, st) }
        "socialize" -> vm.act { d, st -> World.socialize(d, st) }
        "training", "range", "tech_lab", "study" -> vm.open(Overlay.Training)
        "treat" -> vm.act { d, st -> World.treat(d, st) }
        "shop", "repair" -> vm.open(Overlay.Shop)
        "missions" -> vm.setTab(1)
        "therapy" -> vm.act { d, st -> World.therapy(d, st) }
        "promotion" -> vm.open(Overlay.Ranks)
        "director_desk" -> vm.open(Overlay.Director)
        "research" -> vm.act { d, st -> World.research(d, st) }
        "team" -> vm.open(Overlay.Team)
        "cafe" -> vm.act { d, st -> World.cafe(d, st) }
        "apartment" -> vm.open(Overlay.City("home"))
        "cover_job" -> vm.open(Overlay.City("job"))
        "city" -> vm.open(Overlay.City("city"))
        "command" -> vm.open(Overlay.Command("team"))
        "leaderboard" -> vm.open(Overlay.CareerView("board"))
    }
}

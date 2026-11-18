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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val quest = Quests.active(data, s).firstOrNull()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (pendingStory != null) item {
            GlassPanel(accent = Pn.Warn, onClick = { vm.act(silent = true) { d, st -> Dialogues.start(d, st, pendingStory.id) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) { PulseDot(Pn.Warn, 6.dp); Spacer(Modifier.width(6.dp)); Text("EINGEHENDE NACHRICHT", style = MaterialTheme.typography.labelLarge, color = Pn.Warn) }
                Text(pendingStory.title, style = MaterialTheme.typography.titleMedium)
                Text("Tippen, um zu reagieren", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (quest != null) item {
            GlassPanel(onClick = { vm.open(Overlay.Quests) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AKTUELLER AUFTRAG · ${quest.chapter.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                        Text(quest.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = Pn.Accent)
                }
                quest.objectives.forEach { o ->
                    val done = Quests.objectiveDone(s, o)
                    Text((if (done) "✓ " else "○ ") + o.text, style = MaterialTheme.typography.bodySmall, color = if (done) Pn.Ok else Pn.Text)
                }
            }
        }
        item {
            SectionLabel("Hauptquartier · Lageplan", trailing = "Raum antippen", icon = PnIcons.Radar)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.hq.floors.forEach { f ->
                    FilterChip(selected = floor == f.floor, onClick = { floor = f.floor }, label = { Text(if (f.floor == 0) "Ebene 0" else "Ebene ${f.floor}") })
                }
            }
        }
        item { FloorMap(vm, s, floor) }
        item { RoomPanel(vm, s) }
    }
}

@Composable
private fun FloorMap(vm: GameViewModel, s: GameState, floor: Int) {
    val data = vm.data
    val fdef = data.hq.floors.firstOrNull { it.floor == floor }
    val rooms = data.hq.rooms.filter { it.floor == floor }
    val cell = 96.dp
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
                        Text(r.name, style = MaterialTheme.typography.labelLarge, color = if (locked != null) Pn.Muted else Pn.Text, maxLines = 2)
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
                Text("STANDORT · " + (data.hq.floors.firstOrNull { it.floor == room.floor }?.name?.substringBefore(" ·") ?: ""), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                Text(room.name.uppercase(), style = MaterialTheme.typography.titleLarge)
            }
        }
        Text(room.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (npcs.isNotEmpty()) {
            Text("Anwesend", style = MaterialTheme.typography.titleSmall)
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
            Text("BEGEGNUNG", style = MaterialTheme.typography.labelSmall, color = Pn.Violet)
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
    "cafe" -> PnIcons.Food; "apartment" -> PnIcons.Hq; "cover_job" -> PnIcons.City; "study" -> PnIcons.Brain
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
        "apartment" -> vm.act { d, st -> World.rentApartment(d, st) }
        "cover_job" -> vm.act { d, st -> World.takeCoverJob(d, st) }
    }
}

@Composable
fun TrainingScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val present = World.npcsIn(data, s, s.location).filter { id -> Relations.get(data, s, id).friendship >= 25 }
    var partner by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Training", data.roomById[s.location]?.name ?: "", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassPanel {
                    Text("Attributlimit bei Level ${s.player.level}: ${Stats.attributeCap(s.player.level)}", style = MaterialTheme.typography.titleSmall)
                    de.protokollnull.agent.model.Attr.all.forEach { a ->
                        StatBar(de.protokollnull.agent.model.Attr.label(a), s.player.attributes[a] ?: 30, 100)
                    }
                }
            }
            if (present.isNotEmpty()) item {
                GlassPanel {
                    Text("Trainingspartner (Freundschaft + Bonus)", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("" to "Allein") + present.map { it to Relations.displayName(data, s, it).substringBefore(" ") }, partner ?: "") { partner = it.ifBlank { null } }
                }
            }
            Progression.trainings.forEach { t ->
                item {
                    ChoiceButton(t.name, "${de.protokollnull.agent.model.Attr.label(t.attribute)} · Energie −${t.energy} · Stress ${Effs.signed(t.stress)} · +${t.xp} XP · 1 Slot", enabled = s.player.energy >= t.energy) {
                        vm.act { d, st -> Progression.train(d, st, t.id, partner) }
                    }
                }
            }
        }
    }
}

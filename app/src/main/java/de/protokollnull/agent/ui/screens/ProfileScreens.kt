package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

@Composable
fun ProfileScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val p = s.player
    val next = Progression.nextRankCheck(data, s)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PlayerPortrait(s, 96.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.codename.uppercase(), style = MaterialTheme.typography.titleLarge, color = Pn.Accent)
                        Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("${Ages.player(s)} Jahre · ${data.rank(p.rank).name}", style = MaterialTheme.typography.bodySmall)
                        Text("Ruf ${p.reputation} · Wunden ${p.wounds}" + (p.coverJob?.let { " · Tarnjob: $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StatBar("Level ${p.level} – ${p.xp} / ${Progression.requiredXp(p.level)} XP", p.xp, Progression.requiredXp(p.level), showValue = false)
                Text("Freie Fähigkeitspunkte: ${p.skillPoints}", style = MaterialTheme.typography.labelLarge, color = if (p.skillPoints > 0) Pn.Warn else Pn.Muted)
            }
        }
        item {
            SectionLabel("Attribute", trailing = "Limit ${Stats.attributeCap(p.level)}")
            Spacer(Modifier.height(6.dp))
            GlassPanel(padding = 12.dp) {
                Attr.all.forEach { a ->
                    val base = p.attributes[a] ?: 30
                    val eff = Stats.effectiveAttribute(data, s, a)
                    StatBar(Attr.label(a) + (if (eff != base) " (${Effs.signed(eff - base)} Ausrüstung)" else "") + (if (a == p.strength) " ★" else if (a == p.weakness) " ▼" else ""), eff, 100)
                }
            }
        }
        item {
            SectionLabel("Laufbahn")
            Spacer(Modifier.height(6.dp))
            val buttons = listOf(
                Triple("Skilltree", "${p.skills.values.sum()} Stufen gelernt", Overlay.Skills),
                Triple("Karriere & Prüfungen", next?.let { "Nächster: ${it.rank.name}" } ?: "Höchster Rang", Overlay.Ranks),
                Triple("Questlog", "${Quests.active(data, s).size} aktiv · ${Quests.completed(data, s).size} erledigt", Overlay.Quests),
                Triple("Zeitplan & Kalender", Clock.dateLabel(s.clock.day), Overlay.Calendar),
                Triple("Einsatzberichte", "${s.reports.size} Berichte", Overlay.Reports),
                Triple("Team", "${s.team.size}/${Progression.teamSize(data, s)} Plätze", Overlay.Team),
                Triple("Neuer Agent (Editor)", if (Social.canCreateRecruit(s)) "freigeschaltet" else if (Ages.player(s) < 18) "ab 18 (noch ${Clock.daysUntilAge(s.clock.day, 18)} Tage)" else "max. 3", Overlay.Recruit),
                Triple("Direktorat", if (s.director != null) "Budget ${s.director.budget} Cr" else "ab Rang 12", Overlay.Director)
            )
            buttons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { (t, sub, o) -> ActionButton(t, Modifier.weight(1f), secondary = true, subtitle = sub) { vm.open(o) } }
                }
            }
        }
        item {
            SectionLabel("System")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Spielstände", Modifier.weight(1f), secondary = true) { vm.open(Overlay.Saves) }
                ActionButton("Einstellungen", Modifier.weight(1f), secondary = true) { vm.open(Overlay.Settings) }
                ActionButton("Credits", Modifier.weight(1f), secondary = true) { vm.open(Overlay.Credits) }
            }
        }
        item {
            SectionLabel("Fraktionen")
            Spacer(Modifier.height(6.dp))
            GlassPanel(padding = 12.dp) {
                data.factions.forEach { f ->
                    val v = s.factionStanding[f.id] ?: 0
                    KeyValue(f.name, (if (v > 0) "+" else "") + v, if (v < -10) Pn.Danger else if (v > 10) Pn.Ok else Pn.Text)
                }
            }
        }
    }
}

private val treeNames = linkedMapOf(
    "kampf" to "Kampf", "tarnung" to "Tarnung", "psychologie" to "Psychologie", "technik" to "Technik", "diplomatie" to "Diplomatie",
    "fuehrung" to "Führung", "medizin" to "Medizin", "aufklaerung" to "Aufklärung", "ueberleben" to "Überleben"
)

@Composable
fun SkillTreeScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var zoom by remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Skilltree", "${s.player.skillPoints} Punkte frei", onBack = { vm.back() }, trailing = {
            Row { TextButton(onClick = { zoom = (zoom - 0.15f).coerceAtLeast(0.7f) }) { Text("−") }; TextButton(onClick = { zoom = (zoom + 0.15f).coerceAtMost(1.3f) }) { Text("+") } }
        })
        Column(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp).graphicsLayer(scaleX = zoom, scaleY = zoom, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            treeNames.forEach { (tree, name) ->
                val skills = data.skills.filter { it.tree == tree }
                Column {
                    Text(name.uppercase() + " · " + Attr.label(skills.firstOrNull()?.attribute ?: ""), style = MaterialTheme.typography.labelLarge, color = Pn.Accent)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        skills.forEachIndexed { i, sk ->
                            if (i > 0) Box(Modifier.width(18.dp).height(2.dp).background(if ((s.player.skills[skills[i - 1].id] ?: 0) > 0) Pn.Accent else Pn.Line))
                            SkillNode(vm, s, sk)
                        }
                    }
                }
            }
            SmallNote("Meisterperks (rechts) benötigen zusätzlich eine Fähigkeit aus einem anderen Baum.")
        }
    }
}

@Composable
private fun SkillNode(vm: GameViewModel, s: GameState, sk: SkillDefinition) {
    val data = vm.data
    val rank = s.player.skills[sk.id] ?: 0
    val can = Progression.skillPrereqsMet(sk, s.player) && rank < sk.maxRank && s.player.skillPoints > 0
    val unlocked = Progression.skillPrereqsMet(sk, s.player)
    val shape = RoundedCornerShape(14.dp)
    val color = when { rank >= sk.maxRank -> Pn.Warn; rank > 0 -> Pn.Accent; unlocked -> Pn.Text; else -> Pn.Muted }
    Column(
        Modifier.width(150.dp).clip(shape).background(if (rank > 0) Color(0x223FD6C4) else Color(0x10FFFFFF)).border(1.dp, color.copy(alpha = 0.6f), shape)
            .clickable(enabled = can) { vm.act { d, x -> Progression.learnSkill(d, x, sk.id) } }.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(sk.name, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 2)
        Text((if (sk.kind == "active") "Aktiv" else "Passiv") + " · ab Lv ${sk.minLevel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row { repeat(sk.maxRank) { i -> Box(Modifier.padding(end = 3.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(if (i < rank) color else Color(0x22FFFFFF))) } }
        Text(sk.effect, style = MaterialTheme.typography.labelSmall, color = Pn.Text, maxLines = 3)
        if (!unlocked) Text("Benötigt: " + (listOfNotNull(sk.prerequisite) + sk.requires).joinToString { data.skillById[it]?.name ?: it }, style = MaterialTheme.typography.labelSmall, color = Pn.Danger, maxLines = 2)
        else if (can) Text("Tippen: lernen", style = MaterialTheme.typography.labelSmall, color = Pn.Ok)
    }
}

@Composable
fun QuestLogScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Questlog", "Kampagne", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionLabel("Aktiv") }
            items(Quests.active(data, s)) { q ->
                GlassPanel {
                    Text(q.chapter.uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                    Text(q.title, style = MaterialTheme.typography.titleMedium)
                    Text(q.description, style = MaterialTheme.typography.bodySmall)
                    q.objectives.forEach { o -> val done = Quests.objectiveDone(s, o); Text((if (done) "✓ " else "○ ") + o.text, color = if (done) Pn.Ok else Pn.Text, style = MaterialTheme.typography.bodySmall) }
                    q.giverId?.let { Text("Kontakt: " + Relations.displayName(data, s, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item { SectionLabel("Abgeschlossen") }
            items(Quests.completed(data, s)) { q -> GlassPanel(accent = Pn.Ok, padding = 12.dp) { Text("✓ " + q.title, style = MaterialTheme.typography.titleSmall, color = Pn.Ok) } }
            item { SectionLabel("Zeitleiste") }
            items(s.log.reversed().take(40)) { e ->
                Row { Text(Clock.dateLabel(e.day).substringAfter(", ") + " " + Clock.slotTimes[e.slot], style = MaterialTheme.typography.labelSmall, color = Pn.Accent, modifier = Modifier.width(120.dp)); Text(e.text, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun ReportsScreen(vm: GameViewModel, s: GameState) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Einsatzberichte", "${s.reports.size} Einträge", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.reports.isEmpty()) item { SmallNote("Noch keine Berichte.") }
            items(s.reports.reversed()) { ReportCard(it) }
        }
    }
}

@Composable
fun TeamScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val size = Progression.teamSize(data, s)
    val candidates = Missions.teamCandidates(data, s, MissionDefinition(id = "team", title = "", locationId = "hq", type = "x", threat = 1))
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Team", "${s.team.size}/$size Plätze", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassPanel {
                    Text(when {
                        s.player.rank >= 9 -> "Einsatzkommandant: Du kannst Aufträge vom Einsatzboard an dein Team delegieren."
                        s.player.rank >= 8 -> "Teamleiter: Teamkommando „Teambefehl“ im Taktikmodus (+1 AP für alle)."
                        else -> "Als Rekrut/in teilt dir die Division bis zu ${size} Partner zu. Ab Rang 8 führst du eigene Teams."
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(candidates) { id ->
                val inTeam = id in s.team
                val r = Relations.get(data, s, id)
                GlassPanel(padding = 12.dp, accent = if (inTeam) Pn.Ok else Pn.Accent) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NpcPortrait(data, s, id, 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text(Relations.displayName(data, s, id), style = MaterialTheme.typography.titleSmall)
                            Text("Loyalität ${r.loyalty} · Vertrauen ${r.trust} · " + (Tactical.abilities[data.agentById[id]?.teamAbility]?.name ?: "Koordination"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ActionButton(if (inTeam) "Entfernen" else "Aufnehmen", secondary = !inTeam, enabled = inTeam || s.team.size < size) {
                            vm.update { st -> st.copy(team = if (inTeam) st.team - id else st.team + id) }
                        }
                    }
                }
            }
            if (s.player.rank >= 9 && s.board.isNotEmpty() && s.team.isNotEmpty()) {
                item { SectionLabel("Aufträge delegieren") }
                items(s.board) { m ->
                    ChoiceButton("Delegieren: " + m.title, "Gefahr ${m.threat} · Team ${s.team.size}") { vm.act { d, x -> Director.delegate(d, x, m, x.team) } }
                }
            }
        }
    }
}

@Composable
fun DirectorScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val d = s.director
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Direktorat", if (d != null) "Budget ${d.budget} Cr" else "gesperrt", onBack = { vm.back() })
        if (d == null) {
            Column(Modifier.padding(16.dp)) {
                GlassPanel {
                    Text("Das Direktorat wird mit Rang 12 (Direktor) freigeschaltet.", style = MaterialTheme.typography.bodyMedium)
                    Text("Voraussetzungen: Level 60, Alter 29, Ruf 900, Vertrauen von Percy und die Mission „Abstimmung im Direktorat“.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassPanel {
                    StatBar("Moral des Personals", d.morale, 100, Pn.Ok)
                    StatBar("Rückhalt der Aufsicht", d.oversight, 100, Pn.Violet)
                    if (d.lastWeekReport.isNotBlank()) SmallNote("Letzte Woche: " + d.lastWeekReport)
                }
            }
            item { SectionLabel("Abteilungen") }
            items(Director.departments) { (id, name) ->
                val lvl = d.departments[id] ?: 1
                GlassPanel(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$name · Stufe $lvl/5", style = MaterialTheme.typography.titleSmall)
                            Text("Leitung: " + (d.heads[id]?.let { Relations.displayName(data, s, it) } ?: "vakant"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ActionButton(if (lvl >= 5) "Maximal" else "Ausbauen · ${Director.upgradeCost(lvl)}", enabled = lvl < 5 && d.budget >= Director.upgradeCost(lvl), secondary = true) {
                            vm.act { dd, x -> Director.upgrade(dd, x, id) }
                        }
                    }
                    val heads = Missions.teamCandidates(data, s, MissionDefinition(id = "h", title = "", locationId = "hq", type = "x", threat = 3)).take(6)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        heads.forEach { h -> FilterChip(selected = d.heads[id] == h, onClick = { vm.act { dd, x -> Director.assignHead(dd, x, id, if (d.heads[id] == h) null else h) } }, label = { Text(Relations.displayName(data, s, h).substringBefore(" ")) }) }
                    }
                }
            }
            item { SectionLabel("Leitlinie") }
            items(Director.policies) { (id, name) ->
                ChoiceButton(name + if (d.policy == id) "  ✓" else "", when (id) { "transparent" -> "Aufsicht +, Geheimnisse riskanter"; "secrecy" -> "Aufsicht −, mehr Spielraum"; "people" -> "Moral +"; else -> "Stabil" }) {
                    vm.act { _, x -> Director.setPolicy(x, id) }
                }
            }
            item { SmallNote("Direktor-Endgame: Generierte Missionen bleiben frei spielbar; das Einsatzboard zeigt Aufträge höherer Gefahrenstufe.") }
        }
    }
}

// ---------------------------------------------------------------------------
// Rekrutierungseditor ab 18: neue/r 16-jährige/r Agent/in
// ---------------------------------------------------------------------------

@Composable
fun RecruitEditorScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val opposite = if (s.player.gender == "male") "female" else "male"
    var first by remember { mutableStateOf(if (opposite == "female") "Mira" else "Jonas") }
    var last by remember { mutableStateOf("Kessler") }
    var gender by remember { mutableStateOf(opposite) }
    var avatar by remember { mutableStateOf(AvatarSpec(skin = 2, hairStyle = if (opposite == "female") 3 else 0, hairColor = 2, outfit = 4)) }
    var traits by remember { mutableStateOf(listOf("loyal", "neugierig")) }
    var background by remember { mutableStateOf("Aufgewachsen in einer Pflegefamilie, über ein Begabtenprogramm entdeckt.") }
    var profession by remember { mutableStateOf("field") }
    var interests by remember { mutableStateOf(listOf("Musik", "Klettern")) }
    var skills by remember { mutableStateOf(mapOf("observe" to 1, "empathy" to 1, "stamina" to 1, "analysis" to 1, "first_aid" to 1)) }
    val points = 8 - skills.values.sum()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Neuer Agent", "Rekrutierungseditor", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!Social.canCreateRecruit(s)) {
                GlassPanel(accent = Pn.Warn) {
                    Text(if (Ages.player(s) < 18) "Freischaltung mit deinem 18. Geburtstag (noch ${Clock.daysUntilAge(s.clock.day, 18)} Tage). Tipp: Zeitraffer im Quartier." else "Du hast bereits drei eigene Rekruten.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            s.customNpcs.forEach { c ->
                GlassPanel(padding = 12.dp, onClick = { vm.open(Overlay.Npc(c.id)) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NpcPortrait(data, s, c.id, 48.dp)
                        Column { Text(c.name, style = MaterialTheme.typography.titleSmall); Text("${Ages.npc(data, s, c.id)} J. · ${Social.professionLabel(c.profession)} · ${data.rank(Life.rank(data, s, c.id)).name}", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (Social.canCreateRecruit(s)) {
                GlassPanel {
                    Text("Die neue Figur ist 16 Jahre alt und kommt als eigenständige/r Rekrut/in in die Division. Sie ist nicht sofort deine Partnerin bzw. dein Partner: Vertrauen entsteht über Zeit. Romantische Optionen gibt es erst, wenn beide volljährig sind.", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(width = 96.dp, height = 120.dp)) { AvatarCanvas(avatar, Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(first, { first = it.take(16) }, label = { Text("Vorname") }, singleLine = true)
                        OutlinedTextField(last, { last = it.take(16) }, label = { Text("Nachname") }, singleLine = true)
                    }
                }
                GlassPanel {
                    Text("Geschlecht (Standard: anderes als deins)", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("female" to "Weiblich", "male" to "Männlich", "diverse" to "Divers"), gender) { gender = it }
                    PortraitUpload(avatar) { avatar = it }
                    if (avatar.portraitId == null) AvatarControls(avatar) { avatar = it }
                }
                GlassPanel {
                    Text("Charakterzüge (bis 3)", style = MaterialTheme.typography.titleSmall)
                    MultiChips(listOf("loyal", "neugierig", "mutig", "ruhig", "humorvoll", "ehrgeizig", "kreativ", "technik", "vorsichtig"), traits, 3) { traits = it }
                    Text("Interessen (bis 3)", style = MaterialTheme.typography.titleSmall)
                    MultiChips(listOf("Musik", "Klettern", "Bücher", "Kochen", "Schach", "Fotografie", "Sprachen", "Laufen", "Zeichnen"), interests, 3) { interests = it }
                    Text("Startberuf", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("field" to "Außendienst", "intel" to "Analyse", "tech" to "Technik", "medic" to "Sanitätsdienst"), profession) { profession = it }
                    OutlinedTextField(background, { background = it.take(160) }, label = { Text("Hintergrund") }, modifier = Modifier.fillMaxWidth())
                }
                GlassPanel {
                    Text("Fähigkeiten verteilen · $points Punkte frei", style = MaterialTheme.typography.titleSmall)
                    skills.keys.toList().forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(data.skillById[k]?.name ?: k, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            ActionButton("−", secondary = true, enabled = (skills[k] ?: 0) > 0) { skills = skills + (k to (skills[k] ?: 0) - 1) }
                            Text("${skills[k]}", modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                            ActionButton("+", secondary = true, enabled = points > 0 && (skills[k] ?: 0) < 3) { skills = skills + (k to (skills[k] ?: 0) + 1) }
                        }
                    }
                }
            }
        }
        if (Social.canCreateRecruit(s)) Box(Modifier.padding(16.dp)) {
            ActionButton("Rekrut/in in die Division holen", Modifier.fillMaxWidth(), enabled = first.isNotBlank()) {
                vm.act { d, x -> Social.createRecruit(d, x, first, last, gender, avatar, traits, background, profession, interests, skills) }
                vm.back()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChips(options: List<String>, selected: List<String>, max: Int, onChange: (List<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val sel = o in selected
            FilterChip(selected = sel, onClick = { onChange(if (sel) selected - o else if (selected.size < max) selected + o else selected) }, label = { Text(o) })
        }
    }
}

package de.protokollnull.agent.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

// ---------------------------------------------------------------------------
// Charaktereditor
// ---------------------------------------------------------------------------

@Composable
fun CreatorScreen(vm: GameViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("Lena") }
    var last by remember { mutableStateOf("Berg") }
    var code by remember { mutableStateOf("Null") }
    var gender by remember { mutableStateOf("female") }
    var avatar by remember { mutableStateOf(AvatarSpec(skin = 1, hairStyle = 4, hairColor = 1, outfit = 0)) }
    var style by remember { mutableStateOf("sachlich") }
    var background by remember { mutableStateOf("analyst") }
    var strength by remember { mutableStateOf(Attr.PERCEPTION) }
    var weakness by remember { mutableStateOf(Attr.CHARISMA) }
    var difficulty by remember { mutableStateOf("normal") }
    val steps = listOf("Identität", "Aussehen", "Hintergrund", "Einsatzprofil")

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Agent erstellen", "Schritt ${step + 1}/4 · ${steps[step]}", onBack = { if (step > 0) step-- else vm.goToStart() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 96.dp, height = 120.dp)) { AvatarCanvas(avatar, Modifier.fillMaxSize()) }
                Column {
                    Text(code.ifBlank { "—" }.uppercase(), style = MaterialTheme.typography.titleLarge, color = Pn.Accent)
                    Text("$first $last", style = MaterialTheme.typography.titleSmall)
                    Text("Kandidat 7 · Projekt PHOENIX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (step) {
                0 -> GlassPanel {
                    OutlinedTextField(first, { first = it.take(18) }, label = { Text("Vorname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(last, { last = it.take(18) }, label = { Text("Nachname (Tarnidentität)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(code, { code = it.take(14) }, label = { Text("Deckname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Geschlecht", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("female" to "Weiblich", "male" to "Männlich", "diverse" to "Divers"), gender) { gender = it }
                    Text("Auftreten", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("sachlich" to "Sachlich", "charmant" to "Charmant", "verschlossen" to "Verschlossen", "rebellisch" to "Rebellisch"), style) { style = it }
                }
                1 -> GlassPanel {
                    OptionStepper("Hautton", AvatarPalette.skinNames, avatar.skin) { avatar = avatar.copy(skin = it) }
                    OptionStepper("Frisur", AvatarPalette.hairNames, avatar.hairStyle) { avatar = avatar.copy(hairStyle = it) }
                    OptionStepper("Haarfarbe", AvatarPalette.hairColorNames, avatar.hairColor) { avatar = avatar.copy(hairColor = it) }
                    OptionStepper("Augen", listOf("Braun", "Blau", "Grün"), avatar.eyes) { avatar = avatar.copy(eyes = it) }
                    OptionStepper("Kleidung", AvatarPalette.outfitNames, avatar.outfit) { avatar = avatar.copy(outfit = it) }
                    OptionStepper("Zubehör", AvatarPalette.accessoryNames, avatar.accessory) { avatar = avatar.copy(accessory = it) }
                }
                2 -> GlassPanel {
                    Text("Was hat PHOENIX aus dir gemacht?", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "athlete" to ("Athlet/in" to "Fitness & Willenskraft hoch, Start-Fähigkeit Ausdauer"),
                        "analyst" to ("Analytiker/in" to "Intellekt & Wahrnehmung hoch, Start-Fähigkeit Datenanalyse"),
                        "social" to ("Menschenleser/in" to "Ausstrahlung & Willenskraft hoch, Start-Fähigkeit Empathie"),
                        "marksman" to ("Präzisionsschütze/in" to "Präzision & Wahrnehmung hoch, Start-Fähigkeit Defensive")
                    ).forEach { (id, t) -> ChoiceButton(t.first, t.second, enabled = true) { background = id }; if (background == id) Pill("Gewählt") }
                    Text("Stärke (+6)", style = MaterialTheme.typography.titleSmall)
                    ChipRow(Attr.all.map { it to Attr.label(it) }, strength) { if (it != weakness) strength = it }
                    Text("Schwäche (−8)", style = MaterialTheme.typography.titleSmall)
                    ChipRow(Attr.all.map { it to Attr.label(it) }, weakness) { if (it != strength) weakness = it }
                    SmallNote("Der PHOENIX-Vorteil zeigt sich nicht als Allmacht, sondern in klaren Stärken – und einer echten Schwäche.")
                }
                else -> GlassPanel {
                    Text("Schwierigkeit", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("easy" to "Leicht", "normal" to "Normal", "hard" to "Hart"), difficulty) { difficulty = it }
                    SmallNote(when (difficulty) { "easy" -> "Proben +10, mehr XP, 600 Startcredits."; "hard" -> "Proben −10, weniger XP, 150 Startcredits."; else -> "Ausgewogene Proben, 300 Startcredits." })
                    Spacer(Modifier.height(8.dp))
                    KeyValue("Name", "$first $last")
                    KeyValue("Deckname", code)
                    KeyValue("Hintergrund", mapOf("athlete" to "Athlet/in", "analyst" to "Analytiker/in", "social" to "Menschenleser/in", "marksman" to "Präzisionsschütze/in")[background] ?: background)
                    KeyValue("Stärke / Schwäche", Attr.label(strength) + " / " + Attr.label(weakness))
                    SmallNote("Der Prolog beginnt mit 13 Jahren. Ab 16 bist du Rekrut/in der Division.")
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) ActionButton("Zurück", Modifier.weight(1f), secondary = true) { step-- }
            if (step < 3) ActionButton("Weiter", Modifier.weight(1f), enabled = first.isNotBlank() && code.isNotBlank()) { step++ }
            else ActionButton("Prolog beginnen", Modifier.weight(1f), enabled = first.isNotBlank() && code.isNotBlank()) {
                vm.startGame(PlayerCharacter(
                    firstName = first.trim(), lastName = last.trim(), codename = code.trim(), gender = gender,
                    appearance = Appearance(avatar, style), background = background, strength = strength,
                    weakness = weakness, difficulty = difficulty
                ))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (id, label) -> FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(label) }) }
    }
}

@Composable
fun OptionStepper(label: String, names: List<String>, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        ActionButton("‹", secondary = true) { onChange((value - 1 + names.size) % names.size) }
        Text(names[value.mod(names.size)], style = MaterialTheme.typography.labelLarge, color = Pn.Accent, modifier = Modifier.width(130.dp).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        ActionButton("›", secondary = true) { onChange((value + 1) % names.size) }
    }
}

// ---------------------------------------------------------------------------
// Prolog (13–16 Jahre)
// ---------------------------------------------------------------------------

@Composable
fun PrologueScreen(vm: GameViewModel, s: GameState) {
    val node = Prologue.node(vm.data, s)
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(node?.chapter ?: "Prolog", "Erinnerung", trailing = {
            TextButton(onClick = { vm.act { d, st -> Prologue.skip(d, st) } }) { Text("Überspringen") }
        })
        val anim = animOn()
        AnimatedContent(targetState = node?.id, transitionSpec = { if (anim) fadeIn() togetherWith fadeOut() else fadeIn(snap()) togetherWith fadeOut(snap()) }, label = "prolog") { _ ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (node == null) {
                    ActionButton("Weiter") { vm.act { d, st -> Prologue.finish(d, st) } }
                    return@Column
                }
                if (node.speakerId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NpcPortrait(vm.data, s, node.speakerId, 64.dp)
                        Column {
                            Text(vm.data.agentById[node.speakerId]?.name ?: node.speakerId, style = MaterialTheme.typography.titleMedium, color = Pn.Accent)
                            Text(vm.data.agentById[node.speakerId]?.role ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                node.age?.let { Pill("Alter: $it") }
                GlassPanel {
                    Text(Dialogues.format(node.text, s), style = MaterialTheme.typography.bodyLarge, fontStyle = if (node.speakerId == null) FontStyle.Italic else FontStyle.Normal)
                }
                node.choices.forEach { c ->
                    val fx = c.effects
                    val hint = fx?.attributes?.entries?.joinToString { Attr.label(it.key) + " +" + it.value }?.takeIf { it.isNotBlank() }
                    ChoiceButton(c.text, hint) { vm.act(silent = true) { d, st -> Prologue.choose(d, st, c.id) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialoge mit Charakterporträts
// ---------------------------------------------------------------------------

@Composable
fun DialogueScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val cur = Dialogues.current(data, s)
    if (cur == null) {
        LaunchedEffect(Unit) { vm.update { it.copy(activeDialogue = null) } }
        return
    }
    val (d, n) = cur
    val speaker = n.speakerId ?: d.npcId
    val choices = Dialogues.visibleChoices(data, s)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(d.title, d.chapter.ifBlank { "Gespräch" })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (speaker != null && speaker != "narrator") {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NpcPortrait(data, s, speaker, 110.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Relations.displayName(data, s, speaker), style = MaterialTheme.typography.titleLarge, color = Pn.Accent)
                        Text(data.agentById[speaker]?.role ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (Relations.isKnownNpc(data, s, speaker)) {
                            val r = Relations.get(data, s, speaker)
                            Spacer(Modifier.height(6.dp))
                            StatBar("Vertrauen", r.trust, 100)
                        }
                    }
                }
            }
            s.activeDialogue?.history?.takeLast(2)?.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic) }
            s.activeDialogue?.lastResult?.takeIf { it.isNotBlank() }?.let { Pill(it, if (it.contains("gelungen")) Pn.Ok else Pn.Danger) }
            GlassPanel { Text(Dialogues.format(n.text, s), style = MaterialTheme.typography.bodyLarge) }
            choices.forEach { c ->
                val chance = Dialogues.choiceChance(data, s, c)
                val detail = c.check?.let { Attr.label(it.attribute) + (it.skill?.let { sk -> " · " + (data.skillById[sk]?.name ?: sk) } ?: "") }
                ChoiceButton(Dialogues.format(c.text, s), detail, chance) { vm.act(silent = true) { dd, st -> Dialogues.choose(dd, st, c.id) } }
            }
            if (choices.isEmpty()) ActionButton("Gespräch beenden", Modifier.fillMaxWidth()) { vm.update { it.copy(activeDialogue = null, completedDialogues = it.completedDialogues + d.id) } }
        }
    }
}

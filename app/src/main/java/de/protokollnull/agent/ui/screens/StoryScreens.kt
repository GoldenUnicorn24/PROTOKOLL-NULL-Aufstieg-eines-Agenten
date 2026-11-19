package de.protokollnull.agent.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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

private val avatarSaver = Saver<AvatarSpec, String>(
    save = { Json.encodeToString(it) }, restore = { Json.decodeFromString<AvatarSpec>(it) }
)
private val dossierSaver = Saver<CustomNpc, String>(
    save = { Json.encodeToString(it) }, restore = { Json.decodeFromString<CustomNpc>(it) }
)

@Composable
fun CreatorScreen(vm: GameViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var first by rememberSaveable { mutableStateOf("Lena") }
    var last by rememberSaveable { mutableStateOf("Berg") }
    var code by rememberSaveable { mutableStateOf("Null") }
    var gender by rememberSaveable { mutableStateOf("female") }
    var avatar by rememberSaveable(stateSaver = avatarSaver) { mutableStateOf(AvatarSpec(skin = 1, hairStyle = 4, hairColor = 1, outfit = 0)) }
    var style by rememberSaveable { mutableStateOf("sachlich") }
    var background by rememberSaveable { mutableStateOf("analyst") }
    var strength by rememberSaveable { mutableStateOf(Attr.PERCEPTION) }
    var weakness by rememberSaveable { mutableStateOf(Attr.CHARISMA) }
    var difficulty by rememberSaveable { mutableStateOf("normal") }
    var includeCompanion by rememberSaveable { mutableStateOf(false) }
    var dossier by rememberSaveable(stateSaver = dossierSaver) { mutableStateOf(CompanionAssignment.dossier()) }
    val steps = listOf("Identität", "Aussehen", "Hintergrund", "Einsatzprofil", "Gefährtin oder Gefährte")

    var usedShortcut by rememberSaveable { mutableStateOf(false) }
    val previous = { step = if (usedShortcut && step == steps.lastIndex) 0 else (step - 1).coerceAtLeast(0) }
    val scroll = key(step) { rememberScrollState() }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Agent erstellen", "Schritt ${step + 1}/${steps.size} · ${steps[step]}", onBack = { if (step > 0) previous() else vm.goToStart() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step < steps.lastIndex) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 96.dp, height = 120.dp)) { AvatarCanvas(avatar, Modifier.fillMaxSize()) }
                Column {
                    Text(code.ifBlank { "—" }.uppercase(), style = MaterialTheme.typography.titleLarge, color = Pn.Accent)
                    Text("$first $last", style = MaterialTheme.typography.titleSmall)
                    Text("Kandidat 7 · Projekt PHOENIX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (step) {
                0 -> GlassPanel {
                    PortraitUpload(avatar) { avatar = it }
                    OutlinedTextField(first, { first = it.take(18) }, label = { Text("Vorname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(last, { last = it.take(18) }, label = { Text("Nachname (Tarnidentität)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(code, { code = it.take(14) }, label = { Text("Deckname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Geschlecht", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("female" to "Weiblich", "male" to "Männlich", "diverse" to "Divers"), gender) { gender = it }
                    Text("Auftreten", style = MaterialTheme.typography.titleSmall)
                    ChipRow(listOf("sachlich" to "Sachlich", "charmant" to "Charmant", "verschlossen" to "Verschlossen", "rebellisch" to "Rebellisch"), style) { style = it }
                }
                1 -> GlassPanel {
                    PortraitUpload(avatar) { avatar = it }
                    if (avatar.portraitId == null) AvatarControls(avatar) { avatar = it }
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
                3 -> GlassPanel {
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
                4 -> {
                    GlassPanel {
                        Text("Wen soll Michael dir später zuteilen?", style = MaterialTheme.typography.titleMedium)
                        SmallNote("An deinem 18. Geburtstag tritt die Person in die Division ein. Bis dahin bleibt die Personalakte versiegelt. Eine Romanze kannst du wählen, wenn ihr beide volljährig seid.")
                        ChipRow(listOf("no" to "Ohne Gefährten starten", "yes" to "Personalakte anlegen"), if (includeCompanion) "yes" else "no") {
                            includeCompanion = it == "yes"
                        }
                    }
                    if (includeCompanion) CompanionForm(dossier) { dossier = it }
                }
            }
        }
        if (step == 0) TextButton(onClick = { usedShortcut = true; step = steps.lastIndex }, enabled = first.isNotBlank() && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Einsatzprofil übernehmen und zur Gefährtenwahl")
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) ActionButton("Zurück", Modifier.weight(1f), secondary = true) { previous() }
            if (step < steps.lastIndex) ActionButton("Weiter", Modifier.weight(1f), enabled = first.isNotBlank() && code.isNotBlank()) { usedShortcut = false; step++ }
            else ActionButton("Prolog beginnen", Modifier.weight(1f), enabled = first.isNotBlank() && code.isNotBlank() && (!includeCompanion || dossier.firstName.isNotBlank())) {
                vm.startGame(PlayerCharacter(
                    firstName = first.trim(), lastName = last.trim(), codename = code.trim(), gender = gender,
                    appearance = Appearance(avatar, style), background = background, strength = strength,
                    weakness = weakness, difficulty = difficulty
                ), if (includeCompanion) dossier.copy(firstName = dossier.firstName.trim(), lastName = dossier.lastName.trim()) else null)
            }
        }
    }
}

@Composable
fun AvatarControls(avatar: AvatarSpec, onChange: (AvatarSpec) -> Unit) {
    OptionStepper("Hautton", AvatarPalette.skinNames, avatar.skin) { onChange(avatar.copy(skin = it)) }
    OptionStepper("Frisur", AvatarPalette.hairNames, avatar.hairStyle) { onChange(avatar.copy(hairStyle = it)) }
    OptionStepper("Haarfarbe", AvatarPalette.hairColorNames, avatar.hairColor) { onChange(avatar.copy(hairColor = it)) }
    OptionStepper("Augen", listOf("Braun", "Blau", "Grün"), avatar.eyes) { onChange(avatar.copy(eyes = it)) }
    OptionStepper("Kleidung", AvatarPalette.outfitNames, avatar.outfit) { onChange(avatar.copy(outfit = it)) }
    OptionStepper("Zubehör", AvatarPalette.accessoryNames, avatar.accessory) { onChange(avatar.copy(accessory = it)) }
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
    var confirmSkip by remember { mutableStateOf(false) }
    if (confirmSkip) AlertDialog(
        onDismissRequest = { confirmSkip = false },
        title = { Text("Zur Division wechseln?") },
        text = { Text("Du beginnst mit 16 bei Michael. Bereits getroffene Entscheidungen bleiben in deiner Akte.") },
        confirmButton = { TextButton(onClick = { confirmSkip = false; vm.act { d, st -> Prologue.skip(d, st) } }) { Text("Überspringen") } },
        dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text("Weiterlesen") } }
    )
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader("Projekt PHOENIX", "Prolog", trailing = {
            TextButton(onClick = { confirmSkip = true }) { Text("Überspringen") }
        })
        val anim = animOn()
        AnimatedContent(targetState = s.prologueNode, transitionSpec = { if (anim) fadeIn() togetherWith fadeOut() else fadeIn(snap()) togetherWith fadeOut(snap()) }, label = "prolog") { id ->
            val node = vm.data.storyById[id]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (node == null) {
                    ActionButton("Weiter") { vm.act { d, st -> Prologue.finish(d, st) } }
                    return@Column
                }
                ScenePanel(node.image ?: "archives", Modifier.fillMaxWidth().aspectRatio(2.1f),
                    node.age?.let { "Erinnerung · $it Jahre" } ?: "Erinnerung", node.chapter)
                if (node.speakerId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NpcPortrait(vm.data, s, node.speakerId, 64.dp)
                        Column {
                            Text(vm.data.agentById[node.speakerId]?.name ?: node.speakerId, style = MaterialTheme.typography.titleMedium, color = Pn.Accent)
                            Text(vm.data.agentById[node.speakerId]?.role ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                GlassPanel {
                    Text(Dialogues.format(node.text, s), style = MaterialTheme.typography.bodyLarge)
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
    val speaker = (n.speakerId ?: d.npcId)?.takeIf { it != "narrator" }
    val choices = Dialogues.visibleChoices(data, s)
    Column(Modifier.fillMaxSize()) {
        // Kinoartiger Kopfbereich mit großem Porträt
        Box(Modifier.fillMaxWidth().height(if (speaker != null) 300.dp else 120.dp)) {
            if (speaker != null) {
                NpcPortrait(data, s, speaker, 400.dp, Modifier.fillMaxSize(), height = 300.dp, framed = false)
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x66060C17), Color.Transparent, Color(0xF2060C17)))))
            Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text(d.chapter.ifBlank { "Gespräch" }.uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                Text(d.title.uppercase(), style = MaterialTheme.typography.titleLarge)
            }
            if (speaker != null) Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(Relations.displayName(data, s, speaker).uppercase(), style = MaterialTheme.typography.headlineSmall, color = Pn.Text)
                Text(data.agentById[speaker]?.role ?: "", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                if (Relations.isKnownNpc(data, s, speaker)) {
                    val r = Relations.get(data, s, speaker)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Vertrauen ${r.trust}", icon = PnIcons.Shield)
                        Pill("Freundschaft ${r.friendship}", Pn.Ok, icon = PnIcons.Heart)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.activeDialogue?.history?.takeLast(2)?.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic) }
            s.activeDialogue?.lastResult?.takeIf { it.isNotBlank() }?.let { Pill(it, if (it.contains("gelungen")) Pn.Ok else Pn.Danger, icon = PnIcons.Spark) }
            GlassPanel {
                TypewriterText(Dialogues.format(n.text, s), MaterialTheme.typography.bodyLarge.copy(fontStyle = if (speaker == null) FontStyle.Italic else FontStyle.Normal))
            }
            choices.forEach { c ->
                val chance = Dialogues.choiceChance(data, s, c)
                val detail = c.check?.let { Attr.label(it.attribute) + (it.skill?.let { sk -> " · " + (data.skillById[sk]?.name ?: sk) } ?: "") }
                ChoiceButton(Dialogues.format(c.text, s), detail, chance, icon = if (c.check != null) PnIcons.Spark else PnIcons.Chat) { vm.act(silent = true) { dd, st -> Dialogues.choose(dd, st, c.id) } }
            }
            if (choices.isEmpty()) ActionButton("Gespräch beenden", Modifier.fillMaxWidth()) { vm.update { it.copy(activeDialogue = null, completedDialogues = it.completedDialogues + d.id) } }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Epilog (nach der letzten Entscheidung)
// ---------------------------------------------------------------------------

@Composable
fun EpilogueScreen(vm: GameViewModel, s: GameState) {
    val sections = Endings.epilogue(vm.data, s)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            RadarEmblem(Modifier.size(170.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EPILOG", style = MaterialTheme.typography.labelLarge, color = Pn.Accent)
                Text(Endings.title(Endings.endingId(s)).removePrefix("Ende: ").uppercase(), style = MaterialTheme.typography.displaySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        sections.forEachIndexed { i, sec ->
            GlassPanel(accent = if (i == 0) Pn.Warn else Pn.Accent) {
                Text(sec.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (i == 0) Pn.Warn else Pn.Accent)
                Text(sec.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        ActionButton("Weiterspielen (freies Spiel)", Modifier.fillMaxWidth(), icon = PnIcons.Play) { vm.update { it.copy(flags = it.flags + "epilogue_seen") } }
        SmallNote("Nach dem Epilog bleibt die Division spielbar: Einsatzboard, Direktorat, Beziehungen und eigene Rekruten.")
        Spacer(Modifier.height(24.dp))
    }
}

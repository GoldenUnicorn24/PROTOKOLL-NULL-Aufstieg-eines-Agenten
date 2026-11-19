package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.Clock
import de.protokollnull.agent.engine.CompanionAssignment
import de.protokollnull.agent.engine.Social
import de.protokollnull.agent.model.CustomNpc
import de.protokollnull.agent.model.GameState
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

@Composable
fun CompanionForm(npc: CustomNpc, onChange: (CustomNpc) -> Unit) {
    GlassPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp, 108.dp)) { AvatarCanvas(npc.avatar, Modifier.fillMaxSize()) }
            Column {
                Text(npc.name, style = MaterialTheme.typography.titleMedium)
                Text(Social.professionLabel(npc.profession), style = MaterialTheme.typography.bodySmall)
            }
        }
        PortraitUpload(npc.avatar) { onChange(npc.copy(avatar = it)) }
        OutlinedTextField(npc.firstName, { onChange(npc.copy(firstName = it.take(18))) },
            label = { Text("Vorname") }, singleLine = true)
        OutlinedTextField(npc.lastName, { onChange(npc.copy(lastName = it.take(18))) },
            label = { Text("Nachname") }, singleLine = true)
        ChipRow(listOf("female" to "Weiblich", "male" to "Männlich", "diverse" to "Divers"), npc.gender) { onChange(npc.copy(gender = it)) }
        if (npc.avatar.portraitId == null) AvatarControls(npc.avatar) { onChange(npc.copy(avatar = it)) }
    }
    GlassPanel {
        OutlinedTextField(npc.background, { onChange(npc.copy(background = it.take(180))) },
            label = { Text("Vorgeschichte") }, modifier = Modifier.fillMaxWidth())
        Text("Charakterzüge · bis zu 3", style = MaterialTheme.typography.titleSmall)
        MultiChips(CompanionOptions.traits, npc.traits, 3) { onChange(npc.copy(traits = it)) }
        SmallNote("Prägen Entscheidungen: ehrgeizig trainiert mehr und tritt früher zu Prüfungen an, ruhig meidet Einsätze, humorvoll knüpft schneller Freundschaften.")
        Text("Interessen · bis zu 3", style = MaterialTheme.typography.titleSmall)
        MultiChips(CompanionOptions.interests, npc.interests, 3) { onChange(npc.copy(interests = it)) }
        SmallNote("Bestimmen Freundschaften in der Division und welche Geschenke gut ankommen.")
        Text("Werte · bis zu 2", style = MaterialTheme.typography.titleSmall)
        MultiChips(CompanionOptions.values, npc.values, 2) { onChange(npc.copy(values = it)) }
        Text("Lebensziel", style = MaterialTheme.typography.titleSmall)
        ChipRow(CompanionOptions.goals.map { it to it }, npc.lifeGoal) { onChange(npc.copy(lifeGoal = if (npc.lifeGoal == it) "" else it)) }
        Text("Fachgebiet (Ausbildung)", style = MaterialTheme.typography.titleSmall)
        ChipRow(listOf("field" to "Außendienst", "intel" to "Analyse", "tech" to "Technik", "medic" to "Sanitätsdienst"), npc.profession) { onChange(npc.copy(profession = it)) }
        Text("Berufswunsch", style = MaterialTheme.typography.titleSmall)
        ChipRow(CompanionOptions.careerWishes, npc.careerWish.ifBlank { npc.profession }) { onChange(npc.copy(careerWish = it)) }
        SmallNote("Die Laufbahn verläuft eigenständig: Training, Monatsnoten, Prüfungen und Beförderungen – unabhängig davon, wie viel Zeit vergeht.")
        Text("Fähigkeiten · ${5 - npc.skills.values.sum()} Punkte frei", style = MaterialTheme.typography.titleSmall)
        for ((skill, label) in listOf("observe" to "Beobachtung", "analysis" to "Datenanalyse", "first_aid" to "Erste Hilfe", "empathy" to "Empathie", "stamina" to "Ausdauer")) {
            val value = npc.skills[skill] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f))
                TextButton(enabled = value > 0, onClick = { onChange(npc.copy(skills = npc.skills + (skill to value - 1))) }) { Text("−") }
                Text("$value")
                TextButton(enabled = value < 3 && npc.skills.values.sum() < 5, onClick = { onChange(npc.copy(skills = npc.skills + (skill to value + 1))) }) { Text("+") }
            }
        }
    }
}

object CompanionOptions {
    val traits = listOf("loyal", "neugierig", "ruhig", "humorvoll", "ehrgeizig", "kreativ", "vorsichtig", "warmherzig", "diszipliniert", "kompetitiv")
    val interests = listOf("Musik", "Klettern", "Bücher", "Schach", "Fotografie", "Sprachen", "Laufen", "Kunst", "Technik", "Kochen", "Spiele", "Schreiben")
    val values = listOf("Loyalität", "Gerechtigkeit", "Freiheit", "Sicherheit", "Wahrheit", "Familie", "Disziplin", "Ehrgeiz")
    val goals = listOf("Eine eigene Einheit führen", "Die Wahrheit über die Division", "Ein Leben außerhalb der Mauern", "Die Beste des Jahrgangs werden", "Menschen schützen, die sich nicht wehren können")
    val careerWishes = listOf("field" to "Außendienst", "intel" to "Analyse", "tech" to "Technik", "medic" to "Sanität", "lead" to "Führung")
}

@Composable
fun CompanionIntroduction(vm: GameViewModel, state: GameState, npc: CustomNpc) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Personalstelle · " + Clock.dateLabel(npc.createdDay))
        if (state.clock.day > npc.createdDay) SmallNote("Erinnerung an deinen 18. Geburtstag")
        Text("Die zweite Unterschrift", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NpcPortrait(vm.data, state, "michael", 76.dp)
            Text("Michael", style = MaterialTheme.typography.titleMedium)
        }
        GlassPanel {
            Text("Michael legt zwei Stifte auf den Tisch. „Als du hier ankamst, habe ich für dich unterschrieben. Heute sitzt du auf der anderen Seite.“ Er schiebt dir den Dienstplan zu. Unter deinem Namen steht ${npc.name}.")
            Text("„Ich teile euch für den Dienst ein. ${npc.firstName} kennt die Verfahren aus dem Unterricht. Du weißt, wie sich der erste Tag wirklich anfühlt.“")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NpcPortrait(vm.data, state, npc.id, 76.dp)
            Column { Text(npc.name, style = MaterialTheme.typography.titleMedium); SmallNote(Social.professionLabel(npc.profession)) }
        }
        GlassPanel { Text("„Die Akte über dich hatte drei geschwärzte Seiten.“ ${npc.firstName} legt den Stift hin. „Was würdest du jemandem erzählen, der hier heute zum ersten Mal durch die Tür kommt?“") }
        ChoiceButton("„Komm. Ich zeige dir meinen Platz am Fenster.“", "Persönlicher Einstieg · Freundschaft +4") {
            vm.act { data, s -> CompanionAssignment.introduce(data, s, offerTour = true) }
        }
        ChoiceButton("„Schreib auf, was du selbst gesehen hast. Auch bei mir.“", "Dienstlicher Einstieg · Respekt +4") {
            vm.act { data, s -> CompanionAssignment.introduce(data, s, offerTour = false) }
        }
    }
}

package de.protokollnull.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.data.GameContent
import de.protokollnull.agent.engine.GameRules
import de.protokollnull.agent.model.GameSave
import de.protokollnull.agent.model.PartnerProfile

@Composable
internal fun ProfileScreen(content: GameContent, save: GameSave, onSave: (GameSave) -> Unit) {
    var partnerName by remember { mutableStateOf("Alina") }
    var specialization by remember { mutableStateOf("field") }
    val player = save.player
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("AGENTENPROFIL", style = MaterialTheme.typography.titleLarge)
            HeroPanel {
                Text("Codename: " + player.name, fontWeight = FontWeight.Bold)
                Text("Alter: " + player.age + " • Level: " + player.level)
                Text("Rang: " + player.rank + "/12 • Ruf: " + player.reputation)
                Text("Freie Fähigkeitspunkte: " + player.skillPoints)
                Text("Der Spielstand wird nach jeder Aktion lokal gesichert.",
                    style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
            }
        }
        item {
            Text("FÄHIGKEITEN", color = Accent, style = MaterialTheme.typography.labelLarge)
            HeroPanel {
                content.skills.take(9).forEach { skill ->
                    Text(skill.name + ": " + (player.skills[skill.id] ?: 0) + "/" + skill.maxRank)
                }
                Text("Alle 27 Fähigkeiten sind in assets/game/skills.json hinterlegt.",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Text("NEUER AGENT · PARTNEREDITOR", color = Accent, style = MaterialTheme.typography.labelLarge)
            HeroPanel {
                if (!GameRules.canCreatePartner(player)) {
                    Text("Wird mit deinem 18. Geburtstag freigeschaltet. Neue Figur startet mit 16.")
                } else if (save.partner != null) {
                    Text("Erstellte Figur: " + save.partner.name, fontWeight = FontWeight.Bold)
                    Text("Startalter: " + save.partner.ageAtCreation)
                    Text("Spezialisierung: " + save.partner.specialization)
                    Text("Freundschaft und altersgerechte Romanze werden später storybasiert erweitert.")
                } else {
                    Text("Erstelle eine neue 16-jährige Agentin oder einen neuen Agenten.")
                    OutlinedTextField(
                        value = partnerName, onValueChange = { partnerName = it },
                        label = { Text("Vorname") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = specialization == "field",
                            onClick = { specialization = "field" },
                            label = { Text("Außendienst") }
                        )
                        FilterChip(
                            selected = specialization == "intel",
                            onClick = { specialization = "intel" },
                            label = { Text("Analyse") }
                        )
                    }
                    Button(
                        enabled = partnerName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val oppositeGender = if (player.gender == "male") "female" else "male"
                            onSave(save.copy(partner = PartnerProfile(
                                id = "custom_partner_001",
                                name = partnerName.trim(),
                                gender = oppositeGender,
                                personality = "loyal",
                                specialization = specialization
                            )))
                        }
                    ) { Text("Rekrut erstellen") }
                }
            }
        }
    }
}

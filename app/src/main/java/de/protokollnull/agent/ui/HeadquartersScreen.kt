package de.protokollnull.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.data.GameContent
import de.protokollnull.agent.engine.GameRules
import de.protokollnull.agent.model.GameSave

@Composable
internal fun HeadquartersScreen(content: GameContent, save: GameSave, onSave: (GameSave) -> Unit) {
    val p = save.player
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            HeroPanel {
                Text("AGENT " + p.name.uppercase(), color = Accent, style = MaterialTheme.typography.labelLarge)
                Text("Level " + p.level + " • " + (content.ranks.find { it.rank == p.rank }?.name ?: "Rekrut"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Alter: " + p.age + " • Diensttag: " + p.elapsedDays + " • Credits: " + p.credits)
                LinearProgressIndicator(
                    progress = { (p.xp.toFloat() / GameRules.requiredXp(p.level)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = Accent
                )
                Text(p.xp.toString() + " / " + GameRules.requiredXp(p.level) + " XP", style = MaterialTheme.typography.labelSmall)
            }
        }
        item { Text("HAUPTGESCHICHTE", color = Accent, style = MaterialTheme.typography.labelLarge) }
        item {
            val node = content.story.firstOrNull { it.id == save.currentStoryNode }
            HeroPanel {
                Text(node?.chapter ?: "Ankunft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                node?.speakerId?.let { id ->
                    Text(content.agents.firstOrNull { it.id == id }?.name ?: id, color = Accent)
                }
                Text(node?.text ?: "Hier beginnt deine Geschichte.")
                node?.choices?.forEach { choice ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val trusted = if (node.speakerId != null)
                                GameRules.changeTrust(p, node.speakerId, choice.trustDelta) else p
                            onSave(save.copy(
                                currentStoryNode = choice.nextNode ?: save.currentStoryNode,
                                player = GameRules.grantXp(trusted, choice.xp).copy(
                                    stress = (trusted.stress + choice.stressDelta).coerceIn(0, 100)
                                )
                            ))
                        }
                    ) { Text(choice.text) }
                }
            }
        }
        item { Text("TAGESPLAN", color = Accent, style = MaterialTheme.typography.labelLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(save.copy(player = GameRules.advanceDay(p))) },
                    modifier = Modifier.weight(1f)) { Text("Nächster Tag") }
                OutlinedButton(
                    onClick = { onSave(save.copy(player = GameRules.train(p, "defense"))) },
                    enabled = p.energy >= 10 && p.skillPoints > 0, modifier = Modifier.weight(1f)
                ) { Text("Training") }
            }
        }
        item {
            Text("Energie " + p.energy + "/100 • Stress " + p.stress + "/100 • Skillpunkte " + p.skillPoints,
                color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Text("Foundation-Demo: Auswahl, Training und lokaler Speicherstand funktionieren. Taktische Missionen und komplexe NPC-Routinen folgen.",
                style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
        }
    }
}

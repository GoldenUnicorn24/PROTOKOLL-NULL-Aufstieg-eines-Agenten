package de.protokollnull.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
internal fun MissionsScreen(content: GameContent, save: GameSave, onSave: (GameSave) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("EINSATZZENTRALE", style = MaterialTheme.typography.titleLarge)
            Text("Datenbank mit Zugangsbedingungen. Missionstaktik folgt in der Vollversion.",
                color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
        items(content.missions, key = { it.id }) { m ->
            val unlocked = GameRules.canEnterMission(save.player, m)
            val done = m.id in save.completedMissions
            HeroPanel {
                Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(m.type + " • Gefahr " + m.threat + "/5 • " + m.xp + " XP",
                    color = Accent, style = MaterialTheme.typography.labelSmall)
                Text(m.description, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    enabled = unlocked && !done,
                    onClick = {
                        val p = GameRules.grantXp(save.player, m.xp)
                        onSave(save.copy(
                            player = p.copy(reputation = p.reputation + m.threat * 5),
                            completedMissions = save.completedMissions + m.id
                        ))
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(if (done) "Abgeschlossen" else if (unlocked) "Demo-Abschluss" else "Gesperrt") }
            }
        }
    }
}

package de.protokollnull.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import de.protokollnull.agent.data.GameContent
import de.protokollnull.agent.engine.GameRules
import de.protokollnull.agent.model.GameSave

@Composable
internal fun ContactsScreen(
    content: GameContent, save: GameSave, onSave: (GameSave) -> Unit, loader: ImageLoader
) {
    val context = LocalContext.current
    val castFiles = remember(context) {
        runCatching { context.assets.list("cast")?.toSet() ?: emptySet() }.getOrDefault(emptySet())
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("PERSONALAKTEN", style = MaterialTheme.typography.titleLarge) }
        items(content.agents, key = { it.id }) { agent ->
            val portrait = if ((agent.id + ".jpg") in castFiles) agent.id + ".jpg" else "placeholder.svg"
            HeroPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AsyncImage(
                        model = "file:///android_asset/cast/" + portrait,
                        imageLoader = loader,
                        contentDescription = "Porträt: " + agent.name,
                        modifier = Modifier.size(width = 82.dp, height = 104.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(agent.name, fontWeight = FontWeight.Bold)
                        Text(agent.role, color = Accent, style = MaterialTheme.typography.labelSmall)
                        Text("Vertrauen: " + (save.player.relationships[agent.id] ?: agent.initialTrust) + "/100")
                        Text(agent.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
                OutlinedButton(
                    onClick = { onSave(save.copy(player = GameRules.changeTrust(save.player, agent.id, 1))) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kurzes Gespräch (Demo)") }
            }
        }
    }
}

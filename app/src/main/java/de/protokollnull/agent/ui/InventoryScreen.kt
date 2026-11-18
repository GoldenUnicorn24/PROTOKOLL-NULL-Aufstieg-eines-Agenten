package de.protokollnull.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import de.protokollnull.agent.data.GameContent
import de.protokollnull.agent.model.GameSave

@Composable
internal fun InventoryScreen(content: GameContent, save: GameSave, loader: ImageLoader) {
    val definitions = content.items.associateBy { it.id }
    val weight = save.player.inventory.sumOf { (definitions[it.itemId]?.weightGrams ?: 0) * it.quantity }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("INVENTAR", style = MaterialTheme.typography.titleLarge)
        Text(save.player.inventory.sumOf { it.quantity }.toString() + " Gegenstände • " + weight + " g",
            color = Color.LightGray)
        LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(save.player.inventory) { stack ->
                val item = definitions[stack.itemId]
                HeroPanel {
                    AsyncImage(
                        model = "file:///android_asset/items/" + (item?.icon ?: "notebook") + ".svg",
                        imageLoader = loader, contentDescription = item?.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(76.dp).align(Alignment.CenterHorizontally)
                    )
                    Text(item?.name ?: stack.itemId, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text("Menge " + stack.quantity + " • " + (item?.rarity ?: "common"),
                        style = MaterialTheme.typography.labelSmall, color = Accent)
                }
            }
        }
    }
}

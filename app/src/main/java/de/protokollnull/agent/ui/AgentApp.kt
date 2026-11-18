package de.protokollnull.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.SvgDecoder
import de.protokollnull.agent.data.GameContent
import de.protokollnull.agent.data.SaveStore
import de.protokollnull.agent.model.GameSave

internal val Accent = Color(0xFF45D2C0)
internal val Dark = Color(0xFF091320)
internal val Panel = Color(0xFF152538)

@Composable
fun AgentApp() {
    val context = LocalContext.current
    val content = remember(context) { GameContent(context) }
    val saves = remember(context) { SaveStore(context) }
    val loader = remember(context) {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    var save by remember { mutableStateOf(saves.load()) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("HQ", "Missionen", "Inventar", "Kontakte", "Profil")
    val update: (GameSave) -> Unit = { next -> save = next; saves.save(next) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Dark, surface = Panel)) {
        Scaffold(
            containerColor = Dark,
            topBar = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("DIVISION NETWORK", color = Accent, style = MaterialTheme.typography.labelSmall)
                    Text("PROTOKOLL NULL", style = MaterialTheme.typography.titleLarge)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌂", "◎", "▦", "♧", "◈")[index]) },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HeadquartersScreen(content, save, update)
                    1 -> MissionsScreen(content, save, update)
                    2 -> InventoryScreen(content, save, loader)
                    3 -> ContactsScreen(content, save, update, loader)
                    else -> ProfileScreen(content, save, update)
                }
            }
        }
    }
}

package de.protokollnull.agent.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.data.CastPortraitBundle
import de.protokollnull.agent.data.ImportedPortraits
import de.protokollnull.agent.data.PrivateCastPortraits
import de.protokollnull.agent.ui.theme.ActionButton
import de.protokollnull.agent.ui.theme.GlassPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One central offline settings panel for all original-series cast images. */
@Composable
fun CastPortraitSettings(characters: List<Pair<String, String>>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rev = PrivateCastPortraits.revision
    val active = remember(context, rev, characters) {
        characters.count { (id, _) -> PrivateCastPortraits.imported(context, id) != null }
    }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val ids = remember(characters) { characters.map { it.first }.toSet() }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            notice = null
            error = null
            try {
                val result = CastPortraitBundle.importZip(context, uri, ids)
                notice = result.imported.size.toString() + " Serienporträts importiert: " +
                    result.imported.mapNotNull { key -> characters.firstOrNull { it.first == key }?.second }.joinToString()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Paket konnte nicht importiert werden."
            } finally {
                busy = false
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = selected
        selected = null
        if (target != null && uri != null) scope.launch {
            busy = true
            notice = null
            error = null
            try {
                val imageId = ImportedPortraits.importPortrait(context, uri)
                PrivateCastPortraits.choose(context, target, imageId)
                notice = "Das Porträt wurde lokal ersetzt."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Bild konnte nicht importiert werden."
            } finally {
                busy = false
            }
        }
    }

    GlassPanel {
        Text("Serienbilder – privat & offline", style = MaterialTheme.typography.titleMedium)
        Text(active.toString() + " von " + characters.size + " Figuren mit persönlichen Bildern",
            style = MaterialTheme.typography.bodySmall)
        Text("Ein ZIP mit show_alex.jpg, show_nikita.jpg usw. wird automatisch allen Figuren zugeordnet. Fehlende Bilder behalten ihr bisheriges Foto.",
            style = MaterialTheme.typography.bodySmall)
        ActionButton("Komplettes Bildpaket (.zip) importieren", Modifier.fillMaxWidth(), enabled = !busy) {
            zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        TextButton(onClick = { expanded = !expanded }, enabled = !busy) {
            Text(if (expanded) "Einzelbilder ausblenden" else "Einzelne Bilder verwalten")
        }
        if (expanded) {
            characters.forEach { (id, name) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val imported = PrivateCastPortraits.imported(context, id) != null
                    Text(name + if (imported) " ✓" else " · Standard", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        selected = id
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, enabled = !busy) { Text("Ändern") }
                    if (imported) TextButton(
                        onClick = { PrivateCastPortraits.clear(context, id) }, enabled = !busy
                    ) { Text("Reset") }
                }
            }
        }
        if (active > 0) TextButton(onClick = { confirmReset = true }, enabled = !busy) {
            Text("Alle Serienbilder zurücksetzen")
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("Die Bilder bleiben lokal auf diesem Gerät. Keine Weitergabe, kein automatischer Download.",
            style = MaterialTheme.typography.labelSmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Alle privaten Serienbilder entfernen?") },
        text = { Text("Die Bildzuordnungen werden zurückgesetzt; die mitgelieferten Darstellerfotos erscheinen wieder. Deine Spielstände bleiben erhalten.") },
        confirmButton = { TextButton(onClick = {
            PrivateCastPortraits.clearAll(context, ids)
            notice = "Alle privaten Bildzuordnungen wurden zurückgesetzt."
            confirmReset = false
        }) { Text("Zurücksetzen") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } }
    )
}

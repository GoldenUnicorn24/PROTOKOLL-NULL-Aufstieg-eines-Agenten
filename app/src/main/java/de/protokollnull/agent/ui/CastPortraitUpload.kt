package de.protokollnull.agent.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import de.protokollnull.agent.data.ImportedPortraits
import de.protokollnull.agent.data.PrivateCastPortraits
import de.protokollnull.agent.ui.theme.ActionButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Local-only replacement image picker for an individual Nikita television character. */
@Composable
fun CastPortraitUpload(npcId: String, characterName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision = PrivateCastPortraits.revision
    val imported = remember(context, npcId, revision) { PrivateCastPortraits.imported(context, npcId) != null }
    val bundled = remember(context, npcId) { PrivateCastPortraits.bundled(context, npcId) != null }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try {
                val imageId = ImportedPortraits.importPortrait(context, uri)
                PrivateCastPortraits.choose(context, npcId, imageId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Dieses Bild kann nicht verwendet werden."
            } catch (_: Exception) {
                error = "Das Foto konnte nicht übernommen werden."
            } finally {
                busy = false
            }
        }
    }
    if (busy) AlertDialog(
        onDismissRequest = {},
        title = { Text("Privates Porträt wird importiert …") },
        text = { LinearProgressIndicator() },
        confirmButton = {}
    )
    Column {
        ActionButton(
            if (imported) "Privates Bild von " + characterName + " wechseln"
            else "Eigenes Bild von " + characterName + " auswählen",
            secondary = true, enabled = !busy
        ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        when {
            imported -> {
                Text("Privates Porträt nur auf diesem Gerät aktiv.",
                    style = MaterialTheme.typography.labelSmall)
                TextButton(
                    onClick = { PrivateCastPortraits.clear(context, npcId) },
                    enabled = !busy
                ) { Text(if (bundled) "Lokales Bild zurücksetzen" else "Originalfoto wiederherstellen") }
            }
            bundled -> Text("Privates Porträt aus lokalem Android-Build aktiv.",
                style = MaterialTheme.typography.labelSmall)
            else -> Text("Aktuell wird das lizenzierte Darstellerfoto verwendet.",
                style = MaterialTheme.typography.labelSmall)
        }
        Text("Nur rechtmäßig nutzbare Bilder auswählen. Kein GitHub-Upload.",
            style = MaterialTheme.typography.labelSmall)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error) }
    }
}

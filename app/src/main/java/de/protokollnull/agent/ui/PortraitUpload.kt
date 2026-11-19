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
import de.protokollnull.agent.model.AvatarSpec
import de.protokollnull.agent.ui.theme.ActionButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PortraitUpload(avatar: AvatarSpec, onChange: (AvatarSpec) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentAvatar by rememberUpdatedState(avatar)
    val currentChange by rememberUpdatedState(onChange)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try {
                val id = ImportedPortraits.importPortrait(context, uri)
                currentChange(currentAvatar.copy(portraitId = id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Dieses Bild kann nicht verwendet werden."
            } catch (_: Exception) {
                error = "Das Bild konnte nicht übernommen werden. Wähle bitte ein anderes Foto."
            } finally {
                busy = false
            }
        }
    }
    if (busy) AlertDialog(
        onDismissRequest = {},
        title = { Text("Porträt wird übernommen …") },
        text = { LinearProgressIndicator() },
        confirmButton = {}
    )
    Column {
        val label = when {
            busy -> "Porträt wird übernommen …"
            avatar.portraitId == null -> "Foto vom Gerät wählen"
            else -> "Porträt wechseln"
        }
        ActionButton(label, secondary = true, enabled = !busy) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (avatar.portraitId != null) TextButton(
            onClick = { onChange(avatar.copy(portraitId = null)) },
            enabled = !busy
        ) { Text("Gezeichneten Avatar verwenden") }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

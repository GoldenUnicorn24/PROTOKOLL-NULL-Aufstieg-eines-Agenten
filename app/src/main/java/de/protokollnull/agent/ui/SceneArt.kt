package de.protokollnull.agent.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.R
import de.protokollnull.agent.ui.theme.Pn

private fun artwork(place: String) = when (place) {
    "assignment" -> R.drawable.scene_assignment
    "platform" -> R.drawable.scene_platform
    "hospital", "medical" -> R.drawable.scene_hospital
    "campus" -> R.drawable.scene_campus
    "archives", "tech" -> R.drawable.scene_archive
    "director" -> R.drawable.scene_hearing
    "harbor" -> R.drawable.scene_harbor
    "city", "embassy", "safehouse" -> R.drawable.scene_city
    else -> R.drawable.scene_division
}

@Composable
fun ScenePanel(place: String, modifier: Modifier, label: String, title: String, cover: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        Image(painterResource(artwork(place)), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEA071019)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = Pn.Accent)
            Text(title, style = if (cover) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

package de.protokollnull.agent.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.protokollnull.agent.data.ImportedPortraits
import de.protokollnull.agent.data.PrivateCastPortraits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.AvatarSpec
import de.protokollnull.agent.model.GameState
import de.protokollnull.agent.ui.theme.Pn

val LocalLoader = staticCompositionLocalOf<ImageLoader?> { null }

object AvatarPalette {
    val skins = listOf(Color(0xFFF3D3BE), Color(0xFFE2B596), Color(0xFFC68E68), Color(0xFF9A6644), Color(0xFF6B4430))
    val hairs = listOf(Color(0xFF1D1A19), Color(0xFF4A3426), Color(0xFF7A4B2C), Color(0xFFC9A16A), Color(0xFF9EA3A8), Color(0xFF8E2F2A))
    val outfits = listOf(Color(0xFF2B3442), Color(0xFF3A4A5E), Color(0xFF1C2430), Color(0xFF4C4F55), Color(0xFF243B3A), Color(0xFF5A2E35))
    val eyes = listOf(Color(0xFF3B2A20), Color(0xFF2F5A7A), Color(0xFF3F6B45))
    val hairNames = listOf("Kurz", "Undercut", "Locken, gebunden", "Lang, offen", "Pferdeschwanz", "Kurz geschoren", "Bob")
    val skinNames = listOf("Hell", "Hell-mittel", "Mittel", "Dunkel-mittel", "Dunkel")
    val hairColorNames = listOf("Schwarz", "Dunkelbraun", "Kastanie", "Blond", "Grau", "Rot")
    val outfitNames = listOf("Graphit", "Stahlblau", "Schwarz", "Grau", "Petrol", "Bordeaux")
    val accessoryNames = listOf("Keins", "Bleistift/Ohrstecker", "Brille", "Headset")
}

/** Original, prozedural gezeichnetes Porträt im Akten-Stil (für fiktive Figuren und den Spieler). */
@Composable
fun AvatarCanvas(spec: AvatarSpec, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo = remember(context, spec.portraitId) { ImportedPortraits.resolve(context, spec.portraitId) }
    if (photo != null) {
        AsyncImage(
            model = photo,
            contentDescription = "Eigenes Porträt",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color(0xFF1A2B40), Color(0xFF0B1422))))) {
        val w = size.width; val h = size.height
        val skin = AvatarPalette.skins[spec.skin.mod(AvatarPalette.skins.size)]
        val hair = AvatarPalette.hairs[spec.hairColor.mod(AvatarPalette.hairs.size)]
        val outfit = AvatarPalette.outfits[spec.outfit.mod(AvatarPalette.outfits.size)]
        val eye = AvatarPalette.eyes[spec.eyes.mod(AvatarPalette.eyes.size)]
        val cx = w / 2
        // Rasterlinien
        for (i in 1..6) drawLine(Color(0x1446E0CF), Offset(0f, h * i / 7f), Offset(w, h * i / 7f), 1f)
        val style = spec.hairStyle.mod(7)
        // Haare hinten (lang)
        if (style == 3 || style == 2) {
            drawRoundRect(hair, topLeft = Offset(cx - w * 0.27f, h * 0.2f), size = Size(w * 0.54f, h * (if (style == 3) 0.55f else 0.4f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.2f))
        }
        // Schultern
        val shoulders = Path().apply {
            moveTo(cx - w * 0.46f, h); cubicTo(cx - w * 0.44f, h * 0.74f, cx - w * 0.2f, h * 0.68f, cx, h * 0.68f)
            cubicTo(cx + w * 0.2f, h * 0.68f, cx + w * 0.44f, h * 0.74f, cx + w * 0.46f, h); close()
        }
        drawPath(shoulders, outfit)
        // Kragen
        val collar = Path().apply { moveTo(cx - w * 0.1f, h * 0.69f); lineTo(cx, h * 0.8f); lineTo(cx + w * 0.1f, h * 0.69f); close() }
        drawPath(collar, Color(0xFF0E151F))
        // Hals
        drawRect(skin.copy(alpha = 0.95f), topLeft = Offset(cx - w * 0.07f, h * 0.55f), size = Size(w * 0.14f, h * 0.15f))
        // Kopf
        drawOval(skin, topLeft = Offset(cx - w * 0.19f, h * 0.22f), size = Size(w * 0.38f, h * 0.38f))
        // Augen
        drawCircle(eye, radius = w * 0.022f, center = Offset(cx - w * 0.075f, h * 0.41f))
        drawCircle(eye, radius = w * 0.022f, center = Offset(cx + w * 0.075f, h * 0.41f))
        drawLine(hair.copy(alpha = 0.8f), Offset(cx - w * 0.11f, h * 0.37f), Offset(cx - w * 0.04f, h * 0.365f), w * 0.012f)
        drawLine(hair.copy(alpha = 0.8f), Offset(cx + w * 0.04f, h * 0.365f), Offset(cx + w * 0.11f, h * 0.37f), w * 0.012f)
        // Mund
        drawLine(Color(0xFF8A4E48), Offset(cx - w * 0.04f, h * 0.5f), Offset(cx + w * 0.04f, h * 0.5f), w * 0.01f)
        // Haare vorne
        when (style) {
            0 -> drawArc(hair, 180f, 180f, true, topLeft = Offset(cx - w * 0.2f, h * 0.19f), size = Size(w * 0.4f, h * 0.22f))
            1 -> { drawArc(hair, 180f, 180f, true, topLeft = Offset(cx - w * 0.2f, h * 0.17f), size = Size(w * 0.4f, h * 0.2f)); drawRect(hair, Offset(cx - w * 0.05f, h * 0.18f), Size(w * 0.22f, h * 0.06f)) }
            2 -> { drawArc(hair, 170f, 200f, true, topLeft = Offset(cx - w * 0.22f, h * 0.17f), size = Size(w * 0.44f, h * 0.26f)); drawCircle(hair, w * 0.09f, Offset(cx, h * 0.17f)) }
            3 -> drawArc(hair, 160f, 220f, true, topLeft = Offset(cx - w * 0.22f, h * 0.18f), size = Size(w * 0.44f, h * 0.27f))
            4 -> { drawArc(hair, 180f, 180f, true, topLeft = Offset(cx - w * 0.2f, h * 0.19f), size = Size(w * 0.4f, h * 0.21f)); drawOval(hair, Offset(cx + w * 0.14f, h * 0.28f), Size(w * 0.1f, h * 0.26f)) }
            5 -> drawArc(hair.copy(alpha = 0.7f), 180f, 180f, true, topLeft = Offset(cx - w * 0.19f, h * 0.215f), size = Size(w * 0.38f, h * 0.14f))
            else -> { drawArc(hair, 180f, 180f, true, topLeft = Offset(cx - w * 0.21f, h * 0.19f), size = Size(w * 0.42f, h * 0.22f)); drawRect(hair, Offset(cx - w * 0.21f, h * 0.29f), Size(w * 0.07f, h * 0.2f)); drawRect(hair, Offset(cx + w * 0.14f, h * 0.29f), Size(w * 0.07f, h * 0.2f)) }
        }
        // Zubehör
        when (spec.accessory.mod(4)) {
            1 -> drawCircle(Color(0xFFE7EEF5), w * 0.012f, Offset(cx - w * 0.19f, h * 0.46f))
            2 -> { drawCircle(Color(0xFFCAD6E2), w * 0.045f, Offset(cx - w * 0.075f, h * 0.41f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.008f)); drawCircle(Color(0xFFCAD6E2), w * 0.045f, Offset(cx + w * 0.075f, h * 0.41f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.008f)) }
            3 -> { drawArc(Color(0xFF2A3340), 180f, 180f, false, topLeft = Offset(cx - w * 0.22f, h * 0.2f), size = Size(w * 0.44f, h * 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.02f)); drawCircle(Pn.Accent, w * 0.025f, Offset(cx - w * 0.21f, h * 0.43f)) }
        }
        // Akten-Markierung
        drawLine(Pn.Accent.copy(alpha = 0.7f), Offset(0f, h - 3f), Offset(w, h - 3f), 3f)
    }
}

/** Porträt eines NPCs: lizenziertes Foto (bzw. später lizenziertes Serienstill) oder Original-Avatar. */
@Composable
fun NpcPortrait(data: GameData, state: GameState, id: String, size: Dp, modifier: Modifier = Modifier, height: Dp = size * 1.25f, framed: Boolean = true) {
    val shape = RoundedCornerShape(if (framed) 14.dp else 0.dp)
    val photo = data.portraitFor(id)
    val def = data.agentById[id]
    val custom = state.customNpcs.firstOrNull { it.id == id }
    val context = LocalContext.current
    val revision = PrivateCastPortraits.revision
    val imported = remember(context, id, revision) {
        if (def?.seriesCharacter == true) PrivateCastPortraits.imported(context, id) else null
    }
    val privateBundle = remember(context, id) {
        if (def?.seriesCharacter == true) PrivateCastPortraits.bundled(context, id) else null
    }
    val imageModel: Any? = imported ?: (privateBundle ?: photo)?.let { "file:///android_asset/" + it }
    val loader = LocalLoader.current
    val base = if (framed) modifier.size(width = size, height = height).clip(shape).border(1.dp, Pn.Accent.copy(alpha = 0.35f), shape) else modifier.clip(shape)
    Box(base) {
        if (imageModel != null && loader != null) {
            val f = def?.portraitFocus ?: listOf(0f, -0.5f)
            AsyncImage(
                model = imageModel, imageLoader = loader,
                contentDescription = "Porträt: " + (def?.name ?: id), contentScale = ContentScale.Crop,
                alignment = BiasAlignment(f.getOrElse(0) { 0f }, f.getOrElse(1) { -0.5f }),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AvatarCanvas(custom?.avatar ?: def?.avatar ?: AvatarSpec(skin = id.length % 5, hairStyle = id.length % 7), Modifier.fillMaxSize())
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(height * 0.28f).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC050A12)))))
    }
}

@Composable
fun PlayerPortrait(state: GameState, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.size(width = size, height = size * 1.25f).clip(shape).border(1.dp, Pn.Accent.copy(alpha = 0.5f), shape)) {
        AvatarCanvas(state.player.appearance.avatar, Modifier.fillMaxSize())
    }
}

@Composable
fun ItemIcon(icon: String, size: Dp, modifier: Modifier = Modifier, tint: Color = Pn.Accent) {
    val loader = LocalLoader.current
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Brush.radialGradient(listOf(tint.copy(alpha = 0.30f), Color(0xFF0A1424))))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        var missing by androidx.compose.runtime.remember(icon) { androidx.compose.runtime.mutableStateOf(false) }
        if (loader != null && !missing) {
            AsyncImage(
                model = "file:///android_asset/items/$icon.svg", imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.lerp(Color.White, tint, 0.35f), androidx.compose.ui.graphics.BlendMode.Modulate),
                onError = { missing = true },
                modifier = Modifier.fillMaxSize().padding(size * 0.16f)
            )
        } else {
            // Neutraler Platzhalter: Bild fehlt (Asset-ID sichtbar, siehe docs/MISSING_ITEM_ART.md)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▣", style = MaterialTheme.typography.titleLarge, color = Pn.Muted)
                if (size >= 56.dp) Text(icon, style = MaterialTheme.typography.labelSmall, color = Pn.Muted, maxLines = 1)
            }
        }
    }
}

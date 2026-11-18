package de.protokollnull.agent.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.protokollnull.agent.model.Settings

object Pn {
    val Navy = Color(0xFF070E1A)
    val Navy2 = Color(0xFF0C1626)
    val Graphite = Color(0xFF131D2B)
    val Glass = Color(0xB8152234)
    val GlassStrong = Color(0xE0182839)
    val Line = Color(0x3346E0CF)
    val Accent = Color(0xFF3FD6C4)
    val AccentDim = Color(0xFF1F7F78)
    val Text = Color(0xFFE7EEF5)
    val Muted = Color(0xFF8DA2B6)
    val Warn = Color(0xFFF2B65C)
    val Danger = Color(0xFFE8636C)
    val Ok = Color(0xFF5CD89C)
    val Violet = Color(0xFF9A8CFF)
    val rarity = mapOf(
        "common" to Color(0xFF9AA8B6), "uncommon" to Color(0xFF5CD89C), "rare" to Color(0xFF5AA8FF),
        "epic" to Color(0xFFB27CFF), "legendary" to Color(0xFFF2B65C)
    )
    fun rarityLabel(r: String) = when (r) {
        "common" -> "Standard"; "uncommon" -> "Erweitert"; "rare" -> "Selten"; "epic" -> "Geheim"; "legendary" -> "Legendär"; else -> r
    }
}

val LocalSettings = staticCompositionLocalOf { Settings() }

@Composable
fun animOn(): Boolean = LocalSettings.current.animations

@Composable
fun ProtokollTheme(settings: Settings, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Pn.Accent, onPrimary = Color(0xFF00201C), secondary = Pn.Violet,
        background = Pn.Navy, surface = Pn.Graphite, onSurface = Pn.Text, onBackground = Pn.Text,
        surfaceVariant = Pn.Navy2, onSurfaceVariant = if (settings.highContrast) Color(0xFFD5E2EE) else Pn.Muted,
        error = Pn.Danger, outline = Pn.Line
    )
    val base = Typography()
    val sans = FontFamily.SansSerif
    val typography = Typography(
        displaySmall = base.displaySmall.copy(fontFamily = sans, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = sans, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = sans, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        titleMedium = base.titleMedium.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = sans, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = sans, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontFamily = sans),
        labelLarge = base.labelLarge.copy(fontFamily = sans, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
        labelMedium = base.labelMedium.copy(fontFamily = sans, letterSpacing = 1.sp),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp)
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalSettings provides settings,
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Dunkler Hintergrund mit dezentem Raster und Scanlinie. */
@Composable
fun TacticalBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pn.Navy2, Pn.Navy, Color(0xFF050A12))))) {
        Canvas(Modifier.matchParentSize()) {
            val step = 42.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(Color(0x0D46E0CF), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color(0x0A46E0CF), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            drawCircle(Brush.radialGradient(listOf(Color(0x2238D4C2), Color.Transparent), center = Offset(size.width * 0.85f, size.height * 0.08f), radius = size.width * 0.7f), radius = size.width * 0.7f, center = Offset(size.width * 0.85f, size.height * 0.08f))
        }
        content()
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color = Pn.Accent,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val hc = LocalSettings.current.highContrast
    val shape = RoundedCornerShape(18.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Pn.GlassStrong, Pn.Glass)))
        .border(BorderStroke(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = if (hc) 0.9f else 0.45f), Color(0x1446E0CF), accent.copy(alpha = if (hc) 0.5f else 0.12f)))), shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(Pn.Accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Pn.Accent, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatBar(label: String, value: Int, max: Int, color: Color = Pn.Accent, modifier: Modifier = Modifier, showValue: Boolean = true) {
    val target = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    val anim by animateFloatAsState(target, animationSpec = tween(if (animOn()) 700 else 0), label = "bar")
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (showValue) Text("$value/$max", style = MaterialTheme.typography.labelSmall, color = Pn.Text)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Pn.Accent, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false, subtitle: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (!enabled) Color(0x14FFFFFF) else if (secondary) Color(0x1A3FD6C4) else Pn.Accent
    val fg = if (!enabled) Pn.Muted else if (secondary) Pn.Accent else Color(0xFF02201C)
    Column(
        modifier.clip(shape).background(bg).border(1.dp, if (secondary && enabled) Pn.Accent.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun ChoiceButton(text: String, detail: String? = null, chance: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (enabled) Color(0x1F3FD6C4) else Color(0x10FFFFFF))
            .border(1.dp, if (enabled) Pn.Accent.copy(alpha = 0.4f) else Color(0x22FFFFFF), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (enabled) Pn.Text else Pn.Muted)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chance != null) {
            Spacer(Modifier.width(10.dp))
            val c = when { chance >= 65 -> Pn.Ok; chance >= 40 -> Pn.Warn; else -> Pn.Danger }
            Pill("$chance%", c)
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Pn.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1A3FD6C4)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = Pn.Accent, style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Pn.Text)
        }
        trailing?.invoke()
    }
}

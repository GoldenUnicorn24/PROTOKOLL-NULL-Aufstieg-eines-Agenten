package de.protokollnull.agent.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.protokollnull.agent.R
import de.protokollnull.agent.model.Settings
import kotlinx.coroutines.delay

/**
 * PROTOKOLL NULL Designsystem 2.0 (v1.0).
 *
 * Farb-Tokens (Graphit/Navy mit zurückhaltendem Türkis), Abstände, Formen und
 * wiederverwendbare Bausteine. Alle Screens nutzen ausschließlich diese Tokens –
 * keine Einzel-Screen-Stile.
 */
object Pn {
    val Navy = Color(0xFF060C17)
    val Navy2 = Color(0xFF0B1524)
    val Graphite = Color(0xFF121C2A)
    val Surface1 = Color(0xFF0F1928)
    val Surface2 = Color(0xFF15233A)
    val Glass = Color(0xB0122033)
    val GlassStrong = Color(0xE0162537)
    val Line = Color(0x3346E0CF)
    val Hairline = Color(0x1AFFFFFF)
    val Accent = Color(0xFF3FD6C4)
    val AccentDim = Color(0xFF1F7F78)
    val Text = Color(0xFFE9EFF6)
    val Muted = Color(0xFF93A7BA)
    val Warn = Color(0xFFF2B65C)
    val Danger = Color(0xFFEF6670)
    val Ok = Color(0xFF5CD89C)
    val Violet = Color(0xFF9A8CFF)
    val Rose = Color(0xFFFF8FB1)
    val OnAccent = Color(0xFF02201C)
    val rarity = mapOf(
        "common" to Color(0xFF9AA8B6), "uncommon" to Color(0xFF5CD89C), "rare" to Color(0xFF5AA8FF),
        "epic" to Color(0xFFB27CFF), "legendary" to Color(0xFFF2B65C)
    )
    fun rarityLabel(r: String) = when (r) {
        "common" -> "Standard"; "uncommon" -> "Erweitert"; "rare" -> "Selten"; "epic" -> "Geheim"; "legendary" -> "Legendär"; else -> r
    }
}

/** Abstands-Raster (4-dp-Basis). */
object Sp {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    /** Seitlicher Bildschirmrand. */
    val gutter = 16.dp
    /** Mindesthöhe für Touch-Ziele (Material: 48 dp). */
    val touch = 48.dp
}

object PnShapes {
    val card = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(50)
}

object PnFonts {
    val Display = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))
    val Body = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
    val Mono = FontFamily(Font(R.font.anonymouspro_regular, FontWeight.Normal), Font(R.font.anonymouspro_bold, FontWeight.Bold))
}

/** Zusatzstile neben der Material-Typografie. */
object PnType {
    /** Kleine, gesperrte Überschrift in Versalien (Akten-/HUD-Optik). Nur für kurze Labels. */
    val overline = TextStyle(fontFamily = PnFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp, lineHeight = 14.sp)
    /** Zahlen und Messwerte. */
    val data = TextStyle(fontFamily = PnFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.6.sp)
    /** Große Kennzahl in Kacheln. */
    val metric = TextStyle(fontFamily = PnFonts.Display, fontSize = 26.sp, letterSpacing = 0.5.sp, lineHeight = 28.sp)
}

val LocalSettings = staticCompositionLocalOf { Settings() }

@Composable
fun animOn(): Boolean = LocalSettings.current.animations

@Composable
fun ProtokollTheme(settings: Settings, content: @Composable () -> Unit) {
    val hc = settings.highContrast
    val scheme = darkColorScheme(
        primary = Pn.Accent, onPrimary = Pn.OnAccent, secondary = Pn.Violet,
        background = Pn.Navy, surface = Pn.Graphite, onSurface = Pn.Text, onBackground = Pn.Text,
        surfaceVariant = Pn.Navy2, onSurfaceVariant = if (hc) Color(0xFFDCE7F1) else Pn.Muted,
        error = Pn.Danger, outline = if (hc) Pn.Accent.copy(alpha = 0.7f) else Pn.Line, secondaryContainer = Color(0x333FD6C4), onSecondaryContainer = Pn.Accent,
        surfaceContainerHigh = Pn.Surface2, surfaceContainer = Pn.Surface1
    )
    val base = Typography()
    val b = PnFonts.Body
    val d = PnFonts.Display
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = d, letterSpacing = 1.sp),
        displaySmall = base.displaySmall.copy(fontFamily = d, letterSpacing = 1.2.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = d, letterSpacing = 1.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = d, letterSpacing = 0.8.sp),
        titleLarge = base.titleLarge.copy(fontFamily = d, fontSize = 23.sp, lineHeight = 27.sp, letterSpacing = 0.7.sp),
        titleMedium = base.titleMedium.copy(fontFamily = b, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = base.titleSmall.copy(fontFamily = b, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = b, fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = b, fontSize = 14.5.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontFamily = b, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontFamily = b, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.9.sp),
        labelMedium = base.labelMedium.copy(fontFamily = b, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.3.sp),
        // v1.0: Sekundärtexte in Inter statt Monospace – deutlich besser lesbar bei langen deutschen Wörtern
        labelSmall = base.labelSmall.copy(fontFamily = b, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
    )
    // Deutsche Silbentrennung für lange Komposita (statt harter Umbrüche mitten im Wort)
    val de = androidx.compose.ui.text.intl.LocaleList("de-DE")
    fun TextStyle.hy() = copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto, localeList = de)
    val hyphenated = typography.copy(
        titleMedium = typography.titleMedium.hy(), titleSmall = typography.titleSmall.hy(),
        bodyLarge = typography.bodyLarge.hy(), bodyMedium = typography.bodyMedium.hy(), bodySmall = typography.bodySmall.hy(),
        labelLarge = typography.labelLarge.hy(), labelMedium = typography.labelMedium.hy(), labelSmall = typography.labelSmall.hy()
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalSettings provides settings,
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = hyphenated) {
            CompositionLocalProvider(LocalContentColor provides Pn.Text, content = content)
        }
    }
}

/** Dunkler Hintergrund mit Raster, Lichtkegel und langsam wandernder Scanlinie. */
@Composable
fun TacticalBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val anim = animOn()
    val scan = if (anim) {
        val t = rememberInfiniteTransition(label = "scan")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "scanY").value
    } else 0.3f
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pn.Navy2, Pn.Navy, Color(0xFF04080F))))) {
        Canvas(Modifier.matchParentSize()) {
            val step = 36.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(Color(0x0946E0CF), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color(0x0746E0CF), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            val c1 = Offset(size.width * 0.9f, size.height * 0.04f)
            drawCircle(Brush.radialGradient(listOf(Color(0x2438D4C2), Color.Transparent), center = c1, radius = size.width * 0.8f), radius = size.width * 0.8f, center = c1)
            val c2 = Offset(0f, size.height * 0.85f)
            drawCircle(Brush.radialGradient(listOf(Color(0x149A8CFF), Color.Transparent), center = c2, radius = size.width * 0.7f), radius = size.width * 0.7f, center = c2)
            val sy = size.height * scan
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x0D3FD6C4), Color.Transparent), startY = sy - 60f, endY = sy + 60f),
                topLeft = Offset(0f, sy - 60f), size = androidx.compose.ui.geometry.Size(size.width, 120f)
            )
        }
        content()
    }
}

/** Glas-Panel mit Eckmarken (Akten-/HUD-Optik). Basis aller Karten. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color = Pn.Accent,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val hc = LocalSettings.current.highContrast
    val shape = PnShapes.card
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null && animOn()) 0.985f else 1f, label = "press")
    val base = modifier
        .fillMaxWidth()
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Pn.GlassStrong, Pn.Glass)))
        .border(BorderStroke(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = if (hc) 0.9f else 0.42f), Color(0x1046E0CF), accent.copy(alpha = if (hc) 0.5f else 0.12f)))), shape)
        .drawBehind {
            val l = 12.dp.toPx(); val w = 2.dp.toPx(); val c = accent.copy(alpha = 0.8f)
            drawLine(c, Offset(0f, w / 2), Offset(l, w / 2), w)
            drawLine(c, Offset(w / 2, 0f), Offset(w / 2, l), w)
            drawLine(c, Offset(size.width - l, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            drawLine(c, Offset(size.width - w / 2, size.height - l), Offset(size.width - w / 2, size.height), w)
            drawLine(Color(0x1CFFFFFF), Offset(l * 1.5f, 0.5f), Offset(size.width * 0.6f, 0.5f), 1f)
        }
    val clickable = if (onClick != null) base.clickable(interactionSource = interaction, indication = ripple(color = accent), role = Role.Button, onClick = onClick) else base
    Column(clickable.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** Abschnittsüberschrift mit Akzentstrich; als Überschrift für TalkBack markiert. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null, icon: ImageVector? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp).semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { PnIcon(icon, size = 16.dp); Spacer(Modifier.width(8.dp)) }
        else { Box(Modifier.size(width = 14.dp, height = 2.dp).background(Pn.Accent)); Spacer(Modifier.width(8.dp)) }
        Text(text.uppercase(), style = PnType.overline.copy(fontSize = 12.sp), color = Pn.Accent, modifier = Modifier.weight(1f), maxLines = 2)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Kleine Überschrift innerhalb einer Karte. */
@Composable
fun Overline(text: String, color: Color = Pn.Accent, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = PnType.overline, color = color, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun StatBar(label: String, value: Int, max: Int, color: Color = Pn.Accent, modifier: Modifier = Modifier, showValue: Boolean = true, icon: ImageVector? = null) {
    val target = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    val anim by animateFloatAsState(target, animationSpec = tween(if (animOn()) 700 else 0), label = "bar")
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value von $max" }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { PnIcon(icon, color, 13.dp); Spacer(Modifier.width(4.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showValue) Text("$value/$max", style = PnType.data.copy(fontSize = 12.sp), color = Pn.Text)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x1FFFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))))
            Canvas(Modifier.matchParentSize()) {
                val seg = size.width / 10
                var x = seg
                while (x < size.width) { drawLine(Color(0x55060C17), Offset(x, 0f), Offset(x, size.height), 2f); x += seg }
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Pn.Accent, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(PnShapes.pill).background(color.copy(alpha = 0.13f)).border(1.dp, color.copy(alpha = 0.36f), PnShapes.pill).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { PnIcon(icon, color, 12.dp); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ActionButton(
    text: String, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false,
    subtitle: String? = null, icon: ImageVector? = null, danger: Boolean = false, onClick: () -> Unit
) {
    val shape = PnShapes.control
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && animOn()) 0.97f else 1f, label = "btn")
    val tone = if (danger) Pn.Danger else Pn.Accent
    val bg = when {
        !enabled -> Brush.linearGradient(listOf(Color(0x12FFFFFF), Color(0x0AFFFFFF)))
        secondary -> Brush.linearGradient(listOf(tone.copy(alpha = 0.16f), tone.copy(alpha = 0.06f)))
        danger -> Brush.linearGradient(listOf(Color(0xFFF2828A), Pn.Danger, Color(0xFFC94A55)))
        else -> Brush.linearGradient(listOf(Color(0xFF52E5D2), Pn.Accent, Color(0xFF2AB3A4)))
    }
    val fg = if (!enabled) Pn.Muted else if (secondary) tone else if (danger) Color(0xFF2A0508) else Pn.OnAccent
    Column(
        modifier.defaultMinSize(minHeight = Sp.touch).graphicsLayer(scaleX = scale, scaleY = scale).clip(shape).background(bg)
            .border(1.dp, if (secondary && enabled) tone.copy(alpha = 0.5f) else if (!enabled) Color(0x18FFFFFF) else Color(0x66FFFFFF), shape)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { PnIcon(icon, fg, 17.dp); Spacer(Modifier.width(7.dp)) }
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
        if (subtitle != null) Text(subtitle, color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun ChoiceButton(text: String, detail: String? = null, chance: Int? = null, enabled: Boolean = true, icon: ImageVector? = null, selected: Boolean = false, onClick: () -> Unit) {
    val shape = PnShapes.control
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && animOn()) 0.985f else 1f, label = "choice")
    val tone = if (selected) Pn.Ok else Pn.Accent
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(shape)
            .background(if (enabled) Brush.horizontalGradient(listOf(tone.copy(alpha = if (selected) 0.24f else 0.15f), tone.copy(alpha = 0.05f))) else Brush.horizontalGradient(listOf(Color(0x10FFFFFF), Color(0x08FFFFFF))))
            .border(if (selected) 1.5.dp else 1.dp, if (enabled) tone.copy(alpha = if (selected) 0.8f else 0.38f) else Color(0x1EFFFFFF), shape)
            .clickable(interactionSource = interaction, indication = ripple(color = tone), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 3.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).background(if (enabled) tone else Pn.Muted.copy(alpha = 0.4f)))
        Spacer(Modifier.width(12.dp))
        if (icon != null) { PnIcon(icon, if (enabled) tone else Pn.Muted, 18.dp); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (enabled) Pn.Text else Pn.Muted)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) { Spacer(Modifier.width(8.dp)); PnIcon(PnIcons.Check, Pn.Ok, 18.dp) }
        if (chance != null) {
            Spacer(Modifier.width(10.dp))
            ChanceRing(chance)
        }
    }
}

/** Ringanzeige für Erfolgswahrscheinlichkeiten. */
@Composable
fun ChanceRing(chance: Int, size: Dp = 42.dp) {
    val c = when { chance >= 65 -> Pn.Ok; chance >= 40 -> Pn.Warn; else -> Pn.Danger }
    Box(Modifier.size(size).semantics { contentDescription = "Erfolgschance $chance Prozent" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = 3.dp.toPx()
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, style = Stroke(sw))
            drawArc(c, -90f, 360f * chance / 100f, false, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text("$chance%", style = PnType.data.copy(fontSize = 11.sp), color = c)
    }
}

/**
 * Schlüssel/Wert-Zeile. v1.0: feste Spaltenaufteilung, damit lange Werte umbrechen
 * statt den Schlüssel auf Buchstabenbreite zusammenzudrücken.
 */
@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Pn.Text) {
    // Inhaltsabhängige Aufteilung: Der Wert erhält höchstens 60 % der Breite, der Schlüssel
    // den Rest – kurze Werte lassen langen deutschen Komposita genug Platz.
    androidx.compose.ui.layout.Layout(
        content = {
            Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}
    ) { measurables, constraints ->
        val gap = 10.dp.roundToPx()
        // In unbegrenzt breiten Containern (horizontales Scrollen) auf natürliche Breite fallen
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth
            else measurables[0].maxIntrinsicWidth(constraints.maxHeight) + gap + measurables[1].maxIntrinsicWidth(constraints.maxHeight)
        val valuePl = measurables[1].measure(androidx.compose.ui.unit.Constraints(maxWidth = (w * 0.6f).toInt()))
        val keyPl = measurables[0].measure(androidx.compose.ui.unit.Constraints(maxWidth = (w - valuePl.width - gap).coerceAtLeast(w / 3)))
        val h = maxOf(keyPl.height, valuePl.height)
        layout(w, h) {
            keyPl.placeRelative(0, 0)
            valuePl.placeRelative(w - valuePl.width, 0)
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = Sp.gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    Modifier.size(Sp.touch).clip(RoundedCornerShape(14.dp)).background(Color(0x1F3FD6C4))
                        .border(1.dp, Pn.Accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClickLabel = "Zurück", onClick = onBack)
                        .semantics { contentDescription = "Zurück" },
                    contentAlignment = Alignment.Center
                ) { PnIcon(PnIcons.Back, size = 22.dp) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                if (subtitle != null) Text(subtitle.uppercase(), style = PnType.overline, color = Pn.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Pn.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Pn.Accent.copy(alpha = 0.35f), Color.Transparent))))
    }
}

/** Pulsierender Punkt (z. B. Standortmarker). */
@Composable
fun PulseDot(color: Color = Pn.Accent, size: Dp = 10.dp) {
    val anim = animOn()
    val a = if (anim) rememberInfiniteTransition(label = "pulse").animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value else 1f
    Box(Modifier.size(size * 2), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 2).clip(CircleShape).background(color.copy(alpha = 0.25f * a)))
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** Schreibmaschinen-Text; Tippen zeigt sofort alles. Ohne Animationen sofort vollständig. */
@Composable
fun TypewriterText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Pn.Text) {
    val anim = animOn()
    var shown by remember(text) { mutableIntStateOf(if (anim) 0 else text.length) }
    LaunchedEffect(text) {
        if (anim) {
            while (shown < text.length) { delay(12); shown = (shown + 3).coerceAtMost(text.length) }
        }
    }
    Text(
        text.take(shown) + if (shown < text.length) "▍" else "", style = style, color = color,
        modifier = modifier
            .semantics { contentDescription = text }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Text vollständig anzeigen") { shown = text.length }
    )
}

/** Fortschrittsanzeige der Missionsphasen. */
@Composable
fun PhaseStepper(types: List<String>, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Phase ${current + 1} von ${types.size}" }, verticalAlignment = Alignment.CenterVertically) {
        types.forEachIndexed { i, t ->
            val done = i < current
            val now = i == current
            val c = when { now -> Pn.Accent; done -> Pn.Ok; else -> Pn.Muted.copy(alpha = 0.5f) }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(c.copy(alpha = if (now) 0.25f else 0.1f)).border(if (now) 2.dp else 1.dp, c, CircleShape),
                contentAlignment = Alignment.Center
            ) { PnIcon(if (done) PnIcons.Check else PnIcons.forStage(t), c, 15.dp) }
            if (i < types.lastIndex) Box(Modifier.weight(1f).height(2.dp).background(if (done) Pn.Ok.copy(alpha = 0.6f) else Color(0x22FFFFFF)))
        }
    }
}

/** Quadratische Icon-Kachel. */
@Composable
fun IconTile(icon: ImageVector, color: Color = Pn.Accent, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.08f))))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) { PnIcon(icon, color, size * 0.5f) }
}

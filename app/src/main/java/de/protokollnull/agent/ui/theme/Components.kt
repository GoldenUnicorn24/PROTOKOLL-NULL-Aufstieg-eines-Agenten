package de.protokollnull.agent.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reiterleiste: eine Zeile, horizontal scrollbar, der gewählte Reiter scrollt
 * automatisch in den sichtbaren Bereich. Ersetzt die früheren mehrzeiligen
 * Chip-Gitter (z. B. Mission Control, Personalakte, Führungscockpit).
 */
@Composable
fun PnTabs(
    tabs: List<Pair<String, String>>,
    selected: String,
    modifier: Modifier = Modifier,
    badges: Set<String> = emptySet(),
    onSelect: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val index = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val anim = animOn()
    LaunchedEffect(index) {
        val target = (index - 1).coerceAtLeast(0)
        if (anim) listState.animateScrollToItem(target) else listState.scrollToItem(target)
    }
    Box(modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().clip(PnShapes.control).background(Color(0x14FFFFFF)).border(1.dp, Pn.Hairline, PnShapes.control).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(tabs, key = { _, t -> t.first }) { _, (id, label) ->
                val sel = id == selected
                val bg by animateColorAsState(if (sel) Pn.Accent.copy(alpha = 0.22f) else Color.Transparent, label = "tab")
                Box(
                    Modifier.defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(11.dp)).background(bg)
                        .border(1.dp, if (sel) Pn.Accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(11.dp))
                        .clickable(role = Role.Tab) { onSelect(id) }
                        .semantics { this.selected = sel }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.4.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (sel) Pn.Accent else Pn.Text.copy(alpha = 0.82f), maxLines = 1)
                        if (id in badges) { Spacer(Modifier.width(6.dp)); BadgeDot(Pn.Warn) }
                    }
                }
            }
        }
    }
}

/** Auswahlchip für Optionen (nicht für Navigation). */
@Composable
fun PnChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Pn.Accent.copy(alpha = 0.2f) else Color(0x12FFFFFF), label = "chip")
    Row(
        modifier.defaultMinSize(minHeight = 40.dp).clip(PnShapes.chip).background(bg)
            .border(1.dp, if (selected) Pn.Accent.copy(alpha = 0.7f) else Pn.Hairline, PnShapes.chip)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) { PnIcon(PnIcons.Check, Pn.Accent, 14.dp); Spacer(Modifier.width(5.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = when { !enabled -> Pn.Muted.copy(alpha = 0.6f); selected -> Pn.Accent; else -> Pn.Text }, maxLines = 2)
    }
}

/** Navigationszeile mit Icon, Titel, Untertitel, optionalem Hinweis und Pfeil. */
@Composable
fun NavRow(
    title: String, subtitle: String? = null, icon: ImageVector? = null, color: Color = Pn.Accent,
    badge: String? = null, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).clip(PnShapes.control)
            .background(Brush.horizontalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Pn.Hairline, PnShapes.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { IconTile(icon, if (enabled) color else Pn.Muted, 38.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Pn.Text else Pn.Muted)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) { Spacer(Modifier.width(8.dp)); Pill(badge, Pn.Warn) }
        Spacer(Modifier.width(6.dp))
        PnIcon(PnIcons.Chevron, if (enabled) Pn.Accent.copy(alpha = 0.8f) else Pn.Muted, 18.dp)
    }
}

/** Kompakte Kennzahl-Kachel. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Pn.Accent, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(PnShapes.control).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.14f), color.copy(alpha = 0.04f))))
            .border(1.dp, color.copy(alpha = 0.3f), PnShapes.control)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { PnIcon(icon, color, 14.dp); Spacer(Modifier.width(5.dp)) }
            Text(label.uppercase(), style = PnType.overline.copy(fontSize = 10.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = PnType.metric.copy(fontSize = 22.sp), color = Pn.Text, maxLines = 1)
    }
}

/** Leerer Zustand mit Icon, Erklärung und optionaler Aktion. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(PnShapes.card).background(Color(0x0DFFFFFF)).border(1.dp, Pn.Hairline, PnShapes.card).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconTile(icon, Pn.Muted, 46.dp)
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) ActionButton(actionLabel, secondary = true, onClick = onAction)
    }
}

/** Hinweiszeile mit Info-Symbol (ersetzt kleingedruckte Monospace-Notizen). */
@Composable
fun InfoNote(text: String, color: Color = Pn.Muted, icon: ImageVector = PnIcons.Info) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        PnIcon(icon, color.copy(alpha = 0.9f), 15.dp, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (color == Pn.Muted) MaterialTheme.colorScheme.onSurfaceVariant else color)
    }
}

/** Banner für wichtige Lagehinweise (Warnung, Erfolg, Info). */
@Composable
fun Banner(title: String, text: String? = null, color: Color = Pn.Warn, icon: ImageVector = PnIcons.Skull, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(PnShapes.control).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.45f), PnShapes.control)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PnIcon(icon, color, 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = Pn.Text.copy(alpha = 0.85f))
        }
        if (onClick != null) PnIcon(PnIcons.Chevron, color, 18.dp)
    }
}

@Composable
fun BadgeDot(color: Color = Pn.Warn, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, Pn.Navy, CircleShape))
}

/** Fortschrittsring mit Wert in der Mitte. */
@Composable
fun ProgressRing(value: Int, max: Int, size: Dp = 64.dp, color: Color = Pn.Accent, label: String? = null, stroke: Dp = 5.dp) {
    val target = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    val anim by animateFloatAsState(target, tween(if (animOn()) 800 else 0), label = "ring")
    Box(Modifier.size(size).semantics { contentDescription = (label ?: "Fortschritt") + ": $value von $max" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, style = Stroke(sw), topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw))
            drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.5f), color)), -90f, 360f * anim, false, style = Stroke(sw, cap = StrokeCap.Round), topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label ?: "${(target * 100).toInt()}%", style = PnType.data.copy(fontSize = if ((label?.length ?: 3) > 3) 11.sp else 15.sp), color = Pn.Text, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** Sechseck-Diagramm der Attribute (Spinnennetz). */
@Composable
fun AttributeRadar(values: List<Pair<String, Int>>, modifier: Modifier = Modifier, max: Int = 100, color: Color = Pn.Accent) {
    val anim by animateFloatAsState(1f, tween(if (animOn()) 900 else 0), label = "radar")
    Canvas(modifier.semantics { contentDescription = values.joinToString { "${it.first} ${it.second}" } }) {
        val n = values.size.coerceAtLeast(3)
        val c = center
        val r = size.minDimension / 2 * 0.86f
        fun pt(i: Int, f: Float): Offset {
            val a = Math.toRadians((-90 + 360.0 * i / n)).toFloat()
            return Offset(c.x + cos(a) * r * f, c.y + sin(a) * r * f)
        }
        for (ring in 1..4) {
            val p = Path()
            for (i in 0 until n) { val o = pt(i, ring / 4f); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            p.close()
            drawPath(p, Color(0x1F46E0CF), style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(Color(0x1746E0CF), c, pt(i, 1f), 1.dp.toPx())
        val shape = Path()
        values.forEachIndexed { i, (_, v) ->
            val o = pt(i, (v.toFloat() / max).coerceIn(0.04f, 1f) * anim)
            if (i == 0) shape.moveTo(o.x, o.y) else shape.lineTo(o.x, o.y)
        }
        shape.close()
        drawPath(shape, Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.12f)), center = c, radius = r))
        drawPath(shape, color, style = Stroke(2.dp.toPx()))
        values.forEachIndexed { i, (_, v) -> drawCircle(color, 3.dp.toPx(), pt(i, (v.toFloat() / max).coerceIn(0.04f, 1f) * anim)) }
    }
}

/** Gestalteter Bestätigungsdialog. */
@Composable
fun PnDialog(
    title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    dismissLabel: String = "Abbrechen", danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pn.Surface2,
        shape = PnShapes.card,
        titleContentColor = Pn.Text,
        textContentColor = Pn.Text.copy(alpha = 0.88f),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { ActionButton(confirmLabel, danger = danger, onClick = onConfirm) },
        dismissButton = { ActionButton(dismissLabel, secondary = true, onClick = onDismiss) }
    )
}

/** Ein Ereignis, das mit einer kurzen Animation gefeiert wird (Level, Rang, Erfolg). */
data class Celebration(val id: Long, val kind: String, val title: String, val subtitle: String)

/**
 * Zentrale Einblendung für Levelaufstieg, Beförderung und abgeschlossene Kapitel.
 * Tippen schließt sofort; ohne Animationen erscheint sie statisch.
 */
@Composable
fun CelebrationHost(c: Celebration?, onDismiss: () -> Unit) {
    val anim = animOn()
    LaunchedEffect(c?.id) { if (c != null) { delay(3400); onDismiss() } }
    AnimatedVisibility(
        visible = c != null,
        enter = if (anim) fadeIn(tween(250)) + scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.85f) else fadeIn(snap()),
        exit = if (anim) fadeOut(tween(220)) + scaleOut(targetScale = 1.05f) else fadeOut(snap())
    ) {
        val cur = c ?: return@AnimatedVisibility
        val color = when (cur.kind) { "rank" -> Pn.Warn; "level" -> Pn.Accent; "chapter" -> Pn.Violet; "fail" -> Pn.Danger; else -> Pn.Ok }
        val icon = when (cur.kind) { "rank" -> PnIcons.Medal; "level" -> PnIcons.Star; "chapter" -> PnIcons.Book; "fail" -> PnIcons.Skull; else -> PnIcons.Trophy }
        val glow = if (anim) rememberInfiniteTransition(label = "glow").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "g").value else 0.8f
        Box(
            Modifier.fillMaxSize().background(Color(0xB3040810))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Schließen", onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(32.dp).clip(PnShapes.card).background(Brush.verticalGradient(listOf(Pn.Surface2, Pn.Navy2)))
                    .border(1.5.dp, color.copy(alpha = 0.7f), PnShapes.card).padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(96.dp).graphicsLayer(alpha = glow).clip(CircleShape).background(Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent))))
                    IconTile(icon, color, 60.dp)
                }
                Text(cur.title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = color, textAlign = TextAlign.Center)
                Text(cur.subtitle, style = MaterialTheme.typography.bodyMedium, color = Pn.Text, textAlign = TextAlign.Center)
                Text("Tippen zum Fortfahren", style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
            }
        }
    }
}

/** Blockierende Anzeige während längerer Hintergrundberechnungen (Zeitraffer). */
@Composable
fun BusyOverlay(label: String?) {
    if (label == null) return
    Box(
        Modifier.fillMaxSize().background(Color(0xCC040810))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Pn.Accent, trackColor = Color(0x22FFFFFF))
            Text(label, style = MaterialTheme.typography.titleSmall, color = Pn.Text)
        }
    }
}

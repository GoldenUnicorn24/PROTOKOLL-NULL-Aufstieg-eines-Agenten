package de.protokollnull.agent.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.protokollnull.agent.engine.Missions
import de.protokollnull.agent.engine.Tactical
import de.protokollnull.agent.engine.TacticalMaps
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.GameViewModel
import de.protokollnull.agent.ui.theme.*

/**
 * v1.0 Taktikbrett: eine performante Canvas-Darstellung statt ~100 einzelner Kacheln.
 * Zweistufige Touch-Steuerung (zielen/vormerken → bestätigen) verhindert Fehltipps auf
 * kleinen Displays; Pfad, Gefahrenzonen und Trefferchancen kommen direkt aus dem Spielzustand.
 */
@Composable
fun TacticalScreen(vm: GameViewModel, s: GameState, st: StageDefinition, run: MissionRun) {
    val t = run.tactical ?: return
    var mode by remember { mutableStateOf("act") }
    var pending by remember(t.turn, t.selectedUnit) { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDanger by remember { mutableStateOf(true) }
    val sel = t.units.firstOrNull { it.id == t.selectedUnit && !it.down && !it.evacuated }
    val reach = if (sel != null && sel.ap > 0 && mode == "act" && sel.stunned == 0 && !t.finished) Tactical.reachable(t, sel) else emptySet()
    val visible = remember(t.units, t.selectedUnit) { if (sel != null && !sel.vip) Tactical.visibleTiles(t, sel) else emptySet() }
    val danger = remember(t.units, reach, showDanger) { if (showDanger) reach.associateWith { Tactical.dangerAt(t, it.first, it.second) } else emptyMap() }
    val pendingPath = remember(pending, t.units) { pending?.let { p -> if (sel != null && p in reach) Tactical.path(t, sel, p.first, p.second) else emptyList() } ?: emptyList() }
    val pendingEnemy = pending?.let { p -> Tactical.unitAt(t, p.first, p.second)?.takeIf { !it.ally } }

    fun upd(f: (TacticalState) -> TacticalState) { pending = null; vm.act(silent = true) { d, x -> Missions.updateTactical(d, x, f) } }

    fun tap(x: Int, y: Int) {
        if (t.finished) return
        val unit = Tactical.unitAt(t, x, y)
        when {
            unit != null && unit.ally -> { mode = "act"; pending = null; upd { Tactical.select(it, unit.id) } }
            unit != null && sel != null && mode == "takedown" -> { upd { Tactical.takedown(it, sel.id, unit.id) }; mode = "act" }
            unit != null && sel != null && mode == "ability" -> { upd { Tactical.useAbility(it, sel.id, unit.id) }; mode = "act" }
            unit != null && sel != null -> if (pending == (x to y)) upd { Tactical.attack(it, sel.id, unit.id) } else pending = x to y
            (x to y) in reach && sel != null -> if (pending == (x to y)) upd { Tactical.move(it, sel.id, x, y) } else pending = x to y
            else -> pending = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ObjectivePanel(t)
        UnitStrip(t, sel) { id -> mode = "act"; upd { Tactical.select(it, id) } }
        val hint = when {
            t.finished -> null
            mode == "takedown" -> "Überwältigen: angrenzenden Gegner antippen"
            mode == "ability" -> "Fähigkeit: Ziel antippen"
            pendingEnemy != null && sel != null -> Tactical.attackBlocker(t, sel, pendingEnemy)?.let { "${pendingEnemy.name}: $it" }
                ?: "${pendingEnemy.name} anvisiert · Treffer ${Tactical.hitChance(t, sel, pendingEnemy)}% · erneut tippen zum Angreifen"
            pending != null -> "Ziel vorgemerkt (${pendingPath.size} Felder" + (danger[pending]?.takeIf { it > 0 }?.let { " · im Schussfeld von $it" } ?: "") + ") · erneut tippen zum Bewegen"
            sel != null && sel.ap > 0 -> "Feld antippen zum Vormerken · Gegner antippen zum Zielen"
            else -> "Keine Aktionspunkte mehr – Runde beenden oder andere Einheit wählen"
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelMedium, color = if (mode != "act") Pn.Violet else if (pending != null) Pn.Warn else Pn.Muted)

        Board(t, sel, reach, visible, danger, pendingPath, pending, Modifier.fillMaxWidth(), onTap = ::tap)

        if (sel != null) ActionPanel(t, sel, mode, onMode = { mode = it; pending = null }, upd = ::upd)
        EnemyList(t, sel, pending) { e -> if (sel != null) { if (pending == (e.x to e.y)) upd { Tactical.attack(it, sel.id, e.id) } else pending = e.x to e.y } }
        GlassPanel(accent = Pn.Muted, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Gefechtsprotokoll", Pn.Muted, Modifier.weight(1f))
                PnChip("Gefahrenzonen", showDanger) { showDanger = !showDanger }
            }
            t.log.takeLast(6).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = when {
                    line.contains("Reaktionsfeuer") || line.contains("kampfunfähig") -> Pn.Warn
                    line.startsWith("—") -> Pn.Accent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                })
            }
        }
        Legend()
    }
}

@Composable
private fun ObjectivePanel(t: TacticalState) {
    GlassPanel(padding = 12.dp, accent = Pn.Warn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(t.mapName.ifBlank { "Einsatzbrett" } + (TacticalMaps.environments[t.environment]?.let { " · $it" } ?: ""), Pn.Warn, Modifier.weight(1f))
            Pill("Runde ${t.turn.coerceAtMost(t.maxTurns)}/${t.maxTurns}", if (t.maxTurns - t.turn <= 2) Pn.Danger else Pn.Accent, icon = PnIcons.Hourglass)
        }
        Text(Tactical.objectiveLabel(t.objective, t.requiredSecured, t.holdTurns, t.units.count { !it.ally }), style = MaterialTheme.typography.titleSmall)
        when (t.objective) {
            "intel", "recon" -> StatBar("Fortschritt", t.secured.size, t.requiredSecured.coerceAtLeast(1), Pn.Warn)
            "protect" -> StatBar("Gehalten", t.held, t.holdTurns.coerceAtLeast(1), Pn.Ok)
            "eliminate" -> StatBar("Neutralisiert", t.units.count { !it.ally && it.down }, t.units.count { !it.ally }.coerceAtLeast(1), Pn.Danger)
        }
        if (t.stalemate >= 2 && !t.finished) Text("Patt droht: noch ${Tactical.STALEMATE_ROUNDS - t.stalemate} Runden ohne Fortschritt bis zum Rückzug.", style = MaterialTheme.typography.labelSmall, color = Pn.Danger)
        if (t.finished) Banner(if (t.won) "Einsatzziel erreicht" else "Einsatzziel verfehlt", null, if (t.won) Pn.Ok else Pn.Danger, if (t.won) PnIcons.Trophy else PnIcons.Skull)
    }
}

/** Schnellauswahl der eigenen Einheiten mit LP und AP. */
@Composable
private fun UnitStrip(t: TacticalState, sel: TacticalUnit?, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        t.units.filter { it.ally && !it.evacuated }.forEach { u ->
            val active = sel?.id == u.id
            val color = unitColor(u)
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) color.copy(alpha = 0.22f) else Color(0x12FFFFFF))
                    .border(1.dp, if (active) color else Pn.Hairline, RoundedCornerShape(12.dp))
                    .clickable(enabled = !u.down, role = Role.Button) { onSelect(u.id) }
                    .semantics { contentDescription = "${u.name}, ${u.hp} von ${u.maxHp} Lebenspunkten, ${u.ap} Aktionspunkte" }
                    .defaultMinSize(minHeight = 44.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(u.name.substringBefore(" "), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = if (u.down) Pn.Muted else Pn.Text, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (u.down) "kampfunfähig" else "${u.hp}/${u.maxHp}", style = PnType.data.copy(fontSize = 11.sp), color = if (u.hp * 3 <= u.maxHp) Pn.Danger else Pn.Ok)
                        if (!u.down && !u.vip) { Spacer(Modifier.width(6.dp)); repeat(u.ap.coerceIn(0, 4)) { Box(Modifier.padding(end = 2.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(Pn.Accent)) } }
                    }
                }
            }
        }
    }
}

private fun unitColor(u: TacticalUnit) = when { u.vip -> Pn.Warn; u.isPlayer -> Pn.Accent; u.ally -> Pn.Ok; else -> Pn.Danger }

private fun envTint(env: String) = when (env) {
    "industrial" -> Color(0xFF3A3324); "clinic" -> Color(0xFF203A42); "transit" -> Color(0xFF1F2E4E)
    "nature" -> Color(0xFF1F3D2C); "interior" -> Color(0xFF2A2E42); else -> Color(0xFF213651)
}

@Composable
private fun Board(
    t: TacticalState, sel: TacticalUnit?, reach: Set<Pair<Int, Int>>, visible: Set<Pair<Int, Int>>, danger: Map<Pair<Int, Int>, Int>,
    path: List<Pair<Int, Int>>, pending: Pair<Int, Int>?, modifier: Modifier, onTap: (Int, Int) -> Unit
) {
    val measurer = rememberTextMeasurer()
    // Aktuellen Tap-Handler verwenden, obwohl pointerInput nur bei Größenänderung neu startet
    val currentTap by rememberUpdatedState(onTap)
    val pulse = if (animOn()) rememberInfiniteTransition(label = "board").animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p").value else 0.8f
    val tint = envTint(t.environment)
    val enemiesInSight = t.units.count { !it.ally && !it.down && (sel == null || (it.x to it.y) in visible) }
    val desc = "Taktikbrett ${t.width} mal ${t.height}. " + (sel?.let { "Ausgewählt: ${it.name} mit ${it.ap} Aktionspunkten. " } ?: "") + "$enemiesInSight Gegner in Sicht. Gegnerliste unterhalb zum Zielen nutzen."
    Canvas(
        modifier.aspectRatio(t.width.toFloat() / t.height).clip(RoundedCornerShape(14.dp))
            .border(1.dp, Pn.Line, RoundedCornerShape(14.dp))
            .semantics { contentDescription = desc }
            .pointerInput(t.width, t.height) {
                detectTapGestures { o ->
                    val cw = size.width.toFloat() / t.width; val ch = size.height.toFloat() / t.height
                    val x = (o.x / cw).toInt().coerceIn(0, t.width - 1); val y = (o.y / ch).toInt().coerceIn(0, t.height - 1)
                    currentTap(x, y)
                }
            }
    ) {
        val cw = size.width / t.width; val ch = size.height / t.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1422), Color(0xFF070D17))))
        for (y in 0 until t.height) for (x in 0 until t.width) drawTile(t, x, y, Offset(x * cw, y * ch), cw, ch, tint, measurer, pulse)
        // Reichweite, Gefahr und Sicht
        for (y in 0 until t.height) for (x in 0 until t.width) {
            val o = Offset(x * cw, y * ch)
            val tile = t.tile(x, y)
            if ((x to y) in reach) {
                val d = danger[x to y] ?: 0
                drawRoundRect(Pn.Accent.copy(alpha = 0.14f), o + Offset(2f, 2f), Size(cw - 4f, ch - 4f), CornerRadius(8f))
                drawRoundRect(Pn.Accent.copy(alpha = 0.45f), o + Offset(2f, 2f), Size(cw - 4f, ch - 4f), CornerRadius(8f), style = Stroke(1.2.dp.toPx()))
                if (d > 0) {
                    drawRoundRect(Pn.Danger.copy(alpha = 0.10f + 0.06f * d.coerceAtMost(3)), o + Offset(2f, 2f), Size(cw - 4f, ch - 4f), CornerRadius(8f))
                    repeat(d.coerceAtMost(3)) { i -> drawCircle(Pn.Danger, 2.2.dp.toPx(), o + Offset(cw - 6.dp.toPx() - i * 5.dp.toPx(), 6.dp.toPx())) }
                }
            }
            val seen = sel == null || sel.vip || (x to y) in visible || tile == '#'
            if (!seen) drawRect(Color(0xA6050A12), o, Size(cw, ch))
        }
        // Pfadvorschau
        if (sel != null && path.isNotEmpty()) {
            val pts = listOf(sel.x to sel.y) + path
            val p = Path()
            pts.forEachIndexed { i, (px, py) -> val c = Offset(px * cw + cw / 2, py * ch + ch / 2); if (i == 0) p.moveTo(c.x, c.y) else p.lineTo(c.x, c.y) }
            drawPath(p, Pn.Warn, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        if (pending != null) {
            val o = Offset(pending.first * cw, pending.second * ch)
            drawRoundRect(Pn.Warn.copy(alpha = pulse), o + Offset(2f, 2f), Size(cw - 4f, ch - 4f), CornerRadius(8f), style = Stroke(2.5.dp.toPx()))
        }
        // Kampfunfähige Einheiten
        t.units.filter { it.down }.forEach { u ->
            val c = Offset(u.x * cw + cw / 2, u.y * ch + ch / 2)
            val r = minOf(cw, ch) * 0.22f
            drawLine(Pn.Muted.copy(alpha = 0.7f), c - Offset(r, r), c + Offset(r, r), 2.dp.toPx())
            drawLine(Pn.Muted.copy(alpha = 0.7f), c + Offset(r, -r), c + Offset(-r, r), 2.dp.toPx())
        }
        // Einheiten und Trefferchancen
        t.units.filter { !it.down && !it.evacuated }.forEach { u ->
            val hidden = !u.ally && sel != null && !sel.vip && (u.x to u.y) !in visible
            drawUnit(u, Offset(u.x * cw, u.y * ch), cw, ch, sel?.id == u.id, hidden, measurer, pulse)
            if (!u.ally && !hidden && sel != null && sel.ap > 0 && !sel.vip && Tactical.attackBlocker(t, sel, u) == null) {
                val chance = Tactical.hitChance(t, sel, u)
                val col = when { chance >= 65 -> Pn.Ok; chance >= 40 -> Pn.Warn; else -> Pn.Danger }
                val label = measurer.measure("$chance%", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF071019)))
                val bw = label.size.width + 8.dp.toPx(); val bh = label.size.height + 2.dp.toPx()
                val bx = u.x * cw + (cw - bw) / 2; val by = u.y * ch + 1.dp.toPx()
                drawRoundRect(col, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
                drawText(label, topLeft = Offset(bx + 4.dp.toPx(), by + 1.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.drawTile(t: TacticalState, x: Int, y: Int, o: Offset, cw: Float, ch: Float, tint: Color, measurer: TextMeasurer, pulse: Float) {
    val tile = t.tile(x, y)
    val inset = 1.dp.toPx()
    val sz = Size(cw - inset * 2, ch - inset * 2)
    val tl = o + Offset(inset, inset)
    when (tile) {
        '#' -> {
            // Wandblock mit heller Oberseite und dunkler Front – klar vom Boden unterscheidbar
            drawRect(Color(0xFF04070D), o, Size(cw, ch))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF4A5F7C), Color(0xFF2A3A52)), startY = o.y, endY = o.y + ch * 0.62f), tl, Size(sz.width, sz.height * 0.62f))
            drawRect(Color(0xFF111A28), tl + Offset(0f, sz.height * 0.62f), Size(sz.width, sz.height * 0.38f))
            drawLine(Color(0x66C9DCF0), tl, tl + Offset(sz.width, 0f), 1.dp.toPx())
        }
        '~' -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B2740), Color(0xFF0E3355)), startY = o.y, endY = o.y + ch), o, Size(cw, ch))
            for (k in 1..2) {
                val wy = o.y + ch * k / 3f
                val p = Path().apply {
                    moveTo(o.x + cw * 0.15f, wy)
                    quadraticBezierTo(o.x + cw * 0.32f, wy - ch * 0.08f, o.x + cw * 0.5f, wy)
                    quadraticBezierTo(o.x + cw * 0.68f, wy + ch * 0.08f, o.x + cw * 0.85f, wy)
                }
                drawPath(p, Color(0x6694C8F0), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        else -> {
            val dark = Color(tint.red * 0.75f, tint.green * 0.75f, tint.blue * 0.8f, 1f)
            drawRoundRect(Brush.linearGradient(listOf(tint, dark), start = o, end = o + Offset(cw, ch)), tl, sz, CornerRadius(6f))
            drawRoundRect(Color(0x1FFFFFFF), tl, sz, CornerRadius(6f), style = Stroke(1f))
            when (tile) {
                'c' -> {
                    val b = Size(cw * 0.62f, ch * 0.5f); val bo = o + Offset((cw - b.width) / 2, ch * 0.3f)
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF557596), Color(0xFF2B3F57)), startY = bo.y, endY = bo.y + b.height), bo, b, CornerRadius(4f))
                    drawRoundRect(Color(0x66A9C6E6), bo, b, CornerRadius(4f), style = Stroke(1.dp.toPx()))
                    drawLine(Color(0x33000000), bo + Offset(0f, b.height * 0.5f), bo + Offset(b.width, b.height * 0.5f), 1.5.dp.toPx())
                }
                'w' -> {
                    drawRect(Color(0x553D6E8F), tl, sz)
                    drawLine(Color(0xAA94C8F0), o + Offset(cw * 0.1f, ch * 0.5f), o + Offset(cw * 0.9f, ch * 0.5f), 3.dp.toPx())
                    drawLine(Color(0x55FFFFFF), o + Offset(cw * 0.25f, ch * 0.75f), o + Offset(cw * 0.55f, ch * 0.25f), 1.dp.toPx())
                }
                'd' -> {
                    drawRect(Color(0xFF2A2116), o + Offset(cw * 0.15f, ch * 0.1f), Size(cw * 0.7f, ch * 0.8f))
                    drawRect(Color(0xAAB08A55), o + Offset(cw * 0.15f, ch * 0.1f), Size(cw * 0.7f, ch * 0.8f), style = Stroke(2.dp.toPx()))
                    drawCircle(Color(0xFFD9B27A), 1.8.dp.toPx(), o + Offset(cw * 0.7f, ch * 0.52f))
                }
                'X', 'I', 'R', 'Z' -> {
                    val secured = Tactical.key(x, y) in t.secured
                    val col = when (tile) { 'X' -> Pn.Accent; 'I' -> Pn.Warn; 'R' -> Pn.Violet; else -> Pn.Ok }
                    val c = o + Offset(cw / 2, ch / 2)
                    val r = minOf(cw, ch) * 0.36f
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.45f * pulse), Color.Transparent), center = c, radius = r * 1.4f), r * 1.4f, c)
                    drawCircle(col.copy(alpha = 0.9f), r, c, style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                    val label = measurer.measure(if (secured) "✓" else tile.toString(), TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = col))
                    drawText(label, topLeft = c - Offset(label.size.width / 2f, label.size.height / 2f))
                }
            }
        }
    }
}

private fun DrawScope.drawUnit(u: TacticalUnit, o: Offset, cw: Float, ch: Float, selected: Boolean, hidden: Boolean, measurer: TextMeasurer, pulse: Float) {
    val color = if (hidden) Pn.Muted else unitColor(u)
    val c = o + Offset(cw / 2, ch / 2 + ch * 0.04f)
    val r = minOf(cw, ch) * 0.36f
    if (selected) drawCircle(color.copy(alpha = 0.25f * pulse + 0.1f), r * 1.45f, c)
    drawCircle(Color(0x66000000), r * 1.02f, c + Offset(0f, 2.dp.toPx()))
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color(0xFF0A1424)), center = c, radius = r), r, c)
    val frac = (u.hp.toFloat() / u.maxHp).coerceIn(0f, 1f)
    val sw = 2.6.dp.toPx()
    drawArc(Color(0x55000000), 0f, 360f, false, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(sw))
    if (!hidden) drawArc(color, -90f, 360f * frac, false, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
    val glyph = when {
        hidden -> "?"
        u.isPlayer -> "★"
        u.vip -> "♦"
        u.ally -> u.name.take(1).uppercase()
        else -> when (u.role) { "guard" -> "W"; "overwatch" -> "Ü"; "flanker" -> "F"; "rusher" -> "S"; else -> "G" }
    }
    val text = measurer.measure(glyph, TextStyle(fontSize = (minOf(cw, ch) / density * 0.30f).sp, fontWeight = FontWeight.Black, color = Pn.Text))
    drawText(text, topLeft = c - Offset(text.size.width / 2f, text.size.height / 2f))
    if (u.guarding) drawCircle(Pn.Accent, r * 1.18f, c, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
    if (u.stunned > 0) {
        val z = measurer.measure("z", TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Pn.Warn))
        drawText(z, topLeft = o + Offset(2.dp.toPx(), 1.dp.toPx()))
    }
    if (u.ally && !u.vip && u.ap > 0) repeat(u.ap.coerceAtMost(4)) { i -> drawCircle(Pn.Accent, 2.dp.toPx(), o + Offset(cw / 2 - 4.dp.toPx() + i * 5.dp.toPx(), ch - 4.dp.toPx())) }
}

@Composable
private fun ActionPanel(t: TacticalState, sel: TacticalUnit, mode: String, onMode: (String) -> Unit, upd: ((TacticalState) -> TacticalState) -> Unit) {
    GlassPanel(padding = 12.dp, accent = unitColor(sel)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sel.name, style = MaterialTheme.typography.titleSmall)
                Text("AP ${sel.ap} · Treffer ${sel.accuracy} · Wirkung ${sel.damage}" + (if (sel.armor > 0) " · Schutz ${sel.armor}" else "") + if (sel.stunned > 0) " · setzt aus" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ProgressRing(sel.hp, sel.maxHp, 42.dp, if (sel.hp * 3 <= sel.maxHp) Pn.Danger else Pn.Ok, "${sel.hp}", stroke = 4.dp)
        }
        if (sel.vip) {
            InfoNote("Schutzperson: kann sich bewegen, aber nicht angreifen. Bring sie zum Ziel (X).", Pn.Warn)
        } else {
            val ab = Tactical.abilities[sel.ability]
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionButton("Überwältigen", Modifier.weight(1f), enabled = sel.ap >= 2 && !t.finished && sel.stunned == 0, secondary = mode != "takedown", subtitle = "2 AP, angrenzend") { onMode(if (mode == "takedown") "act" else "takedown") }
                ActionButton(ab?.name ?: "Fähigkeit", Modifier.weight(1f), enabled = ab != null && sel.abilityCooldown == 0 && sel.ap > 0 && !t.finished, secondary = mode != "ability",
                    subtitle = if (sel.abilityCooldown > 0) "bereit in ${sel.abilityCooldown}" else "1 AP") {
                    if (sel.ability in listOf("hack", "distract")) onMode(if (mode == "ability") "act" else "ability")
                    else upd { Tactical.useAbility(it, sel.id, null) }
                }
            }
            if (t.tile(sel.x, sel.y) == 'I' && Tactical.key(sel.x, sel.y) !in t.secured)
                ActionButton("Infopunkt sichern", Modifier.fillMaxWidth(), enabled = sel.ap > 0 && !t.finished, subtitle = "1 AP", icon = PnIcons.Chip) { upd { Tactical.secure(it, sel.id) } }
            val threat = t.units.filter { !it.ally && !it.down }.minByOrNull { Tactical.dist(it.x, it.y, sel.x, sel.y) }
            if (threat != null) {
                val cov = Tactical.cover(t, threat.x, threat.y, sel.x, sel.y)
                InfoNote("Deckung gegen ${threat.name}: " + when (cov) { 25 -> "voll (−25)"; 20 -> "teilweise (−20)"; else -> "keine" } +
                    if (Tactical.lineOfSight(t, threat.x, threat.y, sel.x, sel.y)) " · im Sichtfeld" else " · außer Sicht", if (cov == 0) Pn.Warn else Pn.Ok, PnIcons.Shield)
            }
            if (ab != null) InfoNote(ab.description)
        }
        val team = t.units.count { it.ally && !it.isPlayer && !it.vip && !it.down && !it.evacuated && it.ap > 0 }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!sel.vip) ActionButton("Sichern", Modifier.weight(1f), enabled = sel.ap > 0 && !t.finished, secondary = true, subtitle = "−15 gegn. Treffer") { upd { Tactical.guard(it, sel.id) } }
            if (team > 0) ActionButton("Team handelt", Modifier.weight(1f), enabled = !t.finished, secondary = true, subtitle = "$team mit AP", icon = PnIcons.Robot) { upd { Tactical.teamAuto(it) } }
        }
        ActionButton("Runde beenden", Modifier.fillMaxWidth(), enabled = !t.finished, icon = PnIcons.Hourglass, subtitle = "Gegner sind am Zug") { onMode("act"); upd { Tactical.endTurn(it) } }
    }
}

@Composable
private fun EnemyList(t: TacticalState, sel: TacticalUnit?, pending: Pair<Int, Int>?, onAim: (TacticalUnit) -> Unit) {
    val enemies = t.units.filter { !it.ally && !it.down }
    if (enemies.isEmpty()) return
    GlassPanel(accent = Pn.Danger, padding = 12.dp) {
        Overline("Gegner · antippen zum Zielen, erneut zum Angreifen", Pn.Danger)
        enemies.forEach { e ->
            val blocker = sel?.let { Tactical.attackBlocker(t, it, e) }
            val chance = if (sel != null && blocker == null) Tactical.hitChance(t, sel, e) else null
            val aimed = pending == (e.x to e.y)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (aimed) Pn.Warn.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(enabled = sel != null && !sel.vip && sel.ap > 0 && !t.finished, role = Role.Button) { onAim(e) }
                    .defaultMinSize(minHeight = 44.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${e.name} · ${Tactical.roleLabels[e.role] ?: "Gegner"}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    Text("${e.hp}/${e.maxHp} LP" + (if (e.stunned > 0) " · setzt aus" else "") + (if (e.hp * 3 <= e.maxHp) " · angeschlagen" else "") + (blocker?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (chance != null) ChanceRing(chance, 38.dp)
            }
        }
    }
}

@Composable
private fun Legend() {
    var open by remember { mutableStateOf(false) }
    GlassPanel(accent = Pn.Muted, padding = 12.dp, onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PnIcon(PnIcons.Info, Pn.Muted, 15.dp); Spacer(Modifier.width(6.dp))
            Text("Legende & Regeln", style = MaterialTheme.typography.labelLarge, color = Pn.Muted, modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = Pn.Muted)
        }
        if (open) {
            SmallNote("Abstrakte Simulation: Treffer bedeuten „kampfunfähig/markiert“. ★ du · Buchstabe Teammitglied · ♦ Schutzperson · W Wachposten · Ü Überwachung (reagiert einmal pro Runde auf Bewegung im Sichtfeld) · F Flankierer · S Stoßtrupp (überwältigt in Deckung).")
            SmallNote("Wände und Türen blockieren die Sicht, Wasser und Glas nur den Weg. Auf Deckung −25, hinter Deckung in Schussrichtung −20, Sichern −15. Rote Punkte auf erreichbaren Feldern = Anzahl Gegner mit Schussfeld. Abgedunkelte Felder liegen außerhalb der Sicht der gewählten Einheit.")
            SmallNote("„Team handelt“ lässt deine Teammitglieder nach festen Regeln agieren: gute Schüsse, Versorgung, Infopunkte, sonst Deckung mit Schussfeld.")
        }
    }
}

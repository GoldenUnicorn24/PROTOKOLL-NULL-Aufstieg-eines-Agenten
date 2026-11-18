package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.Missions
import de.protokollnull.agent.engine.Tactical
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.GameViewModel
import de.protokollnull.agent.ui.theme.*

/** Rundenbasiertes, abstraktes 2D-Taktikbrett. */
@Composable
fun TacticalScreen(vm: GameViewModel, s: GameState, st: StageDefinition, run: MissionRun) {
    val t = run.tactical ?: return
    var mode by remember { mutableStateOf("act") }
    val sel = t.units.firstOrNull { it.id == t.selectedUnit && !it.down }
    val reach = if (sel != null && sel.ap > 0 && mode == "act") Tactical.reachable(t, sel) else emptySet()

    fun upd(f: (TacticalState) -> TacticalState) = vm.act(silent = true) { d, x -> Missions.updateTactical(d, x, f) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(st.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Runde ${t.turn}/${t.maxTurns}")
            Pill(if (t.objective == "reach") "Ziel: X erreichen" else "Ziel: alle neutralisieren", Pn.Warn)
            if (mode != "act") Pill(if (mode == "takedown") "Überwältigen: Gegner wählen" else "Fähigkeit: Ziel wählen", Pn.Violet)
        }
        // Spielfeld
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - 2.dp * t.width) / t.width
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (y in 0 until t.height) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (x in 0 until t.width) {
                            val tile = t.tile(x, y)
                            val unit = Tactical.unitAt(t, x, y)
                            val downUnit = t.units.firstOrNull { it.down && it.x == x && it.y == y }
                            val inReach = (x to y) in reach
                            val isSel = sel != null && sel.x == x && sel.y == y
                            val bg = when (tile) {
                                '#' -> Brush.linearGradient(listOf(Color(0xFF080F1A), Color(0xFF0B1320)))
                                'c' -> Brush.linearGradient(listOf(Color(0xFF1B2B3D), Color(0xFF15233A)))
                                'X' -> Brush.radialGradient(listOf(Color(0x883FD6C4), Color(0x223FD6C4)))
                                else -> Brush.linearGradient(listOf(Color(0xFF15253A), Color(0xFF101C2D)))
                            }
                            Box(
                                Modifier.size(cell).clip(RoundedCornerShape(6.dp)).background(bg)
                                    .border(if (isSel) 2.dp else 1.dp, when { isSel -> Pn.Accent; inReach -> Pn.Accent.copy(alpha = 0.6f); else -> Color(0x14FFFFFF) }, RoundedCornerShape(6.dp))
                                    .clickable(enabled = !t.finished) {
                                        when {
                                            unit != null && unit.ally -> { mode = "act"; upd { Tactical.select(it, unit.id) } }
                                            unit != null && !unit.ally && sel != null -> {
                                                when (mode) {
                                                    "takedown" -> upd { Tactical.takedown(it, sel.id, unit.id) }
                                                    "ability" -> upd { Tactical.useAbility(it, sel.id, unit.id) }
                                                    else -> upd { Tactical.attack(it, sel.id, unit.id) }
                                                }
                                                mode = "act"
                                            }
                                            inReach && sel != null -> upd { Tactical.move(it, sel.id, x, y) }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (tile == '#') Canvas(Modifier.fillMaxSize()) {
                                    var k = -size.height
                                    while (k < size.width) { drawLine(Color(0x14FFFFFF), androidx.compose.ui.geometry.Offset(k, size.height), androidx.compose.ui.geometry.Offset(k + size.height, 0f), 2f); k += 10f }
                                }
                                if (tile == 'c') Box(Modifier.fillMaxSize(0.62f).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(listOf(Color(0xFF4A6684), Color(0xFF2B3F57)))).border(1.dp, Color(0x55A9C6E6), RoundedCornerShape(4.dp)))
                                if (tile == 'X') Text("X", color = Pn.Accent, fontWeight = FontWeight.Black)
                                if (inReach && unit == null) Box(Modifier.size(cell * 0.22f).clip(CircleShape).background(Pn.Accent.copy(alpha = 0.6f)))
                                if (downUnit != null && unit == null) Text("✕", color = Pn.Muted)
                                if (unit != null) UnitToken(unit, cell)
                            }
                        }
                    }
                }
            }
        }
        // Auswahl & Aktionen
        if (sel != null) {
            GlassPanel(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sel.name, style = MaterialTheme.typography.titleSmall)
                        Text("AP ${sel.ap} · Treffer ${sel.accuracy} · Wirkung ${sel.damage}" + if (sel.armor > 0) " · Schutz ${sel.armor}" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${sel.hp}/${sel.maxHp}", color = if (sel.hp <= sel.maxHp / 3) Pn.Danger else Pn.Ok, style = MaterialTheme.typography.labelLarge)
                }
                val ab = Tactical.abilities[sel.ability]
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("Überwältigen", Modifier.weight(1f), enabled = sel.ap >= 2 && !t.finished, secondary = mode != "takedown", subtitle = "2 AP, angrenzend") { mode = if (mode == "takedown") "act" else "takedown" }
                    ActionButton(ab?.name ?: "Fähigkeit", Modifier.weight(1f), enabled = ab != null && sel.abilityCooldown == 0 && sel.ap > 0 && !t.finished, secondary = mode != "ability",
                        subtitle = if (sel.abilityCooldown > 0) "bereit in ${sel.abilityCooldown}" else "1 AP") {
                        if (sel.ability in listOf("hack", "distract")) mode = if (mode == "ability") "act" else "ability"
                        else upd { Tactical.useAbility(it, sel.id, null) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("Sichern", Modifier.weight(1f), enabled = sel.ap > 0 && !t.finished, secondary = true, subtitle = "−15 gegn. Treffer") { upd { Tactical.guard(it, sel.id) } }
                    ActionButton("Runde beenden", Modifier.weight(1f), enabled = !t.finished) { mode = "act"; upd { Tactical.endTurn(it) } }
                }
                if (ab != null) SmallNote(ab.description)
            }
        }
        // Gegnerliste mit Trefferchancen
        if (sel != null) GlassPanel(accent = Pn.Danger, padding = 12.dp) {
            Text("GEGNER · Tippen zum Angreifen (1 AP, Reichweite ${Tactical.ATTACK_RANGE})", style = MaterialTheme.typography.labelSmall, color = Pn.Danger)
            t.units.filter { !it.ally && !it.down }.forEach { e ->
                val d = Tactical.dist(sel.x, sel.y, e.x, e.y)
                Text("${e.name}: ${e.hp}/${e.maxHp}" + (if (e.stunned > 0) " · setzt aus" else "") + " · Distanz $d · Treffer ${if (d <= Tactical.ATTACK_RANGE) Tactical.hitChance(t, sel, e).toString() + "%" else "—"}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        GlassPanel(accent = Pn.Muted, padding = 12.dp) {
            Text("GEFECHTSPROTOKOLL", style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
            t.log.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SmallNote("Abstrakte Simulation: Treffer bedeuten „kampfunfähig/markiert“. Deckung (graue Blöcke) senkt gegnerische Trefferchancen um 25.")
    }
}

@Composable
private fun UnitToken(u: TacticalUnit, cell: androidx.compose.ui.unit.Dp) {
    val color = when { u.isPlayer -> Pn.Accent; u.ally -> Pn.Ok; else -> Pn.Danger }
    val frac = (u.hp.toFloat() / u.maxHp).coerceIn(0f, 1f)
    Box(Modifier.size(cell * 0.86f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(color.copy(alpha = 0.22f))
            drawArc(Color(0x33000000), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawArc(color, -90f, 360f * frac, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        if (u.isPlayer) PnIcon(PnIcons.Star, color, cell * 0.38f)
        else if (u.ally) Text(u.name.take(1), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        else PnIcon(PnIcons.Target, color, cell * 0.38f)
        if (u.guarding) Text("⛨", color = Pn.Text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd))
        if (u.stunned > 0) Text("z", color = Pn.Warn, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopStart))
    }
}

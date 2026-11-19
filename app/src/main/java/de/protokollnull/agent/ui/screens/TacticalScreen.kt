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
import de.protokollnull.agent.engine.TacticalMaps
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.GameViewModel
import de.protokollnull.agent.ui.theme.*

/** Rundenbasiertes, abstraktes 2D-Taktikbrett. */
@Composable
fun TacticalScreen(vm: GameViewModel, s: GameState, st: StageDefinition, run: MissionRun) {
    val t = run.tactical ?: return
    var mode by remember { mutableStateOf("act") }
    val sel = t.units.firstOrNull { it.id == t.selectedUnit && !it.down }
    val reach = if (sel != null && sel.ap > 0 && mode == "act" && sel.stunned == 0) Tactical.reachable(t, sel) else emptySet()
    val visible = remember(t.units, t.selectedUnit) { if (sel != null && !sel.vip) Tactical.visibleTiles(t, sel) else emptySet() }

    fun upd(f: (TacticalState) -> TacticalState) = vm.act(silent = true) { d, x -> Missions.updateTactical(d, x, f) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ObjectivePanel(t)
        if (mode != "act") Pill(if (mode == "takedown") "Überwältigen: angrenzenden Gegner wählen" else "Fähigkeit: Ziel wählen", Pn.Violet)
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
                            val seen = sel == null || sel.vip || (x to y) in visible || tile == '#'
                            val bg = when (tile) {
                                '#' -> Brush.linearGradient(listOf(Color(0xFF080F1A), Color(0xFF0B1320)))
                                'c' -> Brush.linearGradient(listOf(Color(0xFF1B2B3D), Color(0xFF15233A)))
                                'X' -> Brush.radialGradient(listOf(Color(0x883FD6C4), Color(0x223FD6C4)))
                                '~' -> Brush.linearGradient(listOf(Color(0xFF0B2740), Color(0xFF0E3355)))
                                'w' -> Brush.linearGradient(listOf(Color(0xFF1C3347), Color(0xFF223D55)))
                                'd' -> Brush.linearGradient(listOf(Color(0xFF2A2116), Color(0xFF1E1910)))
                                'I' -> Brush.radialGradient(listOf(Color(0x66E8B04B), Color(0x22E8B04B)))
                                'R' -> Brush.radialGradient(listOf(Color(0x669B7BFF), Color(0x229B7BFF)))
                                'Z' -> Brush.radialGradient(listOf(Color(0x664ADE80), Color(0x224ADE80)))
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
                                if (tile == 'I' && unit == null) Text(if (Tactical.key(x, y) in t.secured) "✓" else "I", color = Pn.Warn, fontWeight = FontWeight.Black)
                                if (tile == 'R' && unit == null) Text(if (Tactical.key(x, y) in t.secured) "✓" else "R", color = Pn.Violet, fontWeight = FontWeight.Black)
                                if (tile == 'Z' && unit == null) Text("Z", color = Pn.Ok, fontWeight = FontWeight.Black)
                                if (tile == '~') Text("≈", color = Color(0x5594C8F0))
                                if (tile == 'w') Box(Modifier.fillMaxWidth(0.8f).height(3.dp).background(Color(0x8894C8F0)))
                                if (tile == 'd') Box(Modifier.fillMaxSize(0.5f).border(2.dp, Color(0xAAB08A55), RoundedCornerShape(2.dp)))
                                if (!seen) Box(Modifier.fillMaxSize().background(Color(0x99050A12)))
                                if (inReach && unit == null) Box(Modifier.size(cell * 0.22f).clip(CircleShape).background(Pn.Accent.copy(alpha = 0.6f)))
                                if (downUnit != null && unit == null) Text("✕", color = Pn.Muted)
                                if (unit != null) UnitToken(unit, cell, hidden = !unit.ally && !seen)
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
                if (t.tile(sel.x, sel.y) == 'I' && Tactical.key(sel.x, sel.y) !in t.secured && !sel.vip)
                    ActionButton("Infopunkt sichern", Modifier.fillMaxWidth(), enabled = sel.ap > 0 && !t.finished, subtitle = "1 AP") { upd { Tactical.secure(it, sel.id) } }
                val threat = t.units.filter { !it.ally && !it.down }.minByOrNull { Tactical.dist(it.x, it.y, sel.x, sel.y) }
                if (threat != null) {
                    val cov = Tactical.cover(t, threat.x, threat.y, sel.x, sel.y)
                    Text("Eigene Deckung gegen ${threat.name}: " + when (cov) { 25 -> "voll (−25)"; 20 -> "teilweise (−20)"; else -> "keine" } +
                        if (Tactical.lineOfSight(t, threat.x, threat.y, sel.x, sel.y)) " · im Sichtfeld" else " · außer Sicht",
                        style = MaterialTheme.typography.labelSmall, color = if (cov == 0) Pn.Warn else Pn.Ok)
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
            Text("GEGNER · Tippen zum Angreifen (1 AP, Sicht + Reichweite ${sel.range})", style = MaterialTheme.typography.labelSmall, color = Pn.Danger)
            t.units.filter { !it.ally && !it.down }.forEach { e ->
                val blocker = Tactical.attackBlocker(t, sel, e)
                Text("${e.name} (${Tactical.roleLabels[e.role] ?: "Gegner"}): ${e.hp}/${e.maxHp}" + (if (e.stunned > 0) " · setzt aus" else "") +
                    " · " + (blocker ?: "Treffer ${Tactical.hitChance(t, sel, e)}%"), style = MaterialTheme.typography.bodySmall, color = if (blocker == null) Pn.Text else Pn.Muted)
            }
        }
        GlassPanel(accent = Pn.Muted, padding = 12.dp) {
            Text("GEFECHTSPROTOKOLL", style = MaterialTheme.typography.labelSmall, color = Pn.Muted)
            t.log.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SmallNote("Abstrakte Simulation: Treffer bedeuten „kampfunfähig/markiert“. Wände und Türen blockieren die Sicht, Wasser und Glas nur den Weg. Auf Deckung −25, hinter Deckung in Schussrichtung −20. Abgedunkelte Felder liegen außerhalb der Sicht der gewählten Einheit.")
    }
}

@Composable
private fun ObjectivePanel(t: TacticalState) {
    GlassPanel(padding = 10.dp, accent = Pn.Warn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((t.mapName.ifBlank { "Einsatzbrett" } + (TacticalMaps.environments[t.environment]?.let { " · $it" } ?: "")).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = Pn.Warn, modifier = Modifier.weight(1f))
            Pill("Runde ${t.turn.coerceAtMost(t.maxTurns)}/${t.maxTurns}")
        }
        Text(Tactical.objectiveLabel(t.objective, t.requiredSecured, t.holdTurns, t.units.count { !it.ally }), style = MaterialTheme.typography.titleSmall)
        when (t.objective) {
            "intel", "recon" -> StatBar("Fortschritt", t.secured.size, t.requiredSecured.coerceAtLeast(1), Pn.Warn)
            "protect" -> StatBar("Gehalten", t.held, t.holdTurns.coerceAtLeast(1), Pn.Ok)
            "eliminate" -> StatBar("Neutralisiert", t.units.count { !it.ally && it.down }, t.units.count { !it.ally }.coerceAtLeast(1), Pn.Danger)
        }
        if (t.stalemate >= 2 && !t.finished) Text("Patt droht: ${Tactical.STALEMATE_ROUNDS - t.stalemate} Runden ohne Fortschritt bis zum Rückzug.", style = MaterialTheme.typography.labelSmall, color = Pn.Danger)
        if (t.finished) Text(if (t.won) "Einsatzziel erreicht" else "Einsatzziel verfehlt", style = MaterialTheme.typography.labelLarge, color = if (t.won) Pn.Ok else Pn.Danger)
    }
}

@Composable
private fun UnitToken(u: TacticalUnit, cell: androidx.compose.ui.unit.Dp, hidden: Boolean = false) {
    val color = when { u.vip -> Pn.Warn; u.isPlayer -> Pn.Accent; u.ally -> Pn.Ok; hidden -> Pn.Muted; else -> Pn.Danger }
    val frac = (u.hp.toFloat() / u.maxHp).coerceIn(0f, 1f)
    Box(Modifier.size(cell * 0.86f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(color.copy(alpha = 0.22f))
            drawArc(Color(0x33000000), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawArc(color, -90f, 360f * frac, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        if (u.isPlayer) PnIcon(PnIcons.Star, color, cell * 0.38f)
        else if (u.vip) Text("♦", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        else if (u.ally) Text(u.name.take(1), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        else PnIcon(PnIcons.Target, color, cell * 0.38f)
        if (!u.ally && u.role.isNotBlank()) Text(when (u.role) { "guard" -> "W"; "overwatch" -> "Ü"; "flanker" -> "F"; else -> "" }, color = Pn.Text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd))
        if (u.guarding) Text("⛨", color = Pn.Text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd))
        if (u.stunned > 0) Text("z", color = Pn.Warn, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopStart))
    }
}

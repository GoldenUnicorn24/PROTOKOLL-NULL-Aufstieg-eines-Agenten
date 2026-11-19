package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

/** Ein HQ-Ereignis mit Entscheidungen und echten Folgen (v0.6). */
@Composable
fun HqEventScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val e = HqEvents.pending(data, s)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("HQ-Ereignis", e?.let { HqEvents.fill(data, s, it.title, it.npcId) }, onBack = { vm.back() })
        if (e == null) {
            Column(Modifier.padding(16.dp)) { SmallNote("Kein Ereignis offen.") }
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val people = (listOfNotNull(e.npcId) + e.involved).distinct()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                people.forEach { NpcPortrait(data, s, it, 64.dp) }
                Column(Modifier.weight(1f)) {
                    people.forEach { Text(Relations.displayName(data, s, it), style = MaterialTheme.typography.titleSmall) }
                    Text(Clock.dateLabel(s.clock.day) + " · " + Clock.slotNames[s.clock.slot], style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
                }
            }
            GlassPanel(accent = Pn.Warn) { Text(HqEvents.fill(data, s, e.text, e.npcId), style = MaterialTheme.typography.bodyLarge) }
            SmallNote("Entscheidungen wirken sich auf Beziehungen, Laufbahnen und das Leben in der Division aus. Ohne Reaktion verfällt das Ereignis nach ${HqEvents.EXPIRE_DAYS} Tagen" +
                if (e.choices.any { it.id == "ignore" }) " – mit Folgen." else ".")
            HqEvents.visibleChoices(data, s, e).forEach { c ->
                ChoiceButton(HqEvents.fill(data, s, c.text, e.npcId), consequenceHint(c), chance = c.check?.let { Stats.checkChance(data, s, it) }) {
                    vm.back()
                    vm.act { d, st -> HqEvents.resolve(d, st, c.id) }
                }
            }
        }
    }
}

private fun consequenceHint(c: HqChoice): String {
    val parts = mutableListOf<String>()
    if (c.check != null) parts += "Probe: " + Attr.label(c.check.attribute)
    if (c.life.any { it.status != null }) parts += "verändert den Dienstplan"
    if (c.conflicts.isNotEmpty()) parts += "riskiert einen Konflikt"
    if (c.resolveConflicts.isNotEmpty()) parts += "kann einen Konflikt lösen"
    if (c.life.any { it.bondWith != null }) parts += "beeinflusst Beziehungen untereinander"
    if (c.arcs.isNotEmpty()) parts += "hat Folgen im Hintergrund"
    return parts.joinToString(" · ").ifBlank { "1 Zeitslot" }
}

/** Kompakte Übersicht für das HQ: offenes Ereignis und Anwesenheit. */
@Composable
fun LivingHqCards(vm: GameViewModel, s: GameState) {
    val data = vm.data
    val e = HqEvents.pending(data, s)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (e != null) GlassPanel(accent = Pn.Warn, onClick = { vm.open(Overlay.HqEvent) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(Pn.Warn, 6.dp); Spacer(Modifier.width(6.dp))
                Text("HQ-EREIGNIS", style = MaterialTheme.typography.labelLarge, color = Pn.Warn)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                e.npcId?.let { NpcPortrait(data, s, it, 44.dp) }
                Column(Modifier.weight(1f)) {
                    Text(HqEvents.fill(data, s, e.title, e.npcId), style = MaterialTheme.typography.titleMedium)
                    val left = HqEvents.EXPIRE_DAYS - (s.clock.day - s.living.pendingHqEventDay)
                    Text("Reagieren · verfällt in $left Tag" + if (left == 1) "" else "en", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (s.living.lives.isNotEmpty()) GlassPanel(onClick = { vm.setTab(3) }) {
            Text("HEUTE IN DER DIVISION", style = MaterialTheme.typography.labelSmall, color = Pn.Accent)
            PresenceStrip(vm, s)
            val latest = s.living.feed.lastOrNull { World.revealed(data, s, it.npcId) && it.type != "mission_start" }
            if (latest != null) Text(latest.text, style = MaterialTheme.typography.bodySmall, color = feedColor(latest.type))
        }
    }
}

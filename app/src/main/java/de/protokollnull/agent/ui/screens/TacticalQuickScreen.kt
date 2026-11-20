package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.Missions
import de.protokollnull.agent.engine.TacticalQuick
import de.protokollnull.agent.engine.TacticalUX
import de.protokollnull.agent.model.GameState
import de.protokollnull.agent.model.MissionRun
import de.protokollnull.agent.ui.GameViewModel
import de.protokollnull.agent.ui.theme.*

/**
 * Standardansicht: Entscheidungen statt Raster, AP, Feld-Klicks oder Rundenende.
 * Jede Aktion aktualisiert denselben gespeicherten Einsatz wie der Kartenmodus.
 */
@Composable
fun TacticalQuickScreen(vm: GameViewModel, s: GameState, run: MissionRun) {
    val tactical = run.tactical ?: return
    val me = tactical.units.firstOrNull { it.isPlayer }
    val friends = tactical.units.count { it.ally && !it.isPlayer && !it.vip && !it.down }
    val enemies = tactical.units.count { !it.ally && !it.down }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassPanel(accent = Pn.Accent) {
            Overline("EINSATZZIEL", Pn.Accent)
            Text(TacticalUX.objective(tactical), style = MaterialTheme.typography.titleMedium)
            Text(TacticalQuick.status(tactical),
                style = MaterialTheme.typography.bodyMedium, color = Pn.Ok)
        }
        GlassPanel(padding = 12.dp) {
            Text("Lage", style = MaterialTheme.typography.titleSmall)
            KeyValue("Gesundheit", "${me?.hp ?: 0}/${me?.maxHp ?: 0}")
            KeyValue("Begleiter", friends.toString())
            KeyValue("Gegner", enemies.toString())
            KeyValue("Schritt", "${tactical.turn}/${tactical.maxTurns}")
        }
        GlassPanel(accent = Pn.Accent, padding = 12.dp) {
            Text("Aktion wählen", style = MaterialTheme.typography.titleSmall)
            TacticalQuick.choices(tactical).forEach { choice ->
                ActionButton(
                    choice.label, Modifier.fillMaxWidth(),
                    enabled = choice.enabled,
                    secondary = choice.id == "recover"
                ) {
                    vm.act(silent = true) { data, state ->
                        Missions.updateTactical(data, state) { TacticalQuick.act(it, choice.id) }
                    }
                }
            }
        }
        tactical.log.takeLast(2).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Pn.Muted)
        }
    }
}

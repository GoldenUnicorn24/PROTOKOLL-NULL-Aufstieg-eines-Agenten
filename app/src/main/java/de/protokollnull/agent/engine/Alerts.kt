package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.ContractStatus
import de.protokollnull.agent.model.GameState

/**
 * v1.0: Hinweise für die Hauptnavigation (Plaketten). Rein lesend – zeigt nur an,
 * wo eine Entscheidung oder ein neuer Inhalt tatsächlich wartet.
 */
object Alerts {
    data class Nav(
        val hq: Int = 0,
        val missions: Int = 0,
        val contacts: Int = 0,
        val profile: Int = 0,
        val hqReasons: List<String> = emptyList()
    )

    fun nav(data: GameData, s: GameState): Nav {
        if (s.phase != "game") return Nav()
        val hqReasons = buildList {
            if (Dialogues.pendingStory(data, s) != null) add("Eingehende Nachricht")
            if (HqEvents.pending(data, s) != null) add("HQ-Ereignis")
            if (s.life.pendingEvent != null) add("Alltagsereignis")
            if (s.lead.pendingDilemma != null) add("Führungsentscheidung")
        }
        val readyStory = data.missions.count { it.id !in s.completedMissions && Missions.lockReason(data, s, it) == null }
        val urgent = s.ops.contracts.count { it.urgency >= 3 && it.status == ContractStatus.OPEN }
        val talks = Life.ids(data, s).count { id ->
            World.revealed(data, s, id) && Dialogues.available(data, s, id).isNotEmpty()
        }
        val profile = (if (s.player.skillPoints > 0) 1 else 0) + (if (Progression.examReady(data, s)) 1 else 0)
        return Nav(hq = hqReasons.size, missions = readyStory + urgent, contacts = talks, profile = profile, hqReasons = hqReasons)
    }
}

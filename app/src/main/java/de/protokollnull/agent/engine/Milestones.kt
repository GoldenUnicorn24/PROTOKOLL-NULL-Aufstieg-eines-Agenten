package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.GameState

/**
 * v1.0: Erkennt bedeutsame Fortschritte zwischen zwei Zuständen, damit die Oberfläche
 * sie genau einmal würdigen kann (Beförderung, Kapitelabschluss, Levelaufstieg).
 * Rein funktional – erzeugt keine Belohnungen und verändert keinen Spielzustand.
 */
object Milestones {
    data class Milestone(val kind: String, val title: String, val subtitle: String)

    val chapterFlags = linkedMapOf(
        "ch1_done" to "Kapitel 1 · Neues Blut",
        "ch2_done" to "Kapitel 2 · Protokoll Null",
        "ch3_done" to "Kapitel 3 · Schattenrat",
        "ch4_done" to "Kapitel 4 · Nachbeben"
    )

    fun detect(data: GameData, before: GameState, after: GameState): Milestone? {
        if (after.phase != "game") return null
        val pb = before.player; val pa = after.player
        if (pa.rank > pb.rank) {
            return Milestone("rank", "Beförderung", "Neuer Dienstgrad: ${data.rank(pa.rank).name} (Rang ${pa.rank}/12)")
        }
        chapterFlags.entries.firstOrNull { (f, _) -> f in after.flags && f !in before.flags }?.let { (_, name) ->
            return Milestone("chapter", "Kapitel abgeschlossen", name)
        }
        if (pa.level > pb.level) {
            val gained = (pa.skillPoints - pb.skillPoints).coerceAtLeast(0)
            return Milestone("level", "Level ${pa.level}", if (gained > 0) "+$gained Fähigkeitspunkt${if (gained > 1) "e" else ""} – im Skilltree einsetzen" else "Deine Erfahrung wächst.")
        }
        return null
    }
}

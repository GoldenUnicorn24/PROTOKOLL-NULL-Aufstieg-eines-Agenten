package de.protokollnull.agent.engine

import de.protokollnull.agent.model.TacticalState
import de.protokollnull.agent.model.TacticalUnit

/**
 * Verständliche, von Compose unabhängige Vorschau für genau EINE taktische Aktion.
 * Eine Vorschau führt niemals eine Aktion aus; der Spieler bestätigt sie ausdrücklich.
 */
data class TacticalActionPreview(
    val action: String,
    val label: String,
    val detail: String,
    val allowed: Boolean,
    val targetId: String? = null
)

object TacticalUX {
    fun objective(t: TacticalState): String = when (t.objective) {
        "eliminate" -> "Besiege die verbleibenden Gegner (" + t.units.count { !it.ally && !it.down } + ")."
        "reach" -> "Bewege eine eigene Figur auf das mit X markierte Zielfeld."
        "extract", "escort" -> "Wähle die Schutzperson (♦) aus und bewege sie auf X. Halte sie am Leben."
        "intel" -> "Gehe auf die gelben Infopunkte (I) und tippe auf „Infopunkt sichern“ (" +
            t.secured.size + "/" + t.requiredSecured + ")."
        "recon" -> "Bewege eine Figur auf die Aufklärungspunkte (R) (" +
            t.secured.size + "/" + t.requiredSecured + ")."
        "rendezvous" -> "Bewege deine Hauptfigur (★) zum Treffpunkt Z."
        "protect" -> "Stelle eine Figur auf Z und beende die Runde, bis " +
            t.held + "/" + t.holdTurns + " Runden gehalten sind."
        else -> "Wähle eine eigene Figur und folge dem markierten Missionsziel."
    }

    fun preview(t: TacticalState, selected: TacticalUnit?, x: Int, y: Int, mode: String = "act"): TacticalActionPreview {
        fun unavailable(action: String, title: String, reason: String) =
            TacticalActionPreview(action, title, reason, false)
        if (t.finished) return unavailable("none", "Einsatz beendet", "Die Mission ist bereits abgeschlossen.")
        if (selected == null || selected.down || selected.evacuated)
            return unavailable("none", "Figur auswählen", "Wähle oben eine einsatzfähige eigene Figur aus.")
        if (x !in 0 until t.width || y !in 0 until t.height)
            return unavailable("none", "Ungültiges Feld", "Das Ziel liegt außerhalb der Karte.")

        val target = Tactical.unitAt(t, x, y)
        if (target?.ally == true)
            return unavailable("none", "Teammitglied", "Tippe die Figur an, um sie auszuwählen.")

        val action = when {
            mode == "takedown" -> "takedown"
            mode == "ability" -> "ability"
            target != null -> "attack"
            else -> "move"
        }
        val title = when (action) {
            "move" -> "Bewegen (1 AP)"
            "attack" -> "Angreifen (1 AP)"
            "takedown" -> "Überwältigen (2 AP)"
            else -> "Fähigkeit einsetzen (1 AP)"
        }

        if (selected.stunned > 0) return unavailable(action, title, "Diese Figur setzt derzeit aus.")
        if (selected.ap == 0) return unavailable(action, title, "Keine Aktionspunkte (AP) mehr. Wähle eine andere Figur oder beende die Runde.")
        if (action != "move" && selected.vip) return unavailable(action, title, "Eine Schutzperson kann nicht angreifen.")
        if (action != "move" && target == null) return unavailable(action, title, "Wähle einen Gegner als Ziel.")

        if (action == "move") {
            if (mode != "act") return unavailable(action, title, "Deaktiviere zuerst die Sonderaktion.")
            if (!Tactical.passable(t.tile(x, y))) return unavailable(action, title, "Das Feld ist nicht begehbar (Wand oder Hindernis).")
            if ((x to y) !in Tactical.reachable(t, selected))
                return unavailable(action, title, "Feld nicht erreichbar. Wähle ein türkis markiertes Feld.")
            val danger = Tactical.dangerAt(t, x, y)
            return TacticalActionPreview(action, title,
                "Deine Figur bewegt sich hierher." + if (danger > 0) " Achtung: Im Sichtfeld von " + danger + " Gegner(n)!" else " Sicherer Weg.",
                true)
        }

        val enemy = target!!
        if (action == "attack") {
            val reason = Tactical.attackBlocker(t, selected, enemy)
            if (reason != null) return unavailable(action, title, "Angriff nicht möglich: " + reason + ".")
            return TacticalActionPreview(action, title,
                enemy.name + " · " + Tactical.hitChance(t, selected, enemy) + "% Trefferchance · 1 AP",
                true, enemy.id)
        }

        if (action == "takedown") {
            if (selected.ap < 2) return unavailable(action, title, "Du brauchst 2 Aktionspunkte.")
            if (Tactical.dist(selected.x, selected.y, x, y) != 1)
                return unavailable(action, title, "Überwältigen funktioniert nur auf direkt benachbarten Feldern.")
            return TacticalActionPreview(action, title, enemy.name + " aus nächster Nähe überwältigen · 2 AP", true, enemy.id)
        }

        if (selected.ability !in setOf("hack", "distract"))
            return unavailable(action, title, "Diese Fähigkeit braucht kein Ziel. Nutze den Fähigkeiten-Button.")
        if (selected.abilityCooldown > 0)
            return unavailable(action, title, "Fähigkeit erst in " + selected.abilityCooldown + " Runden wieder bereit.")
        if (selected.ability == "distract" &&
            (Tactical.dist(selected.x, selected.y, x, y) > 6 || !Tactical.canSee(t, selected, enemy)))
            return unavailable(action, title, "Ablenken benötigt Sichtkontakt und höchstens 6 Felder Entfernung.")
        return TacticalActionPreview(action, title, "Spezialfähigkeit auf " + enemy.name + " anwenden · 1 AP", true, enemy.id)
    }
}

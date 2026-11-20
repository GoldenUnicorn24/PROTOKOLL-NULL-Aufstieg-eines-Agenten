package de.protokollnull.agent.engine

import de.protokollnull.agent.model.TacticalState
import de.protokollnull.agent.model.TacticalUnit

/**
 * Standard-Einsatzmodus: Entscheidungen statt winziger Rasterfelder, AP und manueller Wege.
 * Nutzt denselben TacticalState wie der optionale Kartenmodus, daher kein zweiter
 * Spielstand und kein Überspringen der Missions-Abrechnung.
 */
object TacticalQuick {
    data class Choice(val id: String, val label: String, val enabled: Boolean = true)

    private fun liveEnemies(t: TacticalState) = t.units.filter { !it.ally && !it.down }
    private fun mainCharacter(t: TacticalState) = t.units.firstOrNull { it.isPlayer }

    fun status(t: TacticalState): String {
        val n = liveEnemies(t).size
        return when (t.objective) {
            "eliminate" -> if (n > 0) "$n Gegner verbleiben" else "Widerstand überwunden"
            "reach", "rendezvous" -> if (t.quickProgress >= 2) "Ziel in Reichweite" else "Annäherung ${t.quickProgress}/2"
            "extract", "escort" -> if (t.quickProgress >= 2) "Ausgang erreicht" else "Evakuierungsweg ${t.quickProgress}/2"
            "intel" -> "Daten ${t.secured.size}/${t.requiredSecured.coerceAtLeast(1)}"
            "recon" -> "Aufklärung ${t.secured.size}/${t.requiredSecured.coerceAtLeast(1)}"
            "protect" -> "Position ${t.held}/${t.holdTurns.coerceAtLeast(1)}"
            else -> "Einsatz läuft"
        }
    }

    fun choices(t: TacticalState): List<Choice> {
        if (t.finished) return emptyList()
        val enemies = liveEnemies(t)
        val options = mutableListOf<Choice>()
        when (t.objective) {
            "eliminate" -> options += Choice("combat", "Gegner überwinden", enemies.isNotEmpty())
            "protect" -> options += Choice("secure", "Position halten")
            else -> {
                if (t.quickProgress < 2) {
                    options += Choice("advance", "Vorrücken")
                } else {
                    options += Choice("secure", when (t.objective) {
                        "extract", "escort" -> "Schutzperson evakuieren"
                        "reach", "rendezvous" -> "Ziel erreichen"
                        "intel" -> "Daten sichern"
                        "recon" -> "Bereich aufklären"
                        else -> "Ziel abschließen"
                    })
                }
                if (enemies.isNotEmpty()) options += Choice("combat", "Gegner überwinden")
            }
        }
        if (mainCharacter(t)?.hp?.let { it < (mainCharacter(t)?.maxHp ?: 0) } == true) {
            options += Choice("recover", "Deckung & Erholung")
        }
        return options
    }

    /**
     * Jede Auswahl bedeutet einen vollständigen, automatisch abgewickelten Schritt.
     * Keine AP, keine Wegfindung, keine Rundenende-Taste nötig.
     */
    fun act(t: TacticalState, id: String): TacticalState {
        if (t.finished || choices(t).none { it.id == id && it.enabled }) return t
        var n = t.copy(maxTurns = maxOf(t.maxTurns, 12 + liveEnemies(t).size * 2))
        val player = mainCharacter(n) ?: return t
        val teamCount = n.units.count { it.ally && !it.isPlayer && !it.vip && !it.down && !it.evacuated }
        val messages = mutableListOf<String>()

        when (id) {
            "advance" -> {
                n = n.copy(quickProgress = (n.quickProgress + 1).coerceAtMost(2))
                messages += if (n.quickProgress >= 2) "Zielbereich erreicht." else "Du rückst mit deinem Team vor."
            }
            "combat" -> {
                val enemy = liveEnemies(n).minByOrNull { it.hp } ?: return t
                val damage = 4 + (teamCount * 2).coerceAtMost(6) + (player.accuracy / 30).coerceIn(0, 3)
                val hp = (enemy.hp - damage).coerceAtLeast(0)
                n = n.copy(units = n.units.map { if (it.id == enemy.id) it.copy(hp = hp) else it })
                messages += if (hp == 0) "${enemy.name} überwunden." else "${enemy.name}: $hp LP verbleiben."
            }
            "secure" -> {
                when (n.objective) {
                    "protect" -> {
                        n = n.copy(held = n.held + 1)
                        messages += "Position gehalten (${n.held}/${n.holdTurns})."
                    }
                    "intel", "recon" -> {
                        val symbol = if (n.objective == "intel") 'I' else 'R'
                        val point = (0 until n.height).flatMap { y ->
                            (0 until n.width).mapNotNull { x -> (x to y).takeIf { n.tile(x, y) == symbol } }
                        }.firstOrNull { Tactical.key(it.first, it.second) !in n.secured }
                        val key = point?.let { Tactical.key(it.first, it.second) } ?: "quick-${n.secured.size}"
                        n = n.copy(secured = n.secured + key)
                        messages += if (n.objective == "intel") "Daten gesichert." else "Bereich aufgeklärt."
                    }
                    "extract", "escort" -> {
                        n = n.copy(units = n.units.map { if (it.vip) it.copy(evacuated = true) else it })
                        messages += "Schutzperson evakuiert."
                    }
                    "reach", "rendezvous" -> {
                        val symbol = if (n.objective == "reach") 'X' else 'Z'
                        val point = (0 until n.height).flatMap { y ->
                            (0 until n.width).mapNotNull { x -> (x to y).takeIf { n.tile(x, y) == symbol } }
                        }.firstOrNull()
                        if (point != null) {
                            n = n.copy(units = n.units.map {
                                if (it.isPlayer) it.copy(x = point.first, y = point.second) else it
                            })
                        }
                        messages += "Zielpunkt erreicht."
                    }
                }
            }
            "recover" -> {
                n = n.copy(units = n.units.map {
                    if (it.isPlayer) it.copy(hp = (it.hp + 2).coerceAtMost(it.maxHp)) else it
                })
                messages += "Du erholst dich in Deckung (+2 LP)."
            }
        }

        // Der einfache Modus besitzt eine eigene verständliche Rundenabwicklung.
        // Gegner verursachen gelegentlich leichten Druck; keine Feld-Fallen oder AP-Sperren.
        if (liveEnemies(n).isNotEmpty() && id != "recover" && n.turn % 3 == 0) {
            n = n.copy(units = n.units.map {
                if (it.isPlayer) it.copy(hp = (it.hp - 1).coerceAtLeast(0)) else it
            })
            messages += "Gegnerischer Druck: −1 LP."
        }
        n = n.copy(
            turn = n.turn + 1,
            stalemate = 0,
            log = (n.log + messages).takeLast(30)
        )
        n = Tactical.checkEnd(n)
        // Der einfache Modus erhält das Zeitlimit, aber keine Patt-/Rastersperre.
        if (!n.finished && n.turn > n.maxTurns) {
            n = n.copy(finished = true, won = false,
                log = (n.log + "Zeitfenster verstrichen.").takeLast(30))
        }
        return n
    }
}

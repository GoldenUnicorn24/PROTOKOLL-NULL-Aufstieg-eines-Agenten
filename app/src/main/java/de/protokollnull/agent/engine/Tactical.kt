package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*
import kotlin.math.abs

/**
 * Abstraktes, rundenbasiertes 2D-Taktikbrett. Bewusst ohne reale
 * Waffen-/Taktikanleitungen: Treffer bedeuten "kampfunfähig/markiert".
 */
object Tactical {
    const val MOVE_RANGE = 3
    const val ATTACK_RANGE = 6

    data class Ability(val id: String, val name: String, val description: String)

    val abilities = mapOf(
        "medic" to Ability("medic", "Erste Hilfe", "Heilt eine benachbarte Einheit um 4"),
        "hack" to Ability("hack", "Störsender", "Ein Gegner verliert seinen nächsten Zug"),
        "distract" to Ability("distract", "Ablenkung", "Gegner in Reichweite 6 verliert seinen nächsten Zug"),
        "focus" to Ability("focus", "Koordiniertes Vorgehen", "Alle Verbündeten +15 Treffer in diesem Zug"),
        "shield" to Ability("shield", "Sicherung", "Benachbarte Verbündete gehen in Deckung"),
        "command" to Ability("command", "Teambefehl", "Alle Verbündeten erhalten +1 AP (Teamleiter)")
    )

    fun passable(c: Char) = c != '#'
    fun dist(ax: Int, ay: Int, bx: Int, by: Int) = abs(ax - bx) + abs(ay - by)

    fun setup(data: GameData, state: GameState, setup: TacticalSetup, team: List<String>, seed: Long, threat: Int): TacticalState {
        val rows = setup.map
        val h = rows.size
        val w = rows.maxOf { it.length }
        val tiles = rows.map { r -> r.padEnd(w, '#').map { c -> if (c == 'P' || c == 'E') '.' else c }.joinToString("") }
        val pSpawns = mutableListOf<Pair<Int, Int>>()
        val eSpawns = mutableListOf<Pair<Int, Int>>()
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, c -> if (c == 'P') pSpawns += x to y; if (c == 'E') eSpawns += x to y } }
        val p = state.player
        val fit = Stats.effectiveAttribute(data, state, Attr.FITNESS)
        val prec = Stats.effectiveAttribute(data, state, Attr.PRECISION)
        val gear = Stats.equipped(data, p)
        val armor = gear.sumOf { it.armor }
        val weaponDmg = gear.sumOf { it.damage }
        val units = mutableListOf<TacticalUnit>()
        val maxHp = 8 + fit / 10 + (p.skills["resilience"] ?: 0)
        val hpScaled = (maxHp * (p.health.coerceAtLeast(30)) / 100).coerceAtLeast(4)
        val spawn0 = pSpawns.getOrElse(0) { 0 to h - 1 }
        units += TacticalUnit(
            id = "player", name = p.codename, ally = true, x = spawn0.first, y = spawn0.second,
            hp = hpScaled, maxHp = maxHp, accuracy = 45 + prec / 2 + (p.skills["weapon_handling"] ?: 0) * 4,
            damage = 3 + weaponDmg, armor = armor, isPlayer = true,
            ability = if (p.rank >= 8) "command" else when {
                (p.skills["first_aid"] ?: 0) > 0 -> "medic"
                (p.skills["electronics"] ?: 0) > 0 -> "hack"
                else -> "distract"
            }
        )
        team.forEachIndexed { i, id ->
            val sp = pSpawns.getOrElse(i + 1) { (spawn0.first + i + 1).coerceAtMost(w - 1) to spawn0.second }
            val def = data.agentById[id]
            val combat = def?.combat ?: (state.customNpcs.firstOrNull { it.id == id }?.skills?.values?.sum()?.times(6)?.plus(35) ?: 45)
            val loyalty = Relations.get(data, state, id).loyalty
            units += TacticalUnit(
                id = id, name = Relations.displayName(data, state, id), ally = true, x = sp.first, y = sp.second,
                hp = 8 + combat / 20, maxHp = 8 + combat / 20, accuracy = 40 + combat / 3 + loyalty / 10, damage = 3,
                ability = def?.teamAbility ?: when (state.customNpcs.firstOrNull { it.id == id }?.profession) {
                    "medic" -> "medic"; "tech" -> "hack"; "intel" -> "distract"; else -> "focus"
                }
            )
        }
        val enemyCount = eSpawns.size
        eSpawns.forEachIndexed { i, (x, y) ->
            units += TacticalUnit(
                id = "e$i", name = setup.enemyName + " ${i + 1}", ally = false, x = x, y = y,
                hp = setup.enemyHp + (threat - 1), maxHp = setup.enemyHp + (threat - 1),
                accuracy = setup.enemyAccuracy + threat * 3, damage = 2 + threat / 2, armor = if (threat >= 4) 1 else 0
            )
        }
        return TacticalState(
            width = w, height = h, tiles = tiles, units = units, maxTurns = setup.maxTurns,
            objective = setup.objective, selectedUnit = "player", seed = seed, enemyName = setup.enemyName,
            nonLethal = setup.nonLethal,
            log = listOf("Einsatzbrett: ${if (setup.objective == "reach") "Erreiche das Ziel (X)" else "Setze alle $enemyCount Gegner außer Gefecht"} – max. ${setup.maxTurns} Runden.")
        )
    }

    private fun rng(t: TacticalState) = Rng(Rng.mix(t.seed, t.rolls.toLong(), t.turn.toLong()))

    fun unitAt(t: TacticalState, x: Int, y: Int) = t.units.firstOrNull { !it.down && it.x == x && it.y == y }

    /** Erreichbare Felder (BFS) für eine Einheit. */
    fun reachable(t: TacticalState, u: TacticalUnit, range: Int = MOVE_RANGE): Set<Pair<Int, Int>> {
        val seen = mutableMapOf((u.x to u.y) to 0)
        val queue = ArrayDeque(listOf(u.x to u.y))
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            val d = seen.getValue(x to y)
            if (d >= range) continue
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until t.width || ny !in 0 until t.height) continue
                if (!passable(t.tile(nx, ny))) continue
                if (unitAt(t, nx, ny) != null) continue
                if ((nx to ny) in seen) continue
                seen[nx to ny] = d + 1
                queue.addLast(nx to ny)
            }
        }
        return seen.keys - (u.x to u.y)
    }

    fun hitChance(t: TacticalState, a: TacticalUnit, target: TacticalUnit): Int {
        val d = dist(a.x, a.y, target.x, target.y)
        var c = a.accuracy - 4 * (d - 2).coerceAtLeast(0)
        if (t.tile(target.x, target.y) == 'c') c -= 25
        if (target.guarding) c -= 15
        if (a.ally && t.focusOrder) c += 15
        return c.coerceIn(5, 95)
    }

    private fun replace(t: TacticalState, u: TacticalUnit) = t.copy(units = t.units.map { if (it.id == u.id) u else it })
    private fun log(t: TacticalState, line: String) = t.copy(log = (t.log + line).takeLast(30))

    fun select(t: TacticalState, id: String): TacticalState {
        val u = t.units.firstOrNull { it.id == id && it.ally && !it.down } ?: return t
        return t.copy(selectedUnit = u.id)
    }

    fun move(t: TacticalState, unitId: String, x: Int, y: Int): TacticalState {
        if (t.finished) return t
        val u = t.units.firstOrNull { it.id == unitId } ?: return t
        if (!u.ally || u.down || u.ap <= 0 || u.stunned > 0) return t
        if ((x to y) !in reachable(t, u)) return log(t, "Feld nicht erreichbar.")
        var n = replace(t, u.copy(x = x, y = y, ap = u.ap - 1, guarding = false))
        n = log(n, "${u.name} bewegt sich.")
        return checkEnd(n)
    }

    fun attack(t: TacticalState, unitId: String, targetId: String): TacticalState {
        if (t.finished) return t
        val a = t.units.firstOrNull { it.id == unitId } ?: return t
        val target = t.units.firstOrNull { it.id == targetId && !it.ally && !it.down } ?: return t
        if (!a.ally || a.down || a.ap <= 0 || a.stunned > 0) return t
        val d = dist(a.x, a.y, target.x, target.y)
        if (d > ATTACK_RANGE) return log(t, "Ziel außer Reichweite ($d > $ATTACK_RANGE).")
        val chance = hitChance(t, a, target)
        val r = rng(t)
        val roll = r.percent()
        var n = t.copy(rolls = t.rolls + 1)
        n = replace(n, a.copy(ap = a.ap - 1, guarding = false))
        n = if (roll <= chance) {
            val dmg = (a.damage + r.range(-1, 1) - target.armor).coerceAtLeast(1)
            val hp = target.hp - dmg
            n = replace(n, target.copy(hp = hp))
            log(n, "${a.name} trifft ${target.name} (−$dmg)" + if (hp <= 0) " – ${if (t.nonLethal) "kampfunfähig" else "ausgeschaltet"}." else ".")
        } else log(n, "${a.name} verfehlt ${target.name} ($roll/$chance).")
        return checkEnd(n)
    }

    fun takedown(t: TacticalState, unitId: String, targetId: String): TacticalState {
        if (t.finished) return t
        val a = t.units.firstOrNull { it.id == unitId } ?: return t
        val target = t.units.firstOrNull { it.id == targetId && !it.ally && !it.down } ?: return t
        if (!a.ally || a.down || a.ap < 2 || a.stunned > 0) return log(t, "Überwältigen braucht 2 AP.")
        if (dist(a.x, a.y, target.x, target.y) != 1) return log(t, "Nur an benachbarten Gegnern möglich.")
        val chance = (a.accuracy + 10 - (if (target.guarding) 15 else 0)).coerceIn(10, 95)
        val r = rng(t)
        val roll = r.percent()
        var n = t.copy(rolls = t.rolls + 1)
        n = replace(n, a.copy(ap = 0))
        n = if (roll <= chance) {
            n = replace(n, target.copy(hp = target.hp - 4, stunned = 2))
            log(n, "${a.name} überwältigt ${target.name} (betäubt).")
        } else log(n, "${target.name} wehrt sich ($roll/$chance).")
        return checkEnd(n)
    }

    fun guard(t: TacticalState, unitId: String): TacticalState {
        val u = t.units.firstOrNull { it.id == unitId && it.ally && !it.down } ?: return t
        if (u.ap <= 0) return t
        return log(replace(t, u.copy(ap = 0, guarding = true)), "${u.name} sichert die Position.")
    }

    fun useAbility(t: TacticalState, unitId: String, targetId: String?): TacticalState {
        if (t.finished) return t
        val u = t.units.firstOrNull { it.id == unitId && it.ally && !it.down } ?: return t
        val ab = u.ability ?: return t
        if (u.abilityCooldown > 0) return log(t, "${abilities[ab]?.name} erst in ${u.abilityCooldown} Runden wieder bereit.")
        if (u.ap <= 0) return t
        var n = replace(t, u.copy(ap = u.ap - 1, abilityCooldown = 3))
        when (ab) {
            "medic" -> {
                val target = n.units.filter { it.ally && !it.down && dist(it.x, it.y, u.x, u.y) <= 1 }
                    .minByOrNull { it.hp - it.maxHp } ?: return t
                n = replace(n, target.copy(hp = (target.hp + 4).coerceAtMost(target.maxHp)))
                n = log(n, "${u.name} versorgt ${target.name} (+4).")
            }
            "hack", "distract" -> {
                val target = n.units.firstOrNull { it.id == targetId && !it.ally && !it.down }
                    ?: n.units.filter { !it.ally && !it.down }.minByOrNull { dist(it.x, it.y, u.x, u.y) } ?: return t
                if (ab == "distract" && dist(target.x, target.y, u.x, u.y) > 6) return log(t, "Zu weit für eine Ablenkung.")
                n = replace(n, target.copy(stunned = 1))
                n = log(n, "${u.name}: ${abilities[ab]?.name} – ${target.name} setzt aus.")
            }
            "focus" -> n = log(n.copy(focusOrder = true), "${u.name} koordiniert: +15 Treffer für alle.")
            "shield" -> {
                n.units.filter { it.ally && !it.down && dist(it.x, it.y, u.x, u.y) <= 1 }.forEach { a -> n = replace(n, a.copy(guarding = true)) }
                n = log(n, "${u.name} sichert das Team.")
            }
            "command" -> {
                n.units.filter { it.ally && !it.down && it.id != u.id }.forEach { a -> n = replace(n, a.copy(ap = a.ap + 1)) }
                n = log(n, "Teambefehl: alle Verbündeten +1 AP.")
            }
        }
        return checkEnd(n)
    }

    fun endTurn(t: TacticalState): TacticalState {
        if (t.finished) return t
        var n = t.copy(focusOrder = false)
        // Gegnerzug
        val enemies = n.units.filter { !it.ally && !it.down }
        for (e0 in enemies) {
            var e = n.units.first { it.id == e0.id }
            if (e.stunned > 0) {
                n = replace(n, e.copy(stunned = e.stunned - 1))
                n = log(n, "${e.name} ist außer Gefecht gesetzt.")
                continue
            }
            var ap = 2
            while (ap > 0) {
                val allies = n.units.filter { it.ally && !it.down }
                if (allies.isEmpty()) break
                val best = allies.minByOrNull { dist(it.x, it.y, e.x, e.y) * 10 - (100 - hitChance(n, e, it)) / 10 }!!
                val d = dist(best.x, best.y, e.x, e.y)
                val chance = hitChance(n, e, best)
                if (d <= ATTACK_RANGE && chance >= 30) {
                    val r = rng(n)
                    val roll = r.percent()
                    n = n.copy(rolls = n.rolls + 1)
                    if (roll <= chance) {
                        val dmg = (e.damage + r.range(-1, 1) - best.armor).coerceAtLeast(1)
                        n = replace(n, best.copy(hp = best.hp - dmg))
                        n = log(n, "${e.name} trifft ${best.name} (−$dmg)" + if (best.hp - dmg <= 0) " – ${best.name} ist kampfunfähig!" else ".")
                    } else n = log(n, "${e.name} verfehlt ${best.name}.")
                    ap -= 1
                    break
                } else {
                    val options = reachable(n, e)
                    if (options.isEmpty()) break
                    val dest = options.minByOrNull { (x, y) ->
                        val nd = dist(x, y, best.x, best.y)
                        nd * 10 - (if (n.tile(x, y) == 'c') 15 else 0)
                    }!!
                    e = e.copy(x = dest.first, y = dest.second)
                    n = replace(n, e)
                    ap -= 1
                }
                e = n.units.first { it.id == e0.id }
            }
        }
        // Neue Runde
        n = n.copy(
            turn = n.turn + 1,
            units = n.units.map {
                if (it.ally) it.copy(ap = if (it.stunned > 0) 0 else 2, guarding = false, abilityCooldown = (it.abilityCooldown - 1).coerceAtLeast(0), stunned = (it.stunned - 1).coerceAtLeast(0))
                else it
            }
        )
        if (n.selectedUnit == null || n.units.firstOrNull { it.id == n.selectedUnit }?.down == true) {
            n = n.copy(selectedUnit = n.units.firstOrNull { it.ally && !it.down }?.id)
        }
        n = log(n, "— Runde ${n.turn} —")
        return checkEnd(n, endOfRound = true)
    }

    fun checkEnd(t: TacticalState, endOfRound: Boolean = false): TacticalState {
        val player = t.units.first { it.isPlayer }
        val enemiesLeft = t.units.count { !it.ally && !it.down }
        return when {
            player.down -> t.copy(finished = true, won = false, log = t.log + "Du bist kampfunfähig. Einsatz gescheitert.")
            t.objective == "eliminate" && enemiesLeft == 0 -> t.copy(finished = true, won = true, log = t.log + "Alle Gegner neutralisiert.")
            t.objective == "reach" && t.units.any { it.ally && !it.down && t.tile(it.x, it.y) == 'X' } -> t.copy(finished = true, won = true, log = t.log + "Ziel erreicht.")
            endOfRound && t.turn > t.maxTurns -> t.copy(finished = true, won = false, log = t.log + "Zeitfenster verstrichen.")
            else -> t
        }
    }
}

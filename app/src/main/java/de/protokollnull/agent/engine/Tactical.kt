package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*
import kotlin.math.abs
import kotlin.math.sign

/**
 * Abstraktes, rundenbasiertes 2D-Taktikbrett. Bewusst ohne reale
 * Waffen-/Taktikanleitungen: Treffer bedeuten "kampfunfähig/markiert".
 *
 * v0.7: Sichtlinien (Bresenham, Wände und Türen blockieren), Deckung je nach
 * Schussrichtung, Missionsziele jenseits von „alle ausschalten“ (Evakuierung,
 * Eskorte, Infopunkte, Aufklärung, Treffpunkt, Schutzposition), Gegnerrollen,
 * Patt-Erkennung und Teamwerte aus der Lebenssimulation.
 */
object Tactical {
    const val MOVE_RANGE = 3
    const val ATTACK_RANGE = 6
    const val OVERWATCH_RANGE = 8
    const val STALEMATE_ROUNDS = 4

    data class Ability(val id: String, val name: String, val description: String)

    val abilities = mapOf(
        "medic" to Ability("medic", "Erste Hilfe", "Heilt eine benachbarte Einheit um 4"),
        "hack" to Ability("hack", "Störsender", "Ein Gegner verliert seinen nächsten Zug"),
        "distract" to Ability("distract", "Ablenkung", "Gegner in Reichweite 6 verliert seinen nächsten Zug"),
        "focus" to Ability("focus", "Koordiniertes Vorgehen", "Alle Verbündeten +15 Treffer in diesem Zug"),
        "shield" to Ability("shield", "Sicherung", "Benachbarte Verbündete gehen in Deckung"),
        "command" to Ability("command", "Teambefehl", "Alle Verbündeten erhalten +1 AP (Teamleiter)")
    )

    val objectiveLabels = mapOf(
        "eliminate" to "Alle Gegner neutralisieren",
        "reach" to "Zielpunkt X erreichen",
        "extract" to "Schutzperson zur Evakuierung (X) bringen",
        "escort" to "Schutzperson sicher zum Ausgang (X) eskortieren",
        "intel" to "Infopunkte (I) sichern",
        "recon" to "Alle Aufklärungspunkte (R) erreichen",
        "rendezvous" to "Treffpunkt (Z) erreichen",
        "protect" to "Schutzposition (Z) halten, Schutzperson schützen"
    )

    val roleLabels = mapOf("guard" to "Wachposten", "flanker" to "Flankierer", "overwatch" to "Überwachung", "rusher" to "Stoßtrupp")

    fun passable(c: Char) = c != '#' && c != '~' && c != 'w'
    fun blocksSight(c: Char) = c == '#' || c == 'd'
    fun dist(ax: Int, ay: Int, bx: Int, by: Int) = abs(ax - bx) + abs(ay - by)

    // --- Aufbau --------------------------------------------------------------------

    fun setup(data: GameData, state: GameState, setup: TacticalSetup, team: List<String>, seed: Long, threat: Int): TacticalState {
        val layout = setup.mapId.takeIf { it.isNotBlank() }?.let { TacticalMaps.byId[it] }
        val rows = if (layout != null) layout.rows else setup.map
        val objective = setup.objective
        val h = rows.size
        val w = rows.maxOf { it.length }
        val keep = buildSet {
            if (objective in setOf("reach", "extract", "escort")) add('X')
            if (objective == "intel") add('I')
            if (objective == "recon") add('R')
            if (objective in setOf("rendezvous", "protect")) add('Z')
        }
        val useVip = objective in setOf("extract", "escort", "protect")
        val pSpawns = mutableListOf<Pair<Int, Int>>()
        val eSpawns = mutableListOf<Triple<Int, Int, Char>>()
        var vipSpawn: Pair<Int, Int>? = null
        rows.forEachIndexed { y, r ->
            r.forEachIndexed { x, c ->
                when (c) {
                    'P' -> pSpawns += x to y
                    'E', 'G', 'O' -> eSpawns += Triple(x, y, c)
                    'V' -> vipSpawn = x to y
                }
            }
        }
        val tiles = rows.map { r ->
            r.padEnd(w, '#').map { c ->
                when (c) {
                    'P', 'E', 'G', 'O', 'V' -> '.'
                    'X', 'I', 'R', 'Z' -> if (c in keep || layout == null) c else '.'
                    else -> c
                }
            }.joinToString("")
        }
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
            units += allyUnit(data, state, id, sp.first, sp.second)
        }
        val vs = vipSpawn
        if (useVip) {
            val spot = vs ?: pSpawns.getOrElse(team.size + 1) { spawn0 }
            units += TacticalUnit(
                id = "vip", name = setup.vipName.ifBlank { "Schutzperson" }, ally = true, x = spot.first, y = spot.second,
                hp = 6, maxHp = 6, accuracy = 0, damage = 0, vip = true, role = "vip"
            )
        }
        // Zusätzliche Gegner bei hoher Gefahrenstufe – deterministisch auf freien Feldern der oberen Hälfte
        val r = Rng(Rng.mix(seed, 4242))
        val extra = (threat - 2).coerceIn(0, 2)
        val free = (0 until h / 2).flatMap { y -> (0 until w).map { x -> x to y } }
            .filter { (x, y) -> tiles[y][x] == '.' && eSpawns.none { it.first == x && it.second == y } }
        val extraSpots = r.shuffled(free).take(extra).map { Triple(it.first, it.second, 'E') }
        val enemies = eSpawns + extraSpots
        enemies.forEachIndexed { i, (x, y, c) ->
            val role = when (c) { 'G' -> "guard"; 'O' -> "overwatch"; else -> if (i % 2 == 0) "rusher" else "flanker" }
            units += TacticalUnit(
                id = "e$i", name = setup.enemyName + " ${i + 1}", ally = false, x = x, y = y,
                hp = setup.enemyHp + (threat - 1), maxHp = setup.enemyHp + (threat - 1),
                accuracy = setup.enemyAccuracy + threat * 3 + if (role == "overwatch") 5 else 0,
                damage = 2 + threat / 2, armor = if (threat >= 4) 1 else 0,
                role = role, range = if (role == "overwatch") OVERWATCH_RANGE else ATTACK_RANGE, homeX = x, homeY = y
            )
        }
        val required = when (objective) {
            "intel" -> setup.requiredSecured.takeIf { it > 0 } ?: tiles.sumOf { row -> row.count { it == 'I' } }
            "recon" -> tiles.sumOf { row -> row.count { it == 'R' } }
            else -> 0
        }
        val hold = if (objective == "protect") setup.holdTurns.coerceAtLeast(2) else 0
        return TacticalState(
            width = w, height = h, tiles = tiles, units = units, maxTurns = setup.maxTurns,
            objective = objective, selectedUnit = "player", seed = seed, enemyName = setup.enemyName,
            nonLethal = setup.nonLethal, mapId = layout?.id ?: "", environment = layout?.environment ?: "",
            mapName = layout?.name ?: "", requiredSecured = required, holdTurns = hold,
            log = listOf("Einsatzbrett${layout?.let { " „${it.name}“" } ?: ""}: ${objectiveLabel(objective, required, hold, enemies.size)} – max. ${setup.maxTurns} Runden.")
        )
    }

    fun objectiveLabel(objective: String, required: Int, hold: Int, enemies: Int) = when (objective) {
        "eliminate" -> "Setze alle $enemies Gegner außer Gefecht"
        "intel" -> "Sichere $required Infopunkte (I)"
        "recon" -> "Erreiche alle $required Aufklärungspunkte (R)"
        "protect" -> "Halte die Schutzposition (Z) $hold Runden"
        else -> objectiveLabels[objective] ?: objective
    }

    /** Verbündete Einheit mit Werten aus der Lebenssimulation (Kompetenz, Verfassung). */
    fun allyUnit(data: GameData, state: GameState, id: String, x: Int, y: Int): TacticalUnit {
        val def = data.agentById[id]
        val life = Life.life(data, state, id)
        val combat = Life.competence(data, state, id, "combat")
        val focus = life.focus
        val loyalty = Relations.get(data, state, id).loyalty
        val hp = 7 + combat / 2 + (if (def != null) def.combat / 30 else 0) - (if (life.stress > 70) 1 else 0)
        val ability = def?.teamAbility ?: when (focus) {
            "medic" -> "medic"; "tech" -> "hack"; "intel" -> "distract"; "social" -> "shield"; else -> "focus"
        }
        return TacticalUnit(
            id = id, name = Relations.displayName(data, state, id), ally = true, x = x, y = y,
            hp = hp, maxHp = hp, accuracy = (38 + combat * 5 + loyalty / 10 + (life.mood - 50) / 10).coerceIn(30, 90),
            damage = 3 + if (combat >= 7) 1 else 0, ability = ability, role = focus
        )
    }

    private fun rng(t: TacticalState) = Rng(Rng.mix(t.seed, t.rolls.toLong(), t.turn.toLong()))

    fun unitAt(t: TacticalState, x: Int, y: Int) = t.units.firstOrNull { !it.down && !it.evacuated && it.x == x && it.y == y }

    fun key(x: Int, y: Int) = "$x,$y"

    // --- Bewegung, Sicht, Deckung ---------------------------------------------------------

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

    /** Freie Sichtlinie zwischen zwei Feldern (Endpunkte ausgenommen). */
    fun lineOfSight(t: TacticalState, ax: Int, ay: Int, bx: Int, by: Int): Boolean {
        var x = ax; var y = ay
        val dx = abs(bx - ax); val dy = -abs(by - ay)
        val sx = if (ax < bx) 1 else -1; val sy = if (ay < by) 1 else -1
        var err = dx + dy
        while (!(x == bx && y == by)) {
            val e2 = 2 * err
            val stepX = e2 >= dy; val stepY = e2 <= dx
            // Diagonaler Schritt zwischen zwei blockierenden Ecken: keine Sicht
            if (stepX && stepY && blocksSight(t.tile(x + sx, y)) && blocksSight(t.tile(x, y + sy))) return false
            if (stepX) { err += dy; x += sx }
            if (stepY) { err += dx; y += sy }
            if (x == bx && y == by) break
            if (blocksSight(t.tile(x, y))) return false
        }
        return true
    }

    fun canSee(t: TacticalState, a: TacticalUnit, b: TacticalUnit) = lineOfSight(t, a.x, a.y, b.x, b.y)

    /** Deckungswert des Ziels gegenüber dem Angreifer (0, 20 oder 25). */
    fun cover(t: TacticalState, ax: Int, ay: Int, tx: Int, ty: Int): Int {
        if (t.tile(tx, ty) == 'c') return 25
        val sx = (ax - tx).sign; val sy = (ay - ty).sign
        val sides = buildList {
            if (sx != 0) add(t.tile(tx + sx, ty))
            if (sy != 0) add(t.tile(tx, ty + sy))
        }
        return if (sides.any { it == 'c' || it == '#' || it == 'w' }) 20 else 0
    }

    fun hitChance(t: TacticalState, a: TacticalUnit, target: TacticalUnit): Int = hitChanceFrom(t, a, a.x, a.y, target)

    fun hitChanceFrom(t: TacticalState, a: TacticalUnit, ax: Int, ay: Int, target: TacticalUnit): Int {
        val d = dist(ax, ay, target.x, target.y)
        var c = a.accuracy - 4 * (d - 2).coerceAtLeast(0)
        c -= cover(t, ax, ay, target.x, target.y)
        if (target.guarding) c -= 15
        if (a.ally && t.focusOrder) c += 15
        return c.coerceIn(5, 95)
    }

    /** Kann [a] das Ziel überhaupt angreifen (Reichweite + Sicht)? Begründung oder null. */
    fun attackBlocker(t: TacticalState, a: TacticalUnit, target: TacticalUnit): String? {
        val d = dist(a.x, a.y, target.x, target.y)
        if (d > a.range) return "außer Reichweite ($d > ${a.range})"
        if (!canSee(t, a, target)) return "kein Sichtkontakt"
        return null
    }

    /** Felder in Sicht und Reichweite der Einheit (für die Anzeige). */
    fun visibleTiles(t: TacticalState, u: TacticalUnit): Set<Pair<Int, Int>> =
        (0 until t.height).flatMap { y -> (0 until t.width).map { x -> x to y } }
            .filter { (x, y) -> dist(u.x, u.y, x, y) <= u.range && !blocksSight(t.tile(x, y)) && lineOfSight(t, u.x, u.y, x, y) }.toSet()

    private fun replace(t: TacticalState, u: TacticalUnit) = t.copy(units = t.units.map { if (it.id == u.id) u else it })
    private fun log(t: TacticalState, line: String) = t.copy(log = (t.log + line).takeLast(30))

    // --- Aktionen ------------------------------------------------------------------------

    fun select(t: TacticalState, id: String): TacticalState {
        val u = t.units.firstOrNull { it.id == id && it.ally && !it.down && !it.evacuated } ?: return t
        return t.copy(selectedUnit = u.id)
    }

    fun move(t: TacticalState, unitId: String, x: Int, y: Int): TacticalState {
        if (t.finished) return t
        val u = t.units.firstOrNull { it.id == unitId } ?: return t
        if (!u.ally || u.down || u.evacuated || u.ap <= 0 || u.stunned > 0) return t
        if ((x to y) !in reachable(t, u)) return log(t, "Feld nicht erreichbar.")
        var n = replace(t, u.copy(x = x, y = y, ap = u.ap - 1, guarding = false))
        n = log(n, "${u.name} bewegt sich.")
        n = onEnterTile(n, n.units.first { it.id == unitId })
        return checkEnd(n)
    }

    private fun onEnterTile(t: TacticalState, u: TacticalUnit): TacticalState {
        val tile = t.tile(u.x, u.y)
        var n = t
        if (tile == 'R' && !u.vip && key(u.x, u.y) !in n.secured) n = log(n.copy(secured = n.secured + key(u.x, u.y)), "Aufklärungspunkt erfasst (${n.secured.size + 1}/${n.requiredSecured}).")
        if (tile == 'X' && u.vip && n.objective in setOf("extract", "escort")) n = log(replace(n, u.copy(evacuated = true)), "${u.name} ist in Sicherheit.")
        return n
    }

    /** Infopunkt auf dem eigenen Feld sichern (1 AP). */
    fun secure(t: TacticalState, unitId: String): TacticalState {
        if (t.finished) return t
        val u = t.units.firstOrNull { it.id == unitId && it.ally && !it.down && !it.vip } ?: return t
        if (u.ap <= 0 || u.stunned > 0) return t
        if (t.tile(u.x, u.y) != 'I') return log(t, "Hier gibt es nichts zu sichern.")
        if (key(u.x, u.y) in t.secured) return log(t, "Dieser Infopunkt ist bereits gesichert.")
        var n = replace(t, u.copy(ap = u.ap - 1))
        n = n.copy(secured = n.secured + key(u.x, u.y))
        n = log(n, "${u.name} sichert Informationen (${n.secured.size}/${n.requiredSecured}).")
        return checkEnd(n)
    }

    fun attack(t: TacticalState, unitId: String, targetId: String): TacticalState {
        if (t.finished) return t
        val a = t.units.firstOrNull { it.id == unitId } ?: return t
        val target = t.units.firstOrNull { it.id == targetId && !it.ally && !it.down } ?: return t
        if (!a.ally || a.down || a.vip || a.ap <= 0 || a.stunned > 0) return t
        attackBlocker(t, a, target)?.let { return log(t, "${target.name}: $it.") }
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
        if (!a.ally || a.down || a.vip || a.ap < 2 || a.stunned > 0) return log(t, "Überwältigen braucht 2 AP.")
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
        val u = t.units.firstOrNull { it.id == unitId && it.ally && !it.down && !it.vip } ?: return t
        val ab = u.ability ?: return t
        if (u.abilityCooldown > 0) return log(t, "${abilities[ab]?.name} erst in ${u.abilityCooldown} Runden wieder bereit.")
        if (u.ap <= 0) return t
        var n = replace(t, u.copy(ap = u.ap - 1, abilityCooldown = 3))
        when (ab) {
            "medic" -> {
                val target = n.units.filter { it.ally && !it.down && !it.evacuated && dist(it.x, it.y, u.x, u.y) <= 1 }
                    .minByOrNull { it.hp - it.maxHp } ?: return t
                n = replace(n, target.copy(hp = (target.hp + 4).coerceAtMost(target.maxHp)))
                n = log(n, "${u.name} versorgt ${target.name} (+4).")
            }
            "hack", "distract" -> {
                val target = n.units.firstOrNull { it.id == targetId && !it.ally && !it.down }
                    ?: n.units.filter { !it.ally && !it.down }.minByOrNull { dist(it.x, it.y, u.x, u.y) } ?: return t
                if (ab == "distract" && (dist(target.x, target.y, u.x, u.y) > 6 || !canSee(n, u, target))) return log(t, "Ablenkung braucht Sichtkontakt in Reichweite 6.")
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

    // --- Gegnerzug -------------------------------------------------------------------------

    private fun targets(n: TacticalState) = n.units.filter { it.ally && !it.down && !it.evacuated }

    /** Bestes sichtbares Ziel aus Position (x, y). */
    private fun bestShot(n: TacticalState, e: TacticalUnit, x: Int, y: Int): Pair<TacticalUnit, Int>? =
        targets(n).filter { dist(x, y, it.x, it.y) <= e.range && lineOfSight(n, x, y, it.x, it.y) }
            .map { it to hitChanceFrom(n, e, x, y, it) + (if (it.vip) 10 else 0) + (it.maxHp - it.hp) }
            .maxByOrNull { it.second }

    private fun shoot(n0: TacticalState, e: TacticalUnit, target: TacticalUnit): TacticalState {
        var n = n0
        val chance = hitChance(n, e, target)
        val r = rng(n)
        val roll = r.percent()
        n = n.copy(rolls = n.rolls + 1)
        if (roll <= chance) {
            val dmg = (e.damage + r.range(-1, 1) - target.armor).coerceAtLeast(1)
            n = replace(n, target.copy(hp = target.hp - dmg))
            n = log(n, "${e.name} trifft ${target.name} (−$dmg)" + if (target.hp - dmg <= 0) " – ${target.name} ist kampfunfähig!" else ".")
        } else n = log(n, "${e.name} verfehlt ${target.name}.")
        return n
    }

    private fun ownCover(n: TacticalState, x: Int, y: Int): Int {
        val threats = targets(n)
        if (threats.isEmpty()) return 0
        return threats.minOf { cover(n, it.x, it.y, x, y) }
    }

    private fun chooseMove(n: TacticalState, e: TacticalUnit): Pair<Int, Int>? {
        val options = reachable(n, e).toList()
        if (options.isEmpty()) return null
        val allies = targets(n)
        if (allies.isEmpty()) return null
        fun score(x: Int, y: Int): Int {
            val shot = bestShot(n, e, x, y)
            val nearest = allies.minOf { dist(x, y, it.x, it.y) }
            val homeDist = if (e.homeX >= 0) dist(x, y, e.homeX, e.homeY) else 0
            return when (e.role) {
                "guard" -> (shot?.second ?: 0) + ownCover(n, x, y) - homeDist * 12
                "flanker" -> (shot?.let { it.second * 2 - cover(n, x, y, it.first.x, it.first.y) * 2 } ?: -nearest * 6) + ownCover(n, x, y) / 2
                else -> (shot?.second ?: 0) - nearest * 5 + ownCover(n, x, y)
            }
        }
        val current = score(e.x, e.y)
        val best = options.sortedWith(compareBy<Pair<Int, Int>>({ it.second }, { it.first })).maxByOrNull { score(it.first, it.second) } ?: return null
        return if (score(best.first, best.second) > current) best else null
    }

    fun endTurn(t: TacticalState): TacticalState {
        if (t.finished) return t
        var n = t.copy(focusOrder = false)
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
                if (targets(n).isEmpty()) break
                val shot = bestShot(n, e, e.x, e.y)
                val threshold = when (e.role) { "overwatch" -> 20; "flanker" -> 40; "guard" -> 25; else -> 30 }
                if (shot != null && hitChance(n, e, shot.first) >= threshold) {
                    n = shoot(n, e, shot.first)
                    ap -= 1
                    break
                }
                if (e.role == "overwatch") break
                val dest = chooseMove(n, e) ?: break
                e = e.copy(x = dest.first, y = dest.second)
                n = replace(n, e)
                ap -= 1
                e = n.units.first { it.id == e0.id }
            }
        }
        // Schutzposition: gehalten, wenn ein Teammitglied auf Z steht und die Schutzperson lebt
        if (n.objective == "protect") {
            val onZone = n.units.any { it.ally && !it.vip && !it.down && n.tile(it.x, it.y) == 'Z' }
            val vipOk = n.units.none { it.vip && it.down }
            n = if (onZone && vipOk) log(n.copy(held = n.held + 1), "Schutzposition gehalten (${n.held + 1}/${n.holdTurns}).") else n
        }
        // Patt-Erkennung: keine Veränderung über mehrere Runden
        val mark = progress(n)
        val stale = if (mark == n.progressMark) n.stalemate + 1 else 0
        n = n.copy(
            turn = n.turn + 1, stalemate = stale, progressMark = mark,
            units = n.units.map {
                if (it.ally) it.copy(ap = if (it.stunned > 0) 0 else 2, guarding = false, abilityCooldown = (it.abilityCooldown - 1).coerceAtLeast(0), stunned = (it.stunned - 1).coerceAtLeast(0))
                else it
            }
        )
        if (n.selectedUnit == null || n.units.firstOrNull { it.id == n.selectedUnit }?.let { it.down || it.evacuated } == true) {
            n = n.copy(selectedUnit = n.units.firstOrNull { it.ally && !it.down && !it.evacuated }?.id)
        }
        n = log(n, "— Runde ${n.turn} —")
        return checkEnd(n, endOfRound = true)
    }

    /** Fingerabdruck des Brettzustands für die Patt-Erkennung. */
    fun progress(t: TacticalState): Int =
        t.units.sumOf { (it.hp * 31 + it.x * 7 + it.y * 13 + if (it.evacuated) 500 else 0) * (if (it.ally) 1 else 3) } + t.secured.size * 1000 + t.held * 2000

    fun checkEnd(t: TacticalState, endOfRound: Boolean = false): TacticalState {
        val player = t.units.first { it.isPlayer }
        val enemiesLeft = t.units.count { !it.ally && !it.down }
        val vip = t.units.firstOrNull { it.vip }
        return when {
            player.down -> t.copy(finished = true, won = false, log = t.log + "Du bist kampfunfähig. Einsatz gescheitert.")
            vip != null && vip.down -> t.copy(finished = true, won = false, log = t.log + "Die Schutzperson ist verletzt – Einsatz gescheitert.")
            t.objective == "eliminate" && enemiesLeft == 0 -> t.copy(finished = true, won = true, log = t.log + "Alle Gegner neutralisiert.")
            t.objective == "reach" && t.units.any { it.ally && !it.down && t.tile(it.x, it.y) == 'X' } -> t.copy(finished = true, won = true, log = t.log + "Ziel erreicht.")
            t.objective in setOf("extract", "escort") && vip?.evacuated == true -> t.copy(finished = true, won = true, log = t.log + "Evakuierung abgeschlossen.")
            t.objective in setOf("intel", "recon") && t.requiredSecured > 0 && t.secured.size >= t.requiredSecured -> t.copy(finished = true, won = true, log = t.log + if (t.objective == "intel") "Alle Informationen gesichert." else "Aufklärung abgeschlossen.")
            t.objective == "rendezvous" && t.tile(player.x, player.y) == 'Z' -> t.copy(finished = true, won = true, log = t.log + "Treffpunkt erreicht.")
            t.objective == "protect" && t.held >= t.holdTurns -> t.copy(finished = true, won = true, log = t.log + "Schutzposition erfolgreich gehalten.")
            endOfRound && t.stalemate >= STALEMATE_ROUNDS -> t.copy(finished = true, won = false, log = t.log + "Patt – keine Seite kommt voran. Rückzug befohlen.")
            endOfRound && t.turn > t.maxTurns -> t.copy(finished = true, won = false, log = t.log + "Zeitfenster verstrichen.")
            else -> t
        }
    }
}

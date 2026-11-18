package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Deterministischer SplitMix64-Zufallsgenerator (reproduzierbare Seeds). */
class Rng(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        if (bound <= 1) return 0
        val r = (nextLong() ushr 1) % bound
        return r.toInt()
    }

    fun range(min: Int, maxInclusive: Int): Int = min + nextInt(maxInclusive - min + 1)
    fun percent(): Int = nextInt(100) + 1
    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]
    fun <T> shuffled(list: List<T>): List<T> {
        val m = list.toMutableList()
        for (i in m.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = m[i]; m[i] = m[j]; m[j] = t
        }
        return m
    }

    companion object {
        fun mix(vararg parts: Long): Long {
            var h = 1125899906842597L
            for (p in parts) h = 31 * h + p
            return h
        }
    }
}

/** Ein Ergebnis einer Aktion: neuer Zustand + Meldungen für die Oberfläche. */
data class Result(val state: GameState, val messages: List<String> = emptyList(), val ok: Boolean = true) {
    fun plus(more: List<String>) = copy(messages = messages + more)
}

fun GameState.fail(msg: String) = Result(this, listOf(msg), ok = false)
fun GameState.ok(vararg msg: String) = Result(this, msg.toList())

object Clock {
    val slotIds = listOf("morning", "forenoon", "noon", "afternoon", "evening", "night")
    val slotNames = listOf("Morgen", "Vormittag", "Mittag", "Nachmittag", "Abend", "Nacht")
    val slotTimes = listOf("06:30", "09:00", "12:30", "15:00", "18:30", "22:00")
    private val startDate: LocalDate = LocalDate.of(2011, 9, 5)
    const val START_AGE = 16
    private val fmt = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)

    fun date(day: Int): LocalDate = startDate.plusDays(day.toLong())
    fun dateLabel(day: Int): String = fmt.format(date(day))
    fun weekday(day: Int): Int = date(day).dayOfWeek.value // 1 = Mo
    fun isWeekend(day: Int): Boolean = weekday(day) >= 6
    fun week(day: Int): Int = day / 7

    /** Spieler ist am Tag 0 genau 16 Jahre alt (Geburtstag = Tag 0). */
    fun playerAge(day: Int): Int = START_AGE + day / 365
    fun daysUntilAge(day: Int, age: Int): Int = ((age - START_AGE) * 365 - day).coerceAtLeast(0)
}

object Ages {
    fun player(state: GameState) = Clock.playerAge(state.clock.day)
    fun npc(data: GameData, state: GameState, id: String): Int {
        val custom = state.customNpcs.firstOrNull { it.id == id }
        if (custom != null) return custom.ageAtCreation + (state.clock.day - custom.createdDay).coerceAtLeast(0) / 365
        val def = data.agentById[id] ?: return 30
        return def.ageAtStart + state.clock.day / 365
    }
}

/** Zentrale Klammer-Hilfen. */
fun Int.pct() = coerceIn(0, 100)

object Stats {
    fun attributeCap(level: Int): Int = (40 + level).coerceAtMost(100)

    fun equipped(data: GameData, p: PlayerCharacter): List<ItemDefinition> =
        p.equipment.values.mapNotNull { uid -> p.inventory.firstOrNull { it.uid == uid } }
            .mapNotNull { data.itemById[it.itemId] }

    fun effectiveAttribute(data: GameData, state: GameState, attr: String): Int {
        val p = state.player
        val base = p.attributes[attr] ?: 30
        val items = equipped(data, p).sumOf { it.bonuses[attr] ?: 0 }
        return (base + items).coerceIn(0, 120)
    }

    fun difficultyShift(state: GameState): Int = when (state.player.difficulty) {
        "easy" -> -10
        "hard" -> 10
        else -> 0
    }

    fun checkChance(data: GameData, state: GameState, check: CheckDef, bonus: Int = 0): Int {
        val attr = effectiveAttribute(data, state, check.attribute)
        val skill = check.skill?.let { state.player.skills[it] ?: 0 } ?: 0
        var score = attr + skill * 8 + bonus
        if (state.player.stress > 80) score -= 10
        if (state.player.health < 40) score -= 10
        if (state.player.energy < 20) score -= 5
        return (50 + score - check.difficulty - difficultyShift(state)).coerceIn(5, 95)
    }
}

data class CheckOutcome(val chance: Int, val roll: Int, val success: Boolean)

object Checks {
    /** Würfelt deterministisch anhand Seed und globalem Wurfzähler. */
    fun roll(data: GameData, state: GameState, check: CheckDef, bonus: Int = 0, salt: Long = 0): Pair<GameState, CheckOutcome> {
        val chance = Stats.checkChance(data, state, check, bonus)
        val rng = Rng(Rng.mix(state.seed, state.rolls.toLong(), state.clock.day.toLong(), salt))
        val roll = rng.percent()
        return state.copy(rolls = state.rolls + 1) to CheckOutcome(chance, roll, roll <= chance)
    }
}

object Relations {
    fun get(data: GameData, state: GameState, id: String): Relation {
        state.relations[id]?.let { return it }
        val def = data.agentById[id]
        return if (def != null) Relation(trust = def.initialTrust, friendship = (def.initialTrust / 2), respect = 30)
        else Relation(trust = 15, friendship = 10, respect = 20)
    }

    fun update(data: GameData, state: GameState, id: String, f: (Relation) -> Relation): GameState {
        val r = f(get(data, state, id))
        val clamped = r.copy(
            friendship = r.friendship.pct(), trust = r.trust.pct(), loyalty = r.loyalty.pct(),
            respect = r.respect.pct(), affection = r.affection.pct(), rivalry = r.rivalry.pct(),
            memories = r.memories.takeLast(12)
        )
        return state.copy(relations = state.relations + (id to clamped))
    }

    fun displayName(data: GameData, state: GameState, id: String?): String {
        if (id == null) return ""
        if (id == "player") return state.player.codename
        state.customNpcs.firstOrNull { it.id == id }?.let { return it.name }
        return data.agentById[id]?.name ?: id
    }

    fun isKnownNpc(data: GameData, state: GameState, id: String) =
        data.agentById.containsKey(id) || state.customNpcs.any { it.id == id }
}

object Conds {
    fun met(c: Conditions?, data: GameData, state: GameState, npcId: String? = null): Boolean {
        if (c == null) return true
        val p = state.player
        val age = Ages.player(state)
        val rel = npcId?.let { Relations.get(data, state, it) }
        if (c.minTrust != null && (rel?.trust ?: 0) < c.minTrust) return false
        if (c.minFriendship != null && (rel?.friendship ?: 0) < c.minFriendship) return false
        if (c.minAffection != null && (rel?.affection ?: 0) < c.minAffection) return false
        if (c.minRespect != null && (rel?.respect ?: 0) < c.minRespect) return false
        if (c.minAge != null && age < c.minAge) return false
        if (c.maxAge != null && age > c.maxAge) return false
        if (c.minRank != null && p.rank < c.minRank) return false
        if (c.minLevel != null && p.level < c.minLevel) return false
        if (!state.flags.containsAll(c.flagsAll)) return false
        if (c.flagsNone.any { it in state.flags }) return false
        if (c.minSkill.any { (k, v) -> (p.skills[k] ?: 0) < v }) return false
        if (c.minAttribute.any { (k, v) -> Stats.effectiveAttribute(data, state, k) < v }) return false
        if (!state.completedMissions.containsAll(c.missionsDone)) return false
        if (c.hasItem != null && p.inventory.none { it.itemId == c.hasItem }) return false
        return true
    }

    fun describe(c: Conditions?, data: GameData): List<String> {
        if (c == null) return emptyList()
        val out = mutableListOf<String>()
        c.minTrust?.let { out += "Vertrauen ≥ $it" }
        c.minFriendship?.let { out += "Freundschaft ≥ $it" }
        c.minAffection?.let { out += "Zuneigung ≥ $it" }
        c.minRespect?.let { out += "Respekt ≥ $it" }
        c.minAge?.let { out += "Alter ≥ $it" }
        c.minRank?.let { out += "Rang ≥ $it" }
        c.minLevel?.let { out += "Level ≥ $it" }
        c.minSkill.forEach { (k, v) -> out += (data.skillById[k]?.name ?: k) + " ≥ $v" }
        c.minAttribute.forEach { (k, v) -> out += Attr.label(k) + " ≥ $v" }
        c.missionsDone.forEach { out += "Mission: " + (data.missionById[it]?.title ?: it) }
        c.hasItem?.let { out += "Gegenstand: " + (data.itemById[it]?.name ?: it) }
        return out
    }
}

object Effs {
    /** Wendet Effekte an; npcId bestimmt das Ziel von Beziehungsänderungen. */
    fun apply(e: Effects?, data: GameData, state: GameState, npcId: String? = null): Result {
        if (e == null) return Result(state)
        var s = state
        val msgs = mutableListOf<String>()
        if (npcId != null && Relations.isKnownNpc(data, s, npcId)) {
            val any = e.trust != 0 || e.friendship != 0 || e.loyalty != 0 || e.respect != 0 ||
                e.affection != 0 || e.rivalry != 0 || e.memory != null
            if (any) {
                s = Relations.update(data, s, npcId) { r ->
                    r.copy(
                        trust = r.trust + e.trust, friendship = r.friendship + e.friendship,
                        loyalty = r.loyalty + e.loyalty, respect = r.respect + e.respect,
                        affection = r.affection + romanceSafeAffection(data, s, npcId, e.affection),
                        rivalry = r.rivalry + e.rivalry,
                        memories = if (e.memory != null) r.memories + e.memory else r.memories
                    )
                }
                val name = Relations.displayName(data, s, npcId)
                if (e.trust != 0) msgs += "$name: Vertrauen ${signed(e.trust)}"
                if (e.friendship != 0) msgs += "$name: Freundschaft ${signed(e.friendship)}"
                if (e.respect != 0) msgs += "$name: Respekt ${signed(e.respect)}"
                if (e.loyalty != 0) msgs += "$name: Loyalität ${signed(e.loyalty)}"
                if (e.rivalry != 0) msgs += "$name: Rivalität ${signed(e.rivalry)}"
            }
        }
        e.otherTrust.forEach { (id, d) ->
            if (Relations.isKnownNpc(data, s, id)) {
                s = Relations.update(data, s, id) { it.copy(trust = it.trust + d) }
                msgs += Relations.displayName(data, s, id) + ": Vertrauen " + signed(d)
            }
        }
        var p = s.player
        p = p.copy(
            stress = (p.stress + e.stress).pct(),
            energy = (p.energy + e.energy).pct(),
            health = (p.health + e.health).coerceIn(1, 100),
            credits = (p.credits + e.credits).coerceAtLeast(0),
            reputation = (p.reputation + e.reputation).coerceAtLeast(0)
        )
        if (e.attributes.isNotEmpty()) {
            val cap = Stats.attributeCap(p.level)
            p = p.copy(attributes = p.attributes.mapValues { (k, v) -> (v + (e.attributes[k] ?: 0)).coerceIn(0, cap.coerceAtLeast(v)) })
            e.attributes.forEach { (k, v) -> msgs += Attr.label(k) + " " + signed(v) }
        }
        s = s.copy(player = p)
        if (e.credits != 0) msgs += "Credits ${signed(e.credits)}"
        if (e.reputation != 0) msgs += "Ruf ${signed(e.reputation)}"
        if (e.stress != 0) msgs += "Stress ${signed(e.stress)}"
        if (e.health < 0) msgs += "Gesundheit ${signed(e.health)}"
        s = s.copy(flags = s.flags + e.setFlags - e.clearFlags.toSet())
        e.faction.forEach { (f, d) ->
            s = s.copy(factionStanding = s.factionStanding + (f to ((s.factionStanding[f] ?: 0) + d).coerceIn(-100, 100)))
        }
        e.giveItems.forEach { (id, q) ->
            val r = Inventory.add(data, s, id, q)
            s = r.state; msgs += r.messages
        }
        if (e.xp > 0) {
            val r = Progression.grantXp(data, s, e.xp)
            s = r.state; msgs += r.messages
        }
        return Result(s, msgs)
    }

    /** Zuneigung steigt nur, wenn eine Romanze altersgerecht zulässig ist. */
    private fun romanceSafeAffection(data: GameData, s: GameState, npcId: String, delta: Int): Int =
        if (delta <= 0) delta else if (Romance.allowed(data, s, npcId)) delta else 0

    fun signed(v: Int) = if (v > 0) "+$v" else v.toString()
}

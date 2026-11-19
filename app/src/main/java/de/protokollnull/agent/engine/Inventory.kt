package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

object Inventory {
    const val BASE_CAPACITY = 6000

    fun weight(data: GameData, stacks: List<ItemStack>): Int =
        stacks.sumOf { (data.itemById[it.itemId]?.weightGrams ?: 0) * it.quantity }

    fun capacity(data: GameData, state: GameState): Int {
        val p = state.player
        val bag = Stats.equipped(data, p).sumOf { it.capacityBonusGrams }
        val fit = ((p.attributes[Attr.FITNESS] ?: 30) - 30).coerceAtLeast(0) * 40
        val skill = (p.skills["stamina"] ?: 0) * 400 + (p.skills["logistics"] ?: 0) * 500
        return BASE_CAPACITY + bag + fit + skill
    }

    fun count(state: GameState, itemId: String): Int =
        state.player.inventory.filter { it.itemId == itemId }.sumOf { it.quantity }

    /** Fügt Gegenstände hinzu und respektiert Stapelgrößen und Tragkraft. */
    fun add(data: GameData, state: GameState, itemId: String, quantity: Int, toStorageIfFull: Boolean = true): Result {
        val def = data.itemById[itemId] ?: return state.fail("Unbekannter Gegenstand: $itemId")
        if (quantity <= 0) return Result(state)
        var p = state.player
        var remaining = quantity
        val inv = p.inventory.toMutableList()
        var uid = p.nextUid
        val capacity = capacity(data, state)
        var overflow = 0
        // Bestehende Stapel auffüllen
        for (i in inv.indices) {
            if (remaining == 0) break
            val st = inv[i]
            if (st.itemId == itemId && st.quantity < def.maxStack) {
                val space = def.maxStack - st.quantity
                val add = minOf(space, remaining)
                inv[i] = st.copy(quantity = st.quantity + add)
                remaining -= add
            }
        }
        while (remaining > 0) {
            val add = minOf(def.maxStack.coerceAtLeast(1), remaining)
            inv += ItemStack(uid++, itemId, add)
            remaining -= add
        }
        var storage = p.storage
        // Überladen? Neu erhaltene Gegenstände gehen ins Lager (Spind).
        while (weight(data, inv) > capacity && toStorageIfFull) {
            val idx = inv.indexOfLast { it.itemId == itemId && it.uid !in p.equipment.values }
            if (idx < 0) break
            val st = inv[idx]
            if (st.quantity > 1) {
                inv[idx] = st.copy(quantity = st.quantity - 1)
                storage = mergeInto(storage, ItemStack(uid++, itemId, 1), def.maxStack)
            } else {
                inv.removeAt(idx)
                storage = mergeInto(storage, st, def.maxStack)
            }
            overflow++
        }
        p = p.copy(inventory = inv, storage = storage, nextUid = uid)
        val msgs = mutableListOf("Erhalten: ${def.name} ×$quantity")
        if (overflow > 0) msgs += "$overflow× ${def.name} ins Spind-Lager (Tragkraft erreicht)"
        return Result(state.copy(player = p), msgs)
    }

    private fun mergeInto(list: List<ItemStack>, st: ItemStack, maxStack: Int): List<ItemStack> {
        val idx = list.indexOfFirst { it.itemId == st.itemId && it.quantity + st.quantity <= maxStack && it.condition == st.condition }
        return if (idx >= 0) list.mapIndexed { i, s -> if (i == idx) s.copy(quantity = s.quantity + st.quantity) else s } else list + st
    }

    fun removeById(state: GameState, itemId: String, quantity: Int): GameState {
        var left = quantity
        val p = state.player
        val inv = mutableListOf<ItemStack>()
        for (st in p.inventory.sortedBy { it.quantity }) {
            if (left > 0 && st.itemId == itemId) {
                val take = minOf(left, st.quantity)
                left -= take
                if (st.quantity - take > 0) inv += st.copy(quantity = st.quantity - take)
            } else inv += st
        }
        val ordered = p.inventory.mapNotNull { o -> inv.firstOrNull { it.uid == o.uid } }
        val equipment = p.equipment.filterValues { uid -> ordered.any { it.uid == uid } }
        return state.copy(player = p.copy(inventory = ordered, equipment = equipment))
    }

    fun canEquip(data: GameData, state: GameState, stack: ItemStack): String? {
        val def = data.itemById[stack.itemId] ?: return "Unbekannt"
        if (def.slot == "none") return "Nicht ausrüstbar"
        if (state.player.rank < def.minRank) return "Freigabe ab Rang ${def.minRank} (${data.rank(def.minRank).name})"
        if (def.category == "weapons" && Ages.player(state) < 18 && def.id != "pistol_training") return "Erst ab 18 im Einsatz zugelassen"
        if (stack.condition <= 0) return "Defekt – erst reparieren"
        return null
    }

    fun equip(data: GameData, state: GameState, uid: Long): Result {
        val p = state.player
        val st = p.inventory.firstOrNull { it.uid == uid } ?: return state.fail("Nicht im Inventar")
        canEquip(data, state, st)?.let { return state.fail(it) }
        val def = data.itemById.getValue(st.itemId)
        val next = p.copy(equipment = p.equipment + (def.slot to uid))
        return Result(state.copy(player = next), listOf("${def.name} ausgerüstet (${Slots.label(def.slot)})"))
    }

    fun unequip(data: GameData, state: GameState, slot: String): Result {
        val p = state.player
        val uid = p.equipment[slot] ?: return Result(state)
        val name = p.inventory.firstOrNull { it.uid == uid }?.let { data.itemById[it.itemId]?.name } ?: "Gegenstand"
        return Result(state.copy(player = p.copy(equipment = p.equipment - slot)), listOf("$name abgelegt"))
    }

    fun use(data: GameData, state: GameState, uid: Long): Result {
        val st = state.player.inventory.firstOrNull { it.uid == uid } ?: return state.fail("Nicht im Inventar")
        val def = data.itemById[st.itemId] ?: return state.fail("Unbekannt")
        val eff = def.effect ?: return state.fail("${def.name} kann nicht verbraucht werden.")
        val medBonus = (state.player.skills["first_aid"] ?: 0) * 3
        var s = removeById(state, st.itemId, 1)
        val p = s.player
        val heal = if (eff.health > 0) eff.health + medBonus else eff.health
        val wounds = if (eff.health >= 20 && p.wounds > 0) p.wounds - 1 else p.wounds
        s = s.copy(player = p.copy(
            health = (p.health + heal).coerceIn(1, 100), energy = (p.energy + eff.energy).pct(),
            stress = (p.stress + eff.stress).pct(), wounds = wounds
        ))
        val parts = mutableListOf<String>()
        if (heal != 0) parts += "Gesundheit ${Effs.signed(heal)}"
        if (eff.energy != 0) parts += "Energie ${Effs.signed(eff.energy)}"
        if (eff.stress != 0) parts += "Stress ${Effs.signed(eff.stress)}"
        return Result(s, listOf("${def.name} verwendet: " + parts.joinToString(", ")))
    }

    fun toStorage(data: GameData, state: GameState, uid: Long): Result {
        val p = state.player
        val st = p.inventory.firstOrNull { it.uid == uid } ?: return Result(state)
        if (uid in p.equipment.values) return state.fail("Ausgerüstete Gegenstände zuerst ablegen.")
        if (st.itemId == "id_card") return state.fail("Den Division-Ausweis trägst du immer bei dir.")
        val next = p.copy(inventory = p.inventory.filter { it.uid != uid }, storage = p.storage + st)
        return Result(state.copy(player = next), listOf("${data.itemById[st.itemId]?.name} im Spind verstaut"))
    }

    fun fromStorage(data: GameData, state: GameState, uid: Long): Result {
        val p = state.player
        val st = p.storage.firstOrNull { it.uid == uid } ?: return Result(state)
        val w = weight(data, p.inventory) + (data.itemById[st.itemId]?.weightGrams ?: 0) * st.quantity
        if (w > capacity(data, state)) return state.fail("Zu schwer – Tragkraft ${capacity(data, state) / 1000.0} kg")
        val next = p.copy(storage = p.storage.filter { it.uid != uid }, inventory = p.inventory + st)
        return Result(state.copy(player = next), listOf("${data.itemById[st.itemId]?.name} aus dem Spind genommen"))
    }

    fun price(def: ItemDefinition): Int = (def.baseValue.coerceAtLeast(10) * 0.6).toInt()

    /** Preis nach Rangrabatt (ab Rang 6: −10 %). Rückgabe bleibt immer darunter. */
    fun price(def: ItemDefinition, state: GameState): Int = price(def) * (100 - Career.shopDiscount(state)) / 100

    fun shopItems(data: GameData, state: GameState): List<ItemDefinition> =
        data.items.filter { it.buyable && it.baseValue > 0 }

    fun buy(data: GameData, state: GameState, itemId: String): Result {
        val def = data.itemById[itemId] ?: return state.fail("Unbekannt")
        if (!def.buyable) return state.fail("Nicht über die Ausrüstungskammer erhältlich")
        if (state.player.rank < def.minRank) return state.fail("Freigabe ab Rang ${def.minRank}")
        val price = price(def, state)
        if (state.player.credits < price) return state.fail("Nicht genug Credits ($price benötigt)")
        val s = state.copy(player = state.player.copy(credits = state.player.credits - price))
        val r = add(data, s, itemId, 1)
        return r.copy(messages = listOf("Angefordert: ${def.name} für $price Cr") + r.messages.drop(1))
    }

    fun sell(data: GameData, state: GameState, uid: Long): Result {
        val st = state.player.inventory.firstOrNull { it.uid == uid } ?: return Result(state)
        val def = data.itemById[st.itemId] ?: return Result(state)
        if (def.baseValue <= 0) return state.fail("${def.name} kann nicht zurückgegeben werden.")
        if (uid in state.player.equipment.values) return state.fail("Erst ablegen.")
        val value = (def.baseValue * 0.3 * st.condition / 100.0).toInt().coerceAtLeast(1)
        var s = removeById(state, st.itemId, 1)
        s = s.copy(player = s.player.copy(credits = s.player.credits + value))
        return Result(s, listOf("${def.name} zurückgegeben: +$value Cr"))
    }

    fun repair(data: GameData, state: GameState, uid: Long): Result {
        val st = state.player.inventory.firstOrNull { it.uid == uid } ?: return Result(state)
        if (st.condition >= 100) return state.fail("Zustand bereits einwandfrei.")
        val def = data.itemById[st.itemId] ?: return Result(state)
        val cost = ((100 - st.condition) * def.baseValue.coerceAtLeast(50) / 400).coerceAtLeast(5)
        if (state.player.credits < cost) return state.fail("Reparatur kostet $cost Cr")
        val p = state.player
        val next = p.copy(credits = p.credits - cost, inventory = p.inventory.map { if (it.uid == uid) it.copy(condition = 100) else it })
        return Result(state.copy(player = next), listOf("${def.name} gewartet (−$cost Cr)"))
    }

    /** Verschleiß nach Einsätzen für ausgerüstete Gegenstände. */
    fun wear(state: GameState, amount: Int): GameState {
        val p = state.player
        val eq = p.equipment.values.toSet()
        return state.copy(player = p.copy(inventory = p.inventory.map {
            if (it.uid in eq) it.copy(condition = (it.condition - amount).coerceAtLeast(0)) else it
        }))
    }

    // --- Ausrüstungsprofile (v0.5) -------------------------------------------------

    fun saveLoadout(data: GameData, state: GameState, loadoutId: String): Result {
        val p = state.player
        val items = p.equipment.mapNotNull { (slot, uid) -> p.inventory.firstOrNull { it.uid == uid }?.let { slot to it.itemId } }.toMap()
        if (items.isEmpty()) return state.fail("Nichts ausgerüstet – Profil bleibt leer.")
        val list = state.loadouts.map { if (it.id == loadoutId) it.copy(items = items) else it }
        val name = list.firstOrNull { it.id == loadoutId }?.name ?: loadoutId
        return Result(state.copy(loadouts = list), listOf("Profil „$name“ gespeichert (${items.size} Slots)."))
    }

    /**
     * Profil anlegen: Gegenstände aus Inventar oder Spind (sofern tragbar),
     * Freigaben werden geprüft; fehlende Teile werden gemeldet.
     */
    fun applyLoadout(data: GameData, state: GameState, loadoutId: String): Result {
        val lo = state.loadouts.firstOrNull { it.id == loadoutId } ?: return state.fail("Profil unbekannt")
        if (lo.items.isEmpty()) return state.fail("Profil „${lo.name}“ ist noch leer. Erst Ausrüstung anlegen und speichern.")
        if (state.activeMission != null) return state.fail("Während eines Einsatzes kann das Profil nicht gewechselt werden.")
        var s = state.copy(player = state.player.copy(equipment = emptyMap()))
        val msgs = mutableListOf<String>()
        val used = mutableSetOf<Long>()
        lo.items.forEach { (slot, itemId) ->
            val name = data.itemById[itemId]?.name ?: itemId
            var st = s.player.inventory.firstOrNull { it.itemId == itemId && it.uid !in used }
            if (st == null) {
                val stored = s.player.storage.firstOrNull { it.itemId == itemId }
                if (stored != null) {
                    val r = fromStorage(data, s, stored.uid)
                    if (r.ok) { s = r.state; st = s.player.inventory.firstOrNull { it.uid == stored.uid } } else msgs += "$name: ${r.messages.first()}"
                } else msgs += "$name fehlt (weder Inventar noch Spind)."
            }
            if (st != null) {
                val reason = canEquip(data, s, st)
                if (reason != null) msgs += "$name: $reason"
                else { s = s.copy(player = s.player.copy(equipment = s.player.equipment + (slot to st.uid))); used += st.uid }
            }
        }
        val ok = s.player.equipment.size
        return Result(s, listOf("Profil „${lo.name}“ angelegt: $ok/${lo.items.size} Slots.") + msgs)
    }

    data class Comparison(val label: String, val current: String, val candidate: String, val delta: Int)

    /** Direkter Vergleich mit dem aktuell ausgerüsteten Gegenstand im selben Slot. */
    fun compare(data: GameData, state: GameState, uid: Long): List<Comparison> {
        val p = state.player
        val st = p.inventory.firstOrNull { it.uid == uid } ?: p.storage.firstOrNull { it.uid == uid } ?: return emptyList()
        val def = data.itemById[st.itemId] ?: return emptyList()
        if (def.slot == "none") return emptyList()
        val curUid = p.equipment[def.slot]
        val curSt = p.inventory.firstOrNull { it.uid == curUid }
        val cur = curSt?.let { data.itemById[it.itemId] }
        val out = mutableListOf<Comparison>()
        Attr.all.forEach { a ->
            val c = cur?.bonuses?.get(a) ?: 0; val n = def.bonuses[a] ?: 0
            if (c != 0 || n != 0) out += Comparison(Attr.label(a), Effs.signed(c), Effs.signed(n), n - c)
        }
        if ((cur?.armor ?: 0) != 0 || def.armor != 0) out += Comparison("Schutz", "${cur?.armor ?: 0}", "${def.armor}", def.armor - (cur?.armor ?: 0))
        if ((cur?.damage ?: 0) != 0 || def.damage != 0) out += Comparison("Wirkung", "${cur?.damage ?: 0}", "${def.damage}", def.damage - (cur?.damage ?: 0))
        if ((cur?.capacityBonusGrams ?: 0) != 0 || def.capacityBonusGrams != 0) out += Comparison("Tragkraft (g)", "${cur?.capacityBonusGrams ?: 0}", "${def.capacityBonusGrams}", (def.capacityBonusGrams - (cur?.capacityBonusGrams ?: 0)) / 500)
        out += Comparison("Gewicht (g)", "${cur?.weightGrams ?: 0}", "${def.weightGrams}", ((cur?.weightGrams ?: 0) - def.weightGrams) / 200)
        out += Comparison("Zustand", "${curSt?.condition ?: 0}%", "${st.condition}%", (st.condition - (curSt?.condition ?: 0)) / 10)
        return out
    }

    fun equippedName(data: GameData, state: GameState, slot: String): String? =
        state.player.equipment[slot]?.let { uid -> state.player.inventory.firstOrNull { it.uid == uid } }?.let { data.itemById[it.itemId]?.name }
}

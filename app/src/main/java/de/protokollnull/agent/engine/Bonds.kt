package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * Strukturierte Erinnerungen (v0.6). Jede neue Erinnerung, die über
 * [Relations.update] entsteht, wird mit Tag, Typ und Gewicht abgelegt.
 * Ältere Einträge werden zu Zusammenfassungen verdichtet, damit der
 * Spielstand auch nach Jahren klein bleibt, ohne die Geschichte zu verlieren.
 */
object Memories {
    const val ENTRY_CAP = 24
    const val BATCH = 12
    const val SUMMARY_CAP = 10

    fun book(state: GameState, id: String): MemoryBook = state.living.memories[id] ?: MemoryBook()

    fun typeLabel(type: String) = when (type) {
        "gift" -> "Geschenke"
        "mission" -> "Einsätze"
        "training" -> "Training"
        "conflict" -> "Konflikte"
        "romance" -> "Beziehung"
        "trust" -> "Vertrauen"
        "milestone" -> "Meilensteine"
        "hq" -> "HQ-Ereignisse"
        "career" -> "Laufbahn"
        "legacy" -> "Frühere Erinnerungen"
        else -> "Gemeinsame Momente"
    }

    fun infer(text: String): Pair<String, Int> {
        val t = text.lowercase()
        return when {
            "geschenk" in t -> "gift" to 2
            listOf("antrag", "paar", "wohnung", "familie", "gefühle", "ausgesprochen").any { it in t } -> "romance" to 5
            listOf("streit", "konflikt", "unbeholfen", "enttäusch", "vorwurf").any { it in t } -> "conflict" to 3
            listOf("einsatz", "mission", "zugriff").any { it in t } -> "mission" to 3
            listOf("pakt", "geheimnis", "vertrau", "versprechen").any { it in t } -> "trust" to 4
            listOf("training", "sparring", "trainier", "wettkampf").any { it in t } -> "training" to 2
            listOf("laufbahn", "prüfung", "beförder").any { it in t } -> "career" to 3
            "meilenstein" in t -> "milestone" to 4
            else -> "moment" to 2
        }
    }

    /** Legt eine Erinnerung strukturiert ab (ohne die Beziehungswerte anzufassen). */
    fun record(state: GameState, id: String, text: String, type: String? = null, weight: Int? = null): GameState {
        val (t, w) = infer(text)
        val entry = MemoryEntry(state.clock.day, type ?: t, text, (weight ?: w).coerceIn(1, 5))
        val book = book(state, id)
        val updated = compact(book.copy(entries = book.entries + entry))
        return state.copy(living = state.living.copy(memories = state.living.memories + (id to updated)))
    }

    /** Fügt eine Erinnerung hinzu – auch in die klassische Liste der Beziehung. */
    fun add(data: GameData, state: GameState, id: String, text: String, type: String? = null, weight: Int? = null): GameState {
        if (!Relations.isKnownNpc(data, state, id)) return state
        val s = record(state, id, text, type, weight)
        val r = Relations.get(data, s, id)
        return s.copy(relations = s.relations + (id to r.copy(memories = (r.memories + text).takeLast(12))))
    }

    fun compact(book: MemoryBook): MemoryBook {
        if (book.entries.size <= ENTRY_CAP) return book
        val old = book.entries.take(BATCH)
        val rest = book.entries.drop(BATCH)
        var summaries = book.summaries + summarize(old)
        while (summaries.size > SUMMARY_CAP) {
            val a = summaries[0]; val b = summaries[1]
            summaries = listOf(merge(a, b)) + summaries.drop(2)
        }
        return MemoryBook(rest, summaries)
    }

    fun summarize(entries: List<MemoryEntry>): MemorySummary {
        val types = entries.groupingBy { it.type }.eachCount()
        val highlight = entries.maxByOrNull { it.weight * 100 + it.day }?.text.orEmpty()
        val days = entries.map { it.day }.filter { it >= 0 }
        val from = days.minOrNull() ?: -1
        val to = days.maxOrNull() ?: -1
        val parts = types.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.value}× ${typeLabel(it.key)}" }
        return MemorySummary(from, to, entries.size, parts, types, highlight)
    }

    fun merge(a: MemorySummary, b: MemorySummary): MemorySummary {
        val types = (a.types.keys + b.types.keys).associateWith { (a.types[it] ?: 0) + (b.types[it] ?: 0) }
        val parts = types.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.value}× ${typeLabel(it.key)}" }
        val from = listOf(a.fromDay, b.fromDay).filter { it >= 0 }.minOrNull() ?: -1
        return MemorySummary(from, maxOf(a.toDay, b.toDay), a.count + b.count, parts, types, a.highlight.ifBlank { b.highlight })
    }

    /** Wandelt die Textlisten älterer Spielstände in strukturierte Einträge um. */
    fun migrate(state: GameState): GameState {
        var books = state.living.memories
        state.relations.forEach { (id, r) ->
            if (r.memories.isNotEmpty() && books[id] == null) {
                books = books + (id to MemoryBook(r.memories.map { val (t, w) = infer(it); MemoryEntry(-1, if (t == "moment") "legacy" else t, it, w) }))
            }
        }
        return state.copy(living = state.living.copy(memories = books))
    }

    /** Gewicht gemeinsamer Erinnerungen (für Kompatibilität und Akte). */
    fun weight(state: GameState, id: String): Int {
        val b = book(state, id)
        return b.entries.sumOf { it.weight } + b.summaries.sumOf { it.count * 2 }
    }
}

/**
 * Beziehungen 2.0: Kompatibilität, Meilensteine, Konflikte und Geschenke,
 * die zu den Interessen einer Figur passen.
 */
object Bonds {
    val milestoneNames = mapOf(
        "first_talk" to "Erstes Gespräch",
        "friends" to "Freundschaft",
        "close_friends" to "Enge Freundschaft",
        "trusted" to "Volles Vertrauen",
        "respected" to "Gegenseitiger Respekt",
        "rivals" to "Rivalität",
        "conflict_resolved" to "Konflikt beigelegt",
        "first_gift" to "Erstes Geschenk",
        "perfect_gift" to "Das perfekte Geschenk",
        "interest" to "Gegenseitiges Interesse",
        "partner" to "Feste Beziehung",
        "stay_friends" to "Bewusst Freunde geblieben",
        "breakup" to "Trennung",
        "mentor" to "Mentoring",
        "career_talk" to "Laufbahngespräch"
    )

    /** Welche Interessen ein Gegenstand anspricht. */
    val giftTags = mapOf(
        "camera" to listOf("Fotografie", "Kunst"),
        "headset" to listOf("Musik", "Technik", "Spiele"),
        "notebook" to listOf("Bücher", "Schreiben", "Kunst", "Schach"),
        "watch" to listOf("Laufen", "Mode"),
        "flashlight" to listOf("Klettern"),
        "smartphone" to listOf("Technik", "Spiele", "Sprachen"),
        "wallet" to listOf("Mode"),
        "binoculars" to listOf("Klettern", "Fotografie"),
        "rope" to listOf("Klettern"),
        // v1.0: eigene Geschenkartikel mit klarer Zielgruppe
        "gift_vinyl" to listOf("Musik"),
        "gift_artbook" to listOf("Kunst", "Bücher", "Fotografie"),
        "gift_chess" to listOf("Schach", "Spiele"),
        "gift_spices" to listOf("Kochen"),
        "gift_phrasebook" to listOf("Sprachen", "Bücher"),
        "gift_sketchbook" to listOf("Zeichnen", "Schreiben", "Kunst"),
        "gear_running_shoes" to listOf("Laufen"),
        "gear_tea_flask" to listOf("Kochen")
    )
    val giftItems = giftTags.keys

    /** Implizite Interessen aus dem Hintergrund des Spielers. */
    private fun playerInterests(state: GameState): Set<String> {
        val p = state.player
        val implied = when (p.background) {
            "athlete" -> listOf("Laufen", "Klettern")
            "analyst" -> listOf("Schach", "Bücher")
            "social" -> listOf("Musik", "Sprachen")
            "marksman" -> listOf("Klettern", "Fotografie")
            else -> emptyList()
        }
        return (p.interests + implied).toSet()
    }

    data class Compat(val score: Int, val reasons: List<String>)

    /** Passung zwischen Spieler und Figur (0..100) mit Begründung. */
    fun compatibility(data: GameData, state: GameState, id: String): Compat {
        val reasons = mutableListOf<String>()
        var score = 40
        val shared = playerInterests(state) intersect Life.interests(data, state, id).toSet()
        if (shared.isNotEmpty()) { score += shared.size * 12; reasons += "Gemeinsame Interessen: " + shared.joinToString() }
        val traits = Life.traits(data, state, id)
        val r = Relations.get(data, state, id)
        if ("loyal" in traits && r.loyalty >= 40) { score += 6; reasons += "Loyalität beruht auf Gegenseitigkeit" }
        if (traits.any { it in setOf("ehrgeizig", "kompetitiv") } && state.player.rank >= Life.rank(data, state, id)) { score += 4; reasons += "Respekt vor deiner Laufbahn" }
        if ("neugierig" in traits && state.player.background == "analyst") { score += 6; reasons += "Neugier trifft Analyse" }
        val mem = (Memories.weight(state, id) / 6).coerceAtMost(20)
        if (mem > 0) { score += mem; reasons += "Gemeinsame Geschichte (+$mem)" }
        if (r.rivalry > 0) { score -= r.rivalry / 4; reasons += "Rivalität belastet" }
        state.living.conflicts[id]?.let { score -= it.severity * 8; reasons += "Ungelöster Konflikt: ${it.reason}" }
        if (reasons.isEmpty()) reasons += "Noch wenig gemeinsam – Zeit miteinander hilft"
        return Compat(score.coerceIn(0, 100), reasons)
    }

    fun milestones(state: GameState, id: String): Set<String> = state.living.milestones[id] ?: emptySet()

    fun addMilestone(data: GameData, state: GameState, id: String, m: String): GameState {
        if (m in milestones(state, id)) return state
        var s = state.copy(living = state.living.copy(milestones = state.living.milestones + (id to milestones(state, id) + m)))
        s = Memories.add(data, s, id, "Meilenstein: ${milestoneNames[m] ?: m}", "milestone", 4)
        return s
    }

    /** Wöchentliche Pflege: Meilensteine, Konflikte, Partnerschaft. */
    fun weekly(data: GameData, state: GameState, day: Int): GameState {
        var s = state
        s.relations.keys.toList().forEach { id ->
            if (!Relations.isKnownNpc(data, s, id)) return@forEach
            val r = Relations.get(data, s, id)
            if (r.friendship >= 50) s = addMilestone(data, s, id, "friends")
            if (r.friendship >= 80) s = addMilestone(data, s, id, "close_friends")
            if (r.trust >= 75) s = addMilestone(data, s, id, "trusted")
            if (r.respect >= 70) s = addMilestone(data, s, id, "respected")
            if (r.rivalry >= 40) s = addMilestone(data, s, id, "rivals")
            // Partnerschaft braucht Zeit füreinander
            if (r.romanceStage >= 2 && r.lastTalkDay >= 0 && day - r.lastTalkDay > 21 && id !in s.living.conflicts) {
                s = addConflict(data, s, id, "Ihr hattet kaum Zeit füreinander", 1, "neglect")
            }
        }
        // Ungelöste Konflikte zehren – und verblassen nach langer Zeit
        s.living.conflicts.forEach { (id, c) ->
            s = Relations.update(data, s, id) { it.copy(friendship = it.friendship - c.severity) }
            if (day - c.sinceDay > 75 && c.severity <= 1) {
                s = s.copy(living = s.living.copy(conflicts = s.living.conflicts - id))
                s = Memories.add(data, s, id, "Ein Streit, der nie ganz ausgesprochen wurde", "conflict", 2)
            } else if (day - c.sinceDay > 75) {
                s = s.copy(living = s.living.copy(conflicts = s.living.conflicts + (id to c.copy(severity = c.severity - 1, sinceDay = day))))
            }
        }
        return s
    }

    fun addConflict(data: GameData, state: GameState, id: String, reason: String, severity: Int = 1, source: String = ""): GameState {
        if (!Relations.isKnownNpc(data, state, id)) return state
        val existing = state.living.conflicts[id]
        val c = if (existing != null) existing.copy(severity = (existing.severity + 1).coerceAtMost(3), reason = reason)
        else Conflict(reason, state.clock.day, severity.coerceIn(1, 3), source)
        var s = state.copy(living = state.living.copy(conflicts = state.living.conflicts + (id to c)))
        s = Memories.add(data, s, id, "Konflikt: $reason", "conflict", 3)
        return s
    }

    fun resolveConflict(data: GameData, state: GameState, id: String): GameState {
        if (id !in state.living.conflicts) return state
        val s = state.copy(living = state.living.copy(conflicts = state.living.conflicts - id))
        return addMilestone(data, s, id, "conflict_resolved")
    }

    /** Aussprache: löst einen Konflikt per Empathie-Probe. */
    fun talkOut(data: GameData, state: GameState, id: String): Result {
        val c = state.living.conflicts[id] ?: return state.fail("Es gibt nichts auszusprechen.")
        val r = Relations.get(data, state, id)
        if (r.lastTalkDay == state.clock.day) return state.fail("Gebt euch etwas Zeit – morgen wieder.")
        if (state.player.energy < 10) return state.fail("Zu erschöpft für ein ehrliches Gespräch.")
        val (s0, roll) = Checks.roll(data, state, CheckDef(Attr.CHARISMA, "empathy", 30 + c.severity * 10), bonus = (r.trust - 40) / 4, salt = 61)
        val name = Relations.displayName(data, state, id)
        var s = Relations.update(data, s0, id) { it.copy(lastTalkDay = state.clock.day) }
        s = s.copy(player = s.player.copy(energy = (s.player.energy - 10).pct()))
        if (!roll.success) {
            s = s.copy(player = s.player.copy(stress = (s.player.stress + 5).pct()))
            val t = World.passTime(data, s, 1)
            return Result(t.state, listOf("Das Gespräch mit $name dreht sich im Kreis. (${roll.roll}/${roll.chance})", "Versucht es an einem anderen Tag noch einmal.") + t.messages, ok = false)
        }
        s = resolveConflict(data, s, id)
        s = Relations.update(data, s, id) { it.copy(trust = it.trust + 4, friendship = it.friendship + 3) }
        s = Memories.add(data, s, id, "Die Aussprache nach „${c.reason}“", "trust", 4)
        val t = World.passTime(data, s, 1)
        return Result(t.state, listOf("Ihr sprecht euch aus. Der Konflikt mit $name ist beigelegt. (${roll.roll}/${roll.chance})", "Vertrauen +4, Freundschaft +3") + t.messages)
    }

    data class GiftPreview(val itemId: String, val name: String, val reaction: String, val bonus: Int)

    fun giftOptions(data: GameData, state: GameState, id: String): List<GiftPreview> {
        val equipped = state.player.equipment.values.toSet()
        return state.player.inventory.filter { it.itemId in giftItems && it.uid !in equipped }
            .map { it.itemId }.distinct().map { preview(data, state, id, it) }
    }

    fun preview(data: GameData, state: GameState, id: String, itemId: String): GiftPreview {
        val tags = giftTags[itemId].orEmpty()
        val interests = Life.interests(data, state, id)
        val traits = Life.traits(data, state, id)
        val last = state.living.gifts[id]?.get(itemId)
        val item = data.itemById[itemId]?.name ?: itemId
        return when {
            last != null && state.clock.day - last < 60 -> GiftPreview(itemId, item, "Hat so etwas gerade erst bekommen", 1)
            tags.any { it in interests } -> GiftPreview(itemId, item, "Trifft genau ein Interesse (" + tags.first { it in interests } + ")", 10)
            traits.any { it in setOf("neugierig", "kreativ", "technik") } -> GiftPreview(itemId, item, "Wird neugierig ausprobiert", 5)
            else -> GiftPreview(itemId, item, "Nette Geste", 3)
        }
    }

    fun give(data: GameData, state: GameState, id: String, itemId: String): Result {
        if (itemId !in giftItems) return state.fail("Das eignet sich nicht als Geschenk.")
        val equipped = state.player.equipment.values.toSet()
        if (state.player.inventory.none { it.itemId == itemId && it.uid !in equipped }) return state.fail("Nicht im Inventar (oder ausgerüstet).")
        val pv = preview(data, state, id, itemId)
        val conflict = state.living.conflicts[id]
        val bonus = if (conflict != null) pv.bonus / 2 else pv.bonus
        var s = Inventory.removeById(state, itemId, 1)
        val romance = Romance.allowed(data, s, id)
        val perfect = pv.bonus >= 10
        s = Relations.update(data, s, id) { it.copy(friendship = it.friendship + bonus, affection = it.affection + if (romance) bonus / 2 else 0) }
        s = Memories.add(data, s, id, "Geschenk: ${pv.name}" + if (perfect) " – genau das Richtige" else "", "gift", if (perfect) 3 else 2)
        val gifts = (s.living.gifts[id] ?: emptyMap()) + (itemId to s.clock.day)
        s = s.copy(living = s.living.copy(gifts = s.living.gifts + (id to gifts)))
        s = addMilestone(data, s, id, "first_gift")
        if (perfect) s = addMilestone(data, s, id, "perfect_gift")
        if (perfect && conflict != null && conflict.severity <= 1) s = resolveConflict(data, s, id)
        val name = Relations.displayName(data, s, id)
        return Result(s, listOf("Du schenkst $name: ${pv.name}.", pv.reaction + " · Freundschaft +$bonus" + if (conflict != null) " (Konflikt dämpft die Freude)" else ""))
    }
}

/** HQ-Ereignisse mit echten Folgen (assets/game/hq_events.json). */
object HqEvents {
    const val EXPIRE_DAYS = 4

    fun pending(data: GameData, state: GameState): HqEventDefinition? =
        state.living.pendingHqEvent?.let { data.hqEventById[it] }

    fun fill(data: GameData, state: GameState, text: String, npcId: String?): String =
        text.replace("{name}", Relations.displayName(data, state, npcId).substringBefore(" "))
            .replace("{fullname}", Relations.displayName(data, state, npcId))
            .replace("{player}", state.player.identityName)

    fun eligible(data: GameData, state: GameState, e: HqEventDefinition, day: Int): Boolean {
        if (day < e.minDay) return false
        val lastSeen = state.living.hqHistory.lastOrNull { it.eventId == e.id }?.day
        if (lastSeen != null && (e.repeatDays <= 0 || day - lastSeen < e.repeatDays)) return false
        val involved = listOfNotNull(e.npcId) + e.involved + e.requirePresent + e.requireStatus.keys
        if (involved.any { !Relations.isKnownNpc(data, state, it) || !World.revealed(data, state, it) }) return false
        if (involved.any { id -> state.customNpcs.any { it.id == id && day < it.createdDay } }) return false
        if (CompanionAssignment.awaitingIntroduction(state)?.id?.let { it in involved } == true) return false
        if (e.npcId != null && data.agentById[e.npcId] != null && "met_${e.npcId}" !in state.flags && !state.relations.containsKey(e.npcId)) return false
        if (!Conds.met(e.conditions, data, state, e.npcId)) return false
        if (e.requireStatus.any { (id, st) -> (state.living.lives[id]?.status ?: LifeStatus.DUTY) != st }) return false
        if (e.requirePresent.any { !Life.inHq(data, state, it) }) return false
        if (e.minArc.any { (k, v) -> (state.living.arcs[k] ?: 0) < v }) return false
        if (e.minPlayerStress != null && state.player.stress < e.minPlayerStress) return false
        if (e.requireConflict != null && e.requireConflict !in state.living.conflicts) return false
        return true
    }

    fun daily(data: GameData, state: GameState, day: Int): GameState {
        var s = state
        val pend = s.living.pendingHqEvent
        if (pend != null) {
            if (day - s.living.pendingHqEventDay < EXPIRE_DAYS) return s
            // Verpasst: wer nicht reagiert, entscheidet auch – über „ignore“
            val e = data.hqEventById[pend]
            val ignore = e?.choices?.firstOrNull { it.id == "ignore" }
            s = if (e != null && ignore != null) applyChoice(data, s, e, ignore, success = true).state
            else s.copy(living = s.living.copy(pendingHqEvent = null, hqHistory = (s.living.hqHistory + HqEventRecord(day, pend, "expired", "verpasst")).takeLast(80)))
            return s.copy(living = s.living.copy(pendingHqEvent = null))
        }
        if (Ages.player(s) < 16 || s.activeMission != null) return s
        val rng = Rng(Rng.mix(s.seed, day.toLong(), 9001))
        if (rng.percent() > 14) return s
        val pool = data.hqEvents.filter { eligible(data, s, it, day) }
        if (pool.isEmpty()) return s
        val total = pool.sumOf { it.weight.coerceAtLeast(1) }
        var pick = rng.nextInt(total)
        val chosen = pool.first { pick -= it.weight.coerceAtLeast(1); pick < 0 }
        return s.copy(living = s.living.copy(pendingHqEvent = chosen.id, pendingHqEventDay = day))
    }

    fun visibleChoices(data: GameData, state: GameState, e: HqEventDefinition) =
        e.choices.filter { it.id != "ignore" && Conds.met(it.conditions, data, state, e.npcId) }

    fun resolve(data: GameData, state: GameState, choiceId: String): Result {
        val e = pending(data, state) ?: return state.fail("Kein Ereignis offen.")
        val c = visibleChoices(data, state, e).firstOrNull { it.id == choiceId } ?: return state.fail("Diese Option ist nicht verfügbar.")
        var s = state
        var success = true
        var rollText = ""
        if (c.check != null) {
            val (s1, roll) = Checks.roll(data, s, c.check, salt = e.id.hashCode().toLong())
            s = s1; success = roll.success
            rollText = " (Probe ${roll.roll}/${roll.chance})"
        }
        val r = applyChoice(data, s, e, c, success)
        val t = World.passTime(data, r.state, 1)
        val text = fill(data, state, if (success) c.result else (c.failResult ?: c.result), e.npcId) + rollText
        return Result(t.state, listOf(text) + r.messages + t.messages)
    }

    private fun applyChoice(data: GameData, state: GameState, e: HqEventDefinition, c: HqChoice, success: Boolean): Result {
        var s = state
        val msgs = mutableListOf<String>()
        val effects = if (success) c.effects else (c.failEffects ?: c.effects)
        val npcEffects = if (success) c.npcEffects else c.failNpcEffects.ifEmpty { c.npcEffects }
        Effs.apply(effects, data, s, e.npcId).let { s = it.state; msgs += it.messages }
        npcEffects.forEach { (id, eff) -> Effs.apply(eff, data, s, id).let { s = it.state; msgs += it.messages } }
        (if (success) c.life else c.failLife.ifEmpty { c.life }).forEach { s = Life.apply(data, s, it) }
        if (success) {
            c.conflicts.forEach { (id, reason) -> s = Bonds.addConflict(data, s, id, fill(data, s, reason, e.npcId), 1, e.id); msgs += Relations.displayName(data, s, id) + ": Konflikt – " + fill(data, s, reason, e.npcId) }
            c.resolveConflicts.forEach { s = Bonds.resolveConflict(data, s, it) }
            c.memories.forEach { (id, m) -> s = Memories.add(data, s, id, fill(data, s, m, e.npcId), "hq", 3) }
        }
        val arcs = s.living.arcs.toMutableMap()
        c.arcs.forEach { (k, v) -> arcs[k] = (arcs[k] ?: 0) + v }
        val text = fill(data, s, e.title, e.npcId) + ": " + fill(data, s, c.text, e.npcId)
        s = s.copy(living = s.living.copy(
            arcs = arcs, pendingHqEvent = null,
            hqHistory = (s.living.hqHistory + HqEventRecord(s.clock.day, e.id, c.id, text)).takeLast(80)
        ))
        s = Life.feed(data, s, LifeEvent(s.clock.day, e.npcId ?: "player", "hq", fill(data, s, e.title, e.npcId)))
        return Result(s, msgs)
    }
}

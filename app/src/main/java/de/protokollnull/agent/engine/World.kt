package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

object World {

    // --- Zeit -----------------------------------------------------------------

    /** Lässt [slots] Zeitslots vergehen. Über Mitternacht ohne Schlaf: Erschöpfung. */
    fun passTime(data: GameData, state: GameState, slots: Int): Result {
        var s = state
        val msgs = mutableListOf<String>()
        repeat(slots.coerceIn(0, 12)) {
            var c = s.clock
            var p = s.player
            p = p.copy(energy = (p.energy - 3).pct())
            if (c.slot >= 5) {
                // Nacht durchgemacht
                val r = newDay(data, s.copy(player = p), sleptWell = false)
                s = r.state; msgs += r.messages
                msgs += "Die Nacht ohne Schlaf zehrt an dir (Energie −20)."
                s = s.copy(player = s.player.copy(energy = (s.player.energy - 20).pct()))
            } else {
                c = c.copy(slot = c.slot + 1)
                s = s.copy(clock = c, player = p)
            }
        }
        return Result(s, msgs)
    }

    fun sleep(data: GameData, state: GameState): Result {
        if (state.clock.slot < 3) return state.fail("Zum Schlafen ist es noch zu früh. Nutze „Ausruhen“.")
        val p = state.player
        val bonus = if (p.apartment) 10 else 0
        var s = state.copy(player = p.copy(
            energy = (p.energy + 70 + bonus).pct(),
            stress = (p.stress - 12 - bonus / 2).pct(),
            health = (p.health + 6 + (if (p.wounds == 0) 4 else 0)).coerceAtMost(100)
        ))
        val r = newDay(data, s, sleptWell = true)
        return Result(r.state, listOf("Du schläfst bis zum Morgen. Energie aufgefüllt.") + r.messages)
    }

    fun rest(data: GameData, state: GameState): Result {
        val p = state.player
        val s = state.copy(player = p.copy(energy = (p.energy + 25).pct(), stress = (p.stress - 6).pct()))
        val t = passTime(data, s, 1)
        return Result(t.state, listOf("Du ruhst dich aus. Energie +25, Stress −6") + t.messages)
    }

    private fun newDay(data: GameData, state: GameState, sleptWell: Boolean): Result {
        val oldAge = Ages.player(state)
        val day = state.clock.day + 1
        var s = state.copy(clock = GameClock(day = day, slot = 0))
        val msgs = mutableListOf<String>()
        msgs += "— " + Clock.dateLabel(day) + " —"
        // Wunden heilen langsam
        val p = s.player
        if (p.wounds > 0 && day % 3 == 0) s = s.copy(player = p.copy(wounds = p.wounds - 1))
        val newAge = Ages.player(s)
        if (newAge > oldAge) {
            msgs += "Geburtstag! Du bist jetzt $newAge."
            s = s.copy(flags = s.flags + "age_$newAge")
            if (newAge == 18) msgs += "Volljährig: Operative Einsätze, eigene Wohnung und der Rekrutierungseditor sind freigeschaltet."
        }
        if (day % 7 == 0) {
            val w = weeklyTick(data, s)
            s = w.state; msgs += w.messages
        }
        return Result(s, msgs)
    }

    fun weeklyTick(data: GameData, state: GameState): Result {
        var s = state
        val msgs = mutableListOf<String>()
        val rank = data.rank(s.player.rank)
        val salary = rank.salaryPerWeek + (if (s.player.coverJob != null) 120 else 0)
        val rent = if (s.player.apartment) 150 else 0
        s = s.copy(player = s.player.copy(credits = (s.player.credits + salary - rent).coerceAtLeast(0)))
        msgs += "Wochensold: +$salary Cr" + if (rent > 0) ", Miete −$rent Cr" else ""
        s = MissionGenerator.refreshBoard(data, s)
        s.director?.let {
            val r = Director.weekly(data, s)
            s = r.state; msgs += r.messages
        }
        return Result(s, msgs)
    }

    // --- Zeitraffer -----------------------------------------------------------

    data class Routine(val id: String, val name: String, val description: String, val attributes: List<String>, val social: Boolean)

    val routines = listOf(
        Routine("balanced", "Ausgewogen", "Etwas von allem – kein Bereich wird vernachlässigt.", Attr.all, true),
        Routine("physical", "Körperliches Training", "Fitness und Präzision, mehr Stress.", listOf(Attr.FITNESS, Attr.PRECISION), false),
        Routine("academic", "Theorie & Technik", "Intellekt und Wahrnehmung.", listOf(Attr.INTELLECT, Attr.PERCEPTION), false),
        Routine("social", "Kontakte pflegen", "Ausstrahlung, Willenskraft, Beziehungen.", listOf(Attr.CHARISMA, Attr.WILLPOWER), true)
    )

    /**
     * Überspringt [days] Tage in geschlossener Form (wochenweise, keine
     * Tagesschleife über Jahre). Die gewählte Routine bestimmt den Fortschritt.
     */
    fun timeSkip(data: GameData, state: GameState, days: Int): Result {
        if (state.activeMission != null) return state.fail("Erst den laufenden Einsatz beenden.")
        val d = days.coerceIn(1, 730)
        val routine = routines.firstOrNull { it.id == state.routine } ?: routines.first()
        val oldAge = Ages.player(state)
        val weeks = d / 7.0
        val rng = Rng(Rng.mix(state.seed, state.clock.day.toLong(), d.toLong(), 4242))
        var p = state.player
        val cap = Stats.attributeCap(p.level)
        // Attributfortschritt mit abnehmendem Ertrag
        val gains = mutableMapOf<String, Int>()
        routine.attributes.forEach { a ->
            val cur = p.attributes[a] ?: 30
            val speed = if (routine.id == "balanced") 0.45 else 0.9
            val raw = (weeks * speed * (1.0 - (cur - 20).coerceAtLeast(0) / 100.0)).toInt()
            val next = (cur + raw).coerceAtMost(cap).coerceAtLeast(cur)
            if (next > cur) gains[a] = next - cur
            p = p.copy(attributes = p.attributes + (a to next))
        }
        val weekCount = (d / 7).coerceAtLeast(0)
        var s = state.copy(player = p)
        val msgs = mutableListOf("Zeitraffer: $d Tage mit Routine „${routine.name}“.")
        gains.forEach { (a, g) -> msgs += Attr.label(a) + " +" + g }
        // XP: Ausbildungsalltag
        val xp = (d * (18 + p.level)).coerceAtMost(200000)
        val xr = Progression.grantXp(data, s, xp)
        s = xr.state
        msgs += xr.messages.filter { it.contains("Level") }.takeLast(3)
        msgs += "+$xp XP aus Ausbildung und Dienst"
        // Ruf & Beziehungen
        s = s.copy(player = s.player.copy(reputation = s.player.reputation + weekCount * 2))
        if (routine.social) {
            val known = (data.agents.map { it.id } + s.customNpcs.map { it.id })
            known.forEach { id ->
                s = Relations.update(data, s, id) { it.copy(friendship = it.friendship + (weekCount / 4).coerceAtMost(15), trust = it.trust + (weekCount / 6).coerceAtMost(10)) }
            }
            msgs += "Beziehungen vertieft (Freundschaft bis +${(weekCount / 4).coerceAtMost(15)})"
        }
        // Wöchentliche Abrechnung in geschlossener Form
        val rank = data.rank(s.player.rank)
        val salary = (rank.salaryPerWeek + (if (s.player.coverJob != null) 120 else 0) - (if (s.player.apartment) 150 else 0)) * weekCount
        s = s.copy(player = s.player.copy(credits = (s.player.credits + salary).coerceAtLeast(0)))
        if (weekCount > 0) msgs += "Sold abzüglich Miete: ${Effs.signed(salary)} Cr"
        s.director?.let {
            repeat(weekCount.coerceAtMost(104)) { s = Director.weekly(data, s).state }
            msgs += "Direktorat: Budget jetzt ${s.director?.budget} Cr"
        }
        // Zufallsereignis-Zusammenfassung
        val events = listOf(
            "Eine Sicherheitsübung in der Nacht hielt das Quartier wach.",
            "Birkhoff hat das Netzwerk umgebaut – niemand findet mehr etwas.",
            "Ein Rekrut wurde \"abgeschaltet\". Niemand spricht darüber.",
            "Amanda führte Einzelgespräche mit allen Rekruten.",
            "Michael verschärfte den Trainingsplan.",
            "Gerüchte über einen Maulwurf machten die Runde."
        )
        msgs += rng.pick(events)
        val newDay = s.clock.day + d
        s = s.copy(
            clock = GameClock(day = newDay, slot = 0),
            player = s.player.copy(energy = 90, stress = (s.player.stress - 10).coerceIn(5, 100), health = 100, wounds = 0)
        )
        val newAge = Ages.player(s)
        for (a in (oldAge + 1)..newAge) {
            s = s.copy(flags = s.flags + "age_$a")
            msgs += "Geburtstag: $a Jahre."
            if (a == 18) msgs += "Volljährig: Operative Einsätze und der Rekrutierungseditor sind freigeschaltet."
        }
        s = MissionGenerator.refreshBoard(data, s)
        msgs += "— " + Clock.dateLabel(newDay) + " —"
        return Result(s, msgs)
    }

    // --- HQ, Räume und NPCs ---------------------------------------------------

    fun roomLocked(data: GameData, state: GameState, room: RoomDefinition): String? {
        if (state.player.rank < room.minRank) return "Zutritt ab Rang ${room.minRank} (${data.rank(room.minRank).name})"
        if (Ages.player(state) < room.minAge) return "Zutritt ab ${room.minAge} Jahren"
        if (room.requiredItem != null && state.player.inventory.none { it.itemId == room.requiredItem }) return "Benötigt: " + (data.itemById[room.requiredItem]?.name ?: room.requiredItem)
        if (room.requiredFlag != null && room.requiredFlag !in state.flags) return "Noch nicht freigegeben"
        return null
    }

    fun moveTo(data: GameData, state: GameState, roomId: String): Result {
        val room = data.roomById[roomId] ?: return state.fail("Unbekannter Raum")
        roomLocked(data, state, room)?.let { return state.fail(it) }
        if (state.location == roomId) return Result(state)
        return Result(state.copy(location = roomId))
    }

    /** Wo hält sich ein NPC gerade auf? null = außer Haus / im Einsatz. */
    fun npcLocation(data: GameData, state: GameState, id: String): String? {
        val slotId = Clock.slotIds[state.clock.slot]
        val custom = state.customNpcs.firstOrNull { it.id == id }
        if (custom != null) {
            val plan = when (custom.profession) {
                "tech" -> listOf("dorm", "tech", "canteen", "tech", "canteen", "dorm")
                "medic" -> listOf("dorm", "medical", "canteen", "training", "canteen", "dorm")
                "intel" -> listOf("dorm", "ops", "canteen", "archives", "canteen", "dorm")
                else -> listOf("dorm", "training", "canteen", "range", "canteen", "dorm")
            }
            return plan[state.clock.slot]
        }
        val def = data.agentById[id] ?: return null
        if (Clock.isWeekend(state.clock.day) && def.ageAtStart > 20 && state.clock.slot >= 4) return null
        val room = def.schedule[slotId] ?: return null
        return if (room == "away") null else room
    }

    fun npcsIn(data: GameData, state: GameState, roomId: String): List<String> {
        val ids = data.agents.map { it.id } + state.customNpcs.map { it.id }
        return ids.filter { npcLocation(data, state, it) == roomId && revealed(data, state, it) }
    }

    fun revealed(data: GameData, state: GameState, id: String): Boolean {
        val flag = data.agentById[id]?.revealFlag ?: return true
        return flag in state.flags
    }

    data class RoomAction(val id: String, val label: String, val hint: String)

    fun actionInfo(id: String): RoomAction = when (id) {
        "sleep" -> RoomAction(id, "Schlafen", "Bis zum Morgen, Energie & Gesundheit")
        "rest" -> RoomAction(id, "Ausruhen", "1 Slot, Energie +25")
        "storage" -> RoomAction(id, "Spind öffnen", "Lager verwalten")
        "timeskip" -> RoomAction(id, "Zeitraffer", "Wochen/Monate mit Routine überspringen")
        "eat" -> RoomAction(id, "Essen", "1 Slot, Energie +20, Stress −4")
        "socialize" -> RoomAction(id, "Unter Leute gehen", "Freundschaft mit Anwesenden")
        "training" -> RoomAction(id, "Trainieren", "Attribute verbessern")
        "range" -> RoomAction(id, "Präzisionsstand", "Präzision trainieren")
        "treat" -> RoomAction(id, "Behandlung", "Gesundheit & Wunden, 2 Slots")
        "shop" -> RoomAction(id, "Ausrüstung anfordern", "Gegenstände für Credits")
        "repair" -> RoomAction(id, "Wartung", "Zustand der Ausrüstung")
        "missions" -> RoomAction(id, "Einsatzboard", "Missionen & Briefings")
        "tech_lab" -> RoomAction(id, "Systeme trainieren", "Intellekt, Technik")
        "therapy" -> RoomAction(id, "Gespräch mit der Psychologie", "Stress −30, 2 Slots")
        "promotion" -> RoomAction(id, "Beförderung beantragen", "Prüfung vor dem Gremium")
        "director_desk" -> RoomAction(id, "Direktorat führen", "Budget, Abteilungen, Politik")
        "research" -> RoomAction(id, "Akten durchsuchen", "Hinweise zu PHOENIX")
        "team" -> RoomAction(id, "Team zusammenstellen", "Einsatzteam verwalten")
        "cafe" -> RoomAction(id, "Café in der Stadt", "Stress −15, 1 Slot, 8 Cr")
        "apartment" -> RoomAction(id, "Eigene Wohnung", "Mieten ab 18 (150 Cr/Woche)")
        "cover_job" -> RoomAction(id, "Tarnjob", "Ziviles Doppelleben, +120 Cr/Woche")
        "study" -> RoomAction(id, "Lernen", "Theorie & Sprachen")
        else -> RoomAction(id, id, "")
    }

    fun eat(data: GameData, state: GameState): Result {
        val p = state.player
        val s = state.copy(player = p.copy(energy = (p.energy + 20).pct(), stress = (p.stress - 4).pct()))
        val t = passTime(data, s, 1)
        return Result(t.state, listOf("Du isst in der Kantine. Energie +20") + t.messages)
    }

    fun socialize(data: GameData, state: GameState): Result {
        val present = npcsIn(data, state, state.location)
        if (present.isEmpty()) return state.fail("Gerade ist niemand hier.")
        var s = state
        present.forEach { id -> s = Relations.update(data, s, id) { it.copy(friendship = it.friendship + 2) } }
        s = s.copy(player = s.player.copy(stress = (s.player.stress - 5).pct()))
        val t = passTime(data, s, 1)
        return Result(t.state, listOf("Du setzt dich zu " + present.joinToString { Relations.displayName(data, s, it) } + ". Freundschaft +2") + t.messages)
    }

    fun treat(data: GameData, state: GameState): Result {
        val p = state.player
        val med = (state.director?.departments?.get("medical") ?: 1) * 5
        val s = state.copy(player = p.copy(health = (p.health + 30 + med).coerceAtMost(100), wounds = (p.wounds - 1).coerceAtLeast(0), stress = (p.stress - 5).pct()))
        val t = passTime(data, s, 2)
        return Result(t.state, listOf("Die Krankenstation versorgt dich. Gesundheit +${30 + med}") + t.messages)
    }

    fun therapy(data: GameData, state: GameState): Result {
        val p = state.player
        var s = state.copy(player = p.copy(stress = (p.stress - 30).pct()))
        s = Relations.update(data, s, "amanda") { it.copy(trust = it.trust + 1) }
        val t = passTime(data, s, 2)
        return Result(t.state, listOf("Amanda hört zu – und notiert mehr, als du erzählst. Stress −30") + t.messages)
    }

    fun cafe(data: GameData, state: GameState): Result {
        if (state.player.credits < 8) return state.fail("Nicht genug Credits.")
        val p = state.player
        val s = state.copy(player = p.copy(stress = (p.stress - 15).pct(), credits = p.credits - 8))
        val t = passTime(data, s, 1)
        return Result(t.state, listOf("Ein normaler Kaffee unter normalen Menschen. Stress −15") + t.messages)
    }

    fun rentApartment(data: GameData, state: GameState): Result {
        if (Ages.player(state) < 18) return state.fail("Eine eigene Wohnung gibt es erst ab 18.")
        if (state.player.apartment) return state.fail("Du hast bereits eine Wohnung.")
        if (state.player.credits < 450) return state.fail("Kaution 450 Cr benötigt.")
        val p = state.player
        return Result(state.copy(player = p.copy(apartment = true, credits = p.credits - 450)), listOf("Du mietest eine kleine Wohnung in der Stadt (Kaution 450 Cr, 150 Cr/Woche). Besserer Schlaf, mehr Privatsphäre."))
    }

    fun takeCoverJob(data: GameData, state: GameState): Result {
        if (Ages.player(state) < 17) return state.fail("Ab 17 genehmigt die Division einen Tarnjob.")
        val p = state.player
        return if (p.coverJob == null) Result(state.copy(player = p.copy(coverJob = "Fahrradkurier")), listOf("Tarnjob: Fahrradkurier. +120 Cr/Woche und eine glaubwürdige zivile Identität."))
        else Result(state.copy(player = p.copy(coverJob = null)), listOf("Tarnjob gekündigt."))
    }

    fun research(data: GameData, state: GameState): Result {
        if (state.player.energy < 10) return state.fail("Zu erschöpft.")
        val (s0, roll) = Checks.roll(data, state, CheckDef(Attr.INTELLECT, "analysis", 50), salt = 31)
        var s = s0.copy(player = s0.player.copy(energy = (s0.player.energy - 10).pct()))
        val clues = listOf("phoenix_clue_1", "phoenix_clue_2", "phoenix_clue_3")
        val next = clues.firstOrNull { it !in s.flags }
        val msgs = mutableListOf<String>()
        if (roll.success && next != null) {
            s = s.copy(flags = s.flags + next)
            msgs += when (next) {
                "phoenix_clue_1" -> "Ein Budgetposten „P-Null“ taucht seit 2008 in jedem Haushalt auf."
                "phoenix_clue_2" -> "Eine Versetzungsakte trägt Percys Kürzel – und das Datum deines Eintritts."
                else -> "Ein gelöschter Eintrag: „Kandidat 7 – Integration in Division bestätigt. V.“"
            }
            val xp = Progression.grantXp(data, s, 60)
            s = xp.state; msgs += xp.messages
        } else if (next == null) msgs += "Das Archiv gibt nichts Neues mehr preis."
        else msgs += "Stunden voller Staub und Schwärzungen. (${roll.roll}/${roll.chance})"
        val t = passTime(data, s, 1)
        return Result(t.state, msgs + t.messages)
    }

    // --- Begegnungen (events.json) ----------------------------------------------

    private fun eventRoom(locationId: String) = if (locationId == "hq") "lobby" else locationId

    fun encounter(data: GameData, state: GameState, roomId: String): EventDefinition? {
        val week = Clock.week(state.clock.day)
        val present = npcsIn(data, state, roomId).toSet()
        return data.events.firstOrNull { e ->
            eventRoom(e.locationId) == roomId && Ages.player(state) >= e.minAge &&
                (e.npcId == null || e.npcId in present) && "ev_${e.id}_w$week" !in state.flags &&
                "ev_${e.id}_done" !in state.flags && (e.npcId == null || "met_${e.npcId}" in state.flags || e.npcId in listOf("percy", "owen", "ryan", "sonya", "nikita"))
        }
    }

    fun resolveEncounter(data: GameData, state: GameState, eventId: String, choiceId: String): Result {
        val e = data.events.firstOrNull { it.id == eventId } ?: return state.fail("Ereignis fehlt")
        val c = e.choices.firstOrNull { it.id == choiceId } ?: return state.fail("Auswahl fehlt")
        val week = Clock.week(state.clock.day)
        var s = state.copy(flags = state.flags + "ev_${e.id}_w$week")
        val r = Effs.apply(Effects(trust = c.trustDelta, friendship = if (c.trustDelta > 0) 2 else 0, xp = c.xp, stress = c.stressDelta), data, s, e.npcId)
        s = r.state
        val r2 = Effs.apply(c.effects, data, s, e.npcId)
        s = r2.state
        val t = if (c.trustDelta > 0) passTime(data, s, 1) else Result(s)
        return Result(t.state, listOf(e.title + ": " + c.text) + r.messages + r2.messages + t.messages)
    }
}

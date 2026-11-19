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
        // Wunden heilen langsam (7 Heiltage pro Wunde, guter Schlaf zählt doppelt)
        val p = s.player
        if (p.wounds > 0) {
            val prog = p.woundProgress + if (sleptWell) 2 else 1
            s = s.copy(player = if (prog >= 7) p.copy(wounds = p.wounds - 1, woundProgress = 0) else p.copy(woundProgress = prog))
            if (prog >= 7) msgs += "Eine Verletzung ist verheilt."
        }
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
        s = CompanionAssignment.arrive(data, s)
        val feedBefore = s.living.feed.size
        s = Life.advance(data, s)
        // v0.7: Weltlage, Ermittlungen und Missionsboard fortschreiben
        if (s.clock.day % 7 == 0) s = Ops.weeklyFactions(data, s)
        s = Cases.daily(data, s)
        val opsBefore = s.ops.history.size
        s = Ops.refresh(data, s)
        s.ops.history.drop(opsBefore).forEach { msgs += "Auftrag ${ContractStatus.label(it.status).lowercase()}: ${it.title}" }
        // Wichtige Neuigkeiten über bekannte Figuren als Tagesmeldung
        s.living.feed.drop(feedBefore.coerceAtMost(s.living.feed.size)).filter { it.type in setOf("promoted", "injured", "transferred", "couple", "returned") }
            .takeLast(3).forEach { msgs += it.text }
        if (s.living.pendingHqEvent != null && state.living.pendingHqEvent == null) msgs += "HQ-Ereignis: " + HqEvents.fill(data, s, HqEvents.pending(data, s)?.title ?: "", HqEvents.pending(data, s)?.npcId)
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

    /**
     * Zeitsprung über die Zeitplan-Simulation (v0.5). Ohne Anhalten bei
     * Ereignissen; Story-Ereignisse bleiben als ausstehend erhalten.
     */
    fun timeSkip(data: GameData, state: GameState, days: Int): Result = Simulation.run(data, state, days, stopOnEvents = false)

    // --- HQ, Räume und NPCs ---------------------------------------------------

    fun roomLocked(data: GameData, state: GameState, room: RoomDefinition): String? {
        if (state.player.rank < room.minRank) return "Zutritt ab Rang ${room.minRank} (${data.rank(room.minRank).name})"
        if (Ages.player(state) < room.minAge) return "Zutritt ab ${room.minAge} Jahren"
        if (room.requiredItem != null && state.player.rank < 7 && state.player.inventory.none { it.itemId == room.requiredItem }) return "Benötigt: " + (data.itemById[room.requiredItem]?.name ?: room.requiredItem)
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
        // v0.6: Lebensstatus geht dem Dienstplan vor
        val life = state.living.lives[id]
        val leader = data.agentById[id]?.lifeProfile in setOf("leader", "rogue")
        if (life != null) {
            val adult = Ages.npc(data, state, id) >= 18
            when (life.status) {
                LifeStatus.MISSION, LifeStatus.VACATION, LifeStatus.ABSENT, LifeStatus.TRANSFERRED -> return null
                LifeStatus.INJURED -> return "medical"
                LifeStatus.TRAINING -> if (!leader && state.clock.slot in 1..3) return if (life.focus == "combat" && state.clock.slot == 3) "range" else if (life.focus == "tech") "tech" else "training"
                LifeStatus.FREE -> if (!leader) return if (adult) listOf("canteen", "canteen", "canteen", "city", "canteen", null)[state.clock.slot]
                    else listOf("dorm", "canteen", "canteen", "canteen", "canteen", "dorm")[state.clock.slot]
            }
        }
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
        // Klinik: Gesundheit sofort, Wunden nur über Heilfortschritt (+3 Tage pro Behandlung)
        val prog = p.woundProgress + if (p.wounds > 0) 3 else 0
        val healed = p.wounds > 0 && prog >= 7
        val s = state.copy(player = p.copy(health = (p.health + 30 + med).coerceAtMost(100), wounds = if (healed) p.wounds - 1 else p.wounds,
            woundProgress = if (healed) 0 else prog, stress = (p.stress - 5).pct()))
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

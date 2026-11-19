package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v0.8 Privatleben: Unterkünfte, Einrichtung mit Wirkung, Stadtkalender,
 * Hobbys, persönliche Treffen, Tarnjobs und Alltagsereignisse.
 *
 * Eine Wohnung schafft keine Romanze und keine Altersausnahme: Treffen sind
 * freundschaftlich; romantische Schritte laufen ausschließlich über die
 * unveränderten Regeln in [Romance]. [PlayerCharacter.apartment] wird für die
 * bestehende Logik (z. B. „Zusammenziehen“) synchron gehalten.
 */
object Home {
    const val LEISURE_PER_DAY = 3
    const val LOG_CAP = 30

    data class Housing(val id: String, val name: String, val deposit: Int, val rent: Int, val minAge: Int, val minRank: Int,
                       val sleepEnergy: Int, val sleepStress: Int, val slots: Int, val description: String)
    val housings = listOf(
        Housing("quarters", "Rekrutenquartier", 0, 0, 16, 1, 0, 0, 0, "Mehrbettzimmer im HQ. Kostenlos, aber wenig Privatsphäre."),
        Housing("small", "Kleine Wohnung", 450, 150, 18, 1, 10, 5, 3, "Ein Zimmer, Küchenzeile, Blick auf den Hinterhof."),
        Housing("large", "Größere Wohnung", 1200, 320, 18, 5, 14, 8, 5, "Zwei Zimmer und ein Balkon. Platz für Gäste."),
        Housing("upscale", "Gehobene Wohnung", 3000, 600, 18, 8, 18, 12, 7, "Altbau mit Concierge – ruhig, sicher, diskret.")
    )
    val housingById = housings.associateBy { it.id }
    fun housingName(id: String) = housingById[id]?.name ?: id
    fun housing(state: GameState): Housing = housingById[state.life.housing] ?: housings.first()

    data class Furniture(val id: String, val name: String, val cost: Int, val effect: String)
    val furniture = listOf(
        Furniture("bed", "Gutes Bett", 300, "+5 Energie beim Schlafen"),
        Furniture("music", "Musikecke", 220, "−3 Stress beim Schlafen"),
        Furniture("plants", "Zimmerpflanzen", 80, "−1 Stress beim Schlafen, Stimmung"),
        Furniture("kitchen", "Ausgestattete Küche", 400, "Kochen zu Hause: Energie +20, Stress −5 (statt Kantine)"),
        Furniture("desk", "Schreibtisch", 250, "Lernabend zu Hause: Intellekt-Fortschritt"),
        Furniture("training", "Trainingsecke", 350, "Heimtraining: Fitness-Fortschritt ohne Weg ins HQ"),
        Furniture("lock", "Sicherheitsschloss", 300, "Schützt vor unangenehmen Überraschungen bei Alltagsereignissen"),
        Furniture("table", "Esstisch für Gäste", 280, "Treffen zu Hause: +2 Freundschaft zusätzlich")
    )
    val furnitureById = furniture.associateBy { it.id }

    // --- Unterkunft ------------------------------------------------------------------

    fun canMove(data: GameData, state: GameState, id: String): String? {
        val h = housingById[id] ?: return "Unbekannte Unterkunft"
        if (state.life.housing == id) return "Du wohnst bereits dort."
        if (Ages.player(state) < h.minAge) return "Ab ${h.minAge} Jahren"
        if (state.player.rank < h.minRank) return "Ab Rang ${h.minRank} (${data.rank(h.minRank).name})"
        if (state.player.credits < h.deposit) return "Kaution ${h.deposit} Cr"
        return null
    }

    fun move(data: GameData, state: GameState, id: String): Result {
        canMove(data, state, id)?.let { return state.fail(it) }
        val h = housingById.getValue(id)
        val week = Clock.week(state.clock.day)
        val keep = state.life.furniture.toList().take(h.slots).toSet()
        val lost = state.life.furniture - keep
        val s = state.copy(
            player = state.player.copy(credits = state.player.credits - h.deposit, apartment = id != "quarters"),
            life = state.life.copy(housing = id, furniture = keep, lastRentWeek = week, missedRent = 0)
        )
        val msgs = listOf(if (id == "quarters") "Du ziehst zurück ins Rekrutenquartier. Keine Miete mehr." else "Du ziehst in: ${h.name} (Kaution ${h.deposit} Cr, ${h.rent} Cr/Woche).") +
            (if (lost.isNotEmpty()) listOf("Kein Platz für: " + lost.joinToString { furnitureById[it]?.name ?: it }) else emptyList())
        return Result(log(s, "Umzug: ${h.name}"), msgs)
    }

    fun buyFurniture(data: GameData, state: GameState, id: String): Result {
        val f = furnitureById[id] ?: return state.fail("Unbekannt")
        val h = housing(state)
        if (h.slots == 0) return state.fail("Im Rekrutenquartier ist kein Platz für eigene Möbel.")
        if (id in state.life.furniture) return state.fail("Schon vorhanden.")
        if (state.life.furniture.size >= h.slots) return state.fail("Kein Platz mehr (${h.slots} Plätze).")
        if (state.player.credits < f.cost) return state.fail("${f.cost} Cr nötig.")
        val s = state.copy(player = state.player.copy(credits = state.player.credits - f.cost), life = state.life.copy(furniture = state.life.furniture + id))
        return Result(log(s, "Gekauft: ${f.name}"), listOf("${f.name} eingerichtet (−${f.cost} Cr). ${f.effect}."))
    }

    fun sellFurniture(state: GameState, id: String): Result {
        val f = furnitureById[id] ?: return state.fail("Unbekannt")
        if (id !in state.life.furniture) return state.fail("Nicht vorhanden.")
        val back = f.cost / 3
        return Result(state.copy(player = state.player.copy(credits = state.player.credits + back), life = state.life.copy(furniture = state.life.furniture - id)),
            listOf("${f.name} verkauft (+$back Cr)."))
    }

    fun sleepEnergyBonus(state: GameState): Int = housing(state).sleepEnergy + (if ("bed" in state.life.furniture) 5 else 0)
    fun sleepStressBonus(state: GameState): Int = housing(state).sleepStress + (if ("music" in state.life.furniture) 3 else 0) + (if ("plants" in state.life.furniture) 1 else 0) +
        Sites.moduleLevel(state, "lounge")

    // --- Wöchentlich: Miete und Tarnjob (genau einmal) --------------------------------------

    fun weekly(data: GameData, state: GameState): Pair<GameState, List<String>> {
        var s = state
        val msgs = mutableListOf<String>()
        val week = Clock.week(s.clock.day)
        // Miete
        val h = housing(s)
        if (h.rent > 0 && s.life.lastRentWeek < week) {
            if (s.player.credits >= h.rent) {
                s = s.copy(player = s.player.copy(credits = s.player.credits - h.rent), life = s.life.copy(lastRentWeek = week, missedRent = 0))
                msgs += "Miete −${h.rent} Cr"
            } else {
                val missed = s.life.missedRent + 1
                s = s.copy(life = s.life.copy(lastRentWeek = week, missedRent = missed))
                msgs += "Miete nicht bezahlt (Mahnung $missed/2)"
                if (missed >= 2) {
                    s = s.copy(player = s.player.copy(apartment = false), life = s.life.copy(housing = "quarters", missedRent = 0, furniture = emptySet()))
                    msgs += "Die Wohnung ist gekündigt. Du ziehst zurück ins Rekrutenquartier."
                    s = log(s, "Wohnung wegen Mietrückstand verloren")
                }
            }
        }
        // Tarnjob: volle Bezahlung nur mit gearbeiteter Schicht
        val job = s.life.coverJob?.let { jobById[it] }
        if (job != null) {
            // Abrechnung zum Wochenwechsel: zählt die Schicht der gerade beendeten Woche
            val worked = s.life.shiftWeek >= week - 1
            val pay = if (worked) job.pay else job.pay / 2
            var warnings = s.life.jobWarnings
            if (!worked) warnings++ else warnings = (warnings - 1).coerceAtLeast(0)
            s = s.copy(player = s.player.copy(credits = s.player.credits + pay), life = s.life.copy(coverJobWeeks = s.life.coverJobWeeks + 1, jobWarnings = warnings))
            msgs += "${job.name}: +$pay Cr" + if (!worked) " (keine Schicht – Verwarnung $warnings/3)" else ""
            if (warnings >= 3) {
                s = s.copy(player = s.player.copy(coverJob = null), life = s.life.copy(coverJob = null, jobWarnings = 0))
                msgs += "Du verlierst den Tarnjob als ${job.name}."
                s = log(s, "Tarnjob verloren: ${job.name}")
            }
        }
        // Ereignis zu Hause
        if (h.id != "quarters" && s.life.pendingEvent == null) {
            val rng = Rng(Rng.mix(s.seed, week.toLong(), 3131))
            if (rng.percent() <= 30) s = trigger(data, s, rng, home = true)
        }
        return s to msgs
    }

    // --- Tarnjobs ------------------------------------------------------------------------

    data class Job(val id: String, val name: String, val pay: Int, val minAge: Int, val requirement: String, val stress: Int)
    val jobs = listOf(
        Job("courier", "Fahrradkurier", 120, 17, "keine", 0),
        Job("cafe", "Café-Aushilfe", 140, 17, "keine", 2),
        Job("tutor", "Nachhilfe", 160, 17, "Intellekt ≥ 40", 0),
        Job("translator", "Übersetzungsbüro", 220, 18, "Fremdsprachen-Fertigkeit", 3)
    )
    val jobById = jobs.associateBy { it.id }

    fun takeJob(data: GameData, state: GameState, id: String?): Result {
        if (id == null) {
            if (state.life.coverJob == null) return state.fail("Kein Tarnjob.")
            return Result(state.copy(player = state.player.copy(coverJob = null), life = state.life.copy(coverJob = null, jobWarnings = 0)), listOf("Tarnjob gekündigt."))
        }
        val j = jobById[id] ?: return state.fail("Unbekannt")
        if (Ages.player(state) < j.minAge) return state.fail("Ab ${j.minAge} Jahren")
        if (id == "tutor" && Stats.effectiveAttribute(data, state, Attr.INTELLECT) < 40) return state.fail("Intellekt ≥ 40 nötig")
        if (id == "translator" && (state.player.skills["language"] ?: 0) < 1) return state.fail("Fertigkeit „Fremdsprachen“ nötig")
        val s = state.copy(player = state.player.copy(coverJob = j.name), life = state.life.copy(coverJob = id, jobWarnings = 0, shiftWeek = Clock.week(state.clock.day)))
        return Result(log(s, "Tarnjob: ${j.name}"), listOf("Tarnjob: ${j.name} – ${j.pay} Cr/Woche bei einer Schicht pro Woche."))
    }

    fun workShift(data: GameData, state: GameState): Result {
        val j = state.life.coverJob?.let { jobById[it] } ?: return state.fail("Kein Tarnjob.")
        val week = Clock.week(state.clock.day)
        if (state.life.shiftWeek == week) return state.fail("Diese Woche ist die Schicht schon erledigt.")
        if (state.player.energy < 20) return state.fail("Zu erschöpft für eine Schicht.")
        var s = state.copy(player = state.player.copy(energy = (state.player.energy - 20).pct(), stress = (state.player.stress + j.stress).pct()),
            life = state.life.copy(shiftWeek = week))
        val t = World.passTime(data, s, 2)
        s = t.state
        return Result(log(s, "Schicht: ${j.name}"), listOf("Schicht als ${j.name} erledigt. Volle Bezahlung zum Wochenende.") + t.messages)
    }

    // --- Stadtkalender -----------------------------------------------------------------

    data class Activity(
        val id: String, val name: String, val description: String, val slots: List<Int>, val cost: Int, val energy: Int,
        val stress: Int, val hobby: String? = null, val attribute: String? = null, val minAge: Int = 16, val needsFurniture: String? = null
    )
    val activities = listOf(
        Activity("cafe", "Café", "Ein Kaffee unter normalen Menschen.", listOf(0, 1, 2, 3), 8, 0, -15),
        Activity("run", "Laufen im Park", "Eine Runde am Fluss.", listOf(0, 1, 3), 0, 12, -8, hobby = "Laufen", attribute = Attr.FITNESS),
        Activity("climb", "Kletterhalle", "Bouldern nach Feierabend.", listOf(2, 3), 15, 15, -10, hobby = "Klettern", attribute = Attr.FITNESS),
        Activity("museum", "Museum", "Eine Ausstellung über Stadtgeschichte.", listOf(1, 2), 12, 4, -8, hobby = "Kunst", attribute = Attr.INTELLECT),
        Activity("library", "Stadtbibliothek", "Lesen und Recherche.", listOf(0, 1, 2), 0, 4, -5, hobby = "Bücher", attribute = Attr.INTELLECT),
        Activity("concert", "Konzert", "Live-Musik in einem kleinen Club.", listOf(3, 4), 25, 8, -18, hobby = "Musik"),
        Activity("cinema", "Kino", "Ein Film, bei dem niemand stirbt. Hoffentlich.", listOf(3, 4), 14, 2, -12),
        Activity("chess", "Schachclub", "Blitzpartien im Hinterzimmer eines Cafés.", listOf(2, 3), 5, 4, -6, hobby = "Schach", attribute = Attr.INTELLECT),
        Activity("photo", "Fotospaziergang", "Mit der Kamera durch alte Viertel.", listOf(0, 1, 2), 0, 6, -8, hobby = "Fotografie", attribute = Attr.PERCEPTION),
        Activity("cooking", "Kochkurs", "Ein Abend über italienische Küche.", listOf(3, 4), 30, 6, -10, hobby = "Kochen"),
        Activity("home_cook", "Zu Hause kochen", "Ein ruhiger Abend in der eigenen Küche.", listOf(2, 3, 4), 6, -20, -5, needsFurniture = "kitchen"),
        Activity("home_study", "Lernabend zu Hause", "Fachliteratur am eigenen Schreibtisch.", listOf(2, 3, 4), 0, 8, 0, attribute = Attr.INTELLECT, needsFurniture = "desk"),
        Activity("home_train", "Heimtraining", "Übungen in der Trainingsecke.", listOf(0, 3, 4), 0, 12, -4, attribute = Attr.FITNESS, needsFurniture = "training")
    )
    val activityById = activities.associateBy { it.id }

    private fun leisureCount(state: GameState) = if (state.life.leisureDay == state.clock.day) state.life.leisureToday else 0

    fun canDo(data: GameData, state: GameState, id: String): String? {
        val a = activityById[id] ?: return "Unbekannt"
        if (state.activeMission != null) return "Während eines Einsatzes nicht möglich"
        if (Ages.player(state) < a.minAge) return "Ab ${a.minAge} Jahren"
        if (a.needsFurniture != null && a.needsFurniture !in state.life.furniture) return "Benötigt: ${furnitureById[a.needsFurniture]?.name}"
        if (state.clock.slot !in a.slots) return "Zu dieser Tageszeit nicht möglich (${a.slots.joinToString("/") { Clock.slotNames[it] }})"
        if ((state.life.lastActivity[id] ?: -1) == state.clock.day) return "Heute schon gemacht"
        if (leisureCount(state) >= LEISURE_PER_DAY) return "Für heute genug Freizeit – der Dienst ruft"
        if (state.player.credits < a.cost) return "${a.cost} Cr nötig"
        if (a.energy > 0 && state.player.energy < a.energy) return "Zu erschöpft"
        return null
    }

    fun doActivity(data: GameData, state: GameState, id: String): Result {
        canDo(data, state, id)?.let { return state.fail(it) }
        val a = activityById.getValue(id)
        val p = state.player
        var s = state.copy(
            player = p.copy(credits = p.credits - a.cost, energy = (p.energy - a.energy).pct(), stress = (p.stress + a.stress).pct()),
            life = state.life.copy(lastActivity = state.life.lastActivity + (id to state.clock.day), leisureToday = leisureCount(state) + 1, leisureDay = state.clock.day)
        )
        val msgs = mutableListOf("${a.name}: ${a.description}" + (if (a.cost > 0) " (−${a.cost} Cr)" else "") + " · Stress ${Effs.signed(a.stress)}")
        a.hobby?.let { h ->
            val lvl = (s.life.hobbies[h] ?: 0) + 1
            s = s.copy(life = s.life.copy(hobbies = s.life.hobbies + (h to lvl)))
            // Ein Hobby mit Ausdauer wird zu einem echten Interesse (Passung, Freundschaften)
            if (lvl == 3 && h !in s.player.interests) {
                s = s.copy(player = s.player.copy(interests = s.player.interests + h))
                msgs += "„$h“ ist jetzt eines deiner Interessen – gemeinsame Themen mit anderen."
            }
        }
        a.attribute?.let { attr ->
            val pl = s.player
            val cur = pl.attributes[attr] ?: 30
            val cap = Stats.attributeCap(pl.level)
            val (nv, prog) = Progression.attributeProgress(cur, pl.trainingProgress[attr] ?: 0, cap, 4, 0, 0.35)
            s = s.copy(player = pl.copy(attributes = pl.attributes + (attr to nv), trainingProgress = pl.trainingProgress + (attr to prog)))
            if (nv > cur) msgs += "${Attr.label(attr)} +${nv - cur}"
        }
        s = log(s, a.name)
        // Gelegenheit für ein Alltagsereignis
        if (s.life.pendingEvent == null) {
            val rng = Rng(Rng.mix(s.seed, s.clock.day.toLong(), id.hashCode().toLong(), 2727))
            if (rng.percent() <= 25) s = trigger(data, s, rng, home = id.startsWith("home_"))
        }
        val t = World.passTime(data, s, 1)
        if (t.state.life.pendingEvent != null && state.life.pendingEvent == null) msgs += "Alltagsereignis: ${eventById[t.state.life.pendingEvent]?.title}"
        return Result(t.state, msgs + t.messages)
    }

    // --- Persönliche Treffen -------------------------------------------------------------

    data class Meeting(
        val id: String, val npcId: String, val name: String, val description: String, val minFriendship: Int,
        val cost: Int = 0, val friendship: Int = 4, val trust: Int = 2, val stress: Int = -6, val minAge: Int = 16,
        val flag: String? = null, val memory: String, val risk: Boolean = false
    )
    val meetings = listOf(
        Meeting("alex_photo", "alex", "Fotospaziergang mit Alex", "Alex zeigt dir die Ecken der Stadt, die sie fotografiert.", 35, memory = "Ein Fotospaziergang durch die Altstadt"),
        Meeting("alex_music", "alex", "Musikabend mit Alex", "Ein geteilter Kopfhörer und schlechte russische Popmusik.", 45, memory = "Musikabend mit geteilten Kopfhörern", stress = -10),
        Meeting("alex_run", "alex", "Laufrunde mit Alex", "Wer zuerst an der Brücke ist, gibt den Kaffee aus.", 30, memory = "Wettlauf zur Brücke"),
        Meeting("michael_chess", "michael", "Schach mit Michael", "Michael spielt so, wie er ausbildet: geduldig und gnadenlos.", 40, cost = 6, trust = 3, memory = "Schachpartie im Café"),
        Meeting("michael_dinner", "michael", "Abendessen bei Michael", "Michael kocht – erstaunlich gut. Über die Arbeit wird nicht gesprochen.", 60, friendship = 6, trust = 4, memory = "Ein Abendessen bei Michael, ohne über die Arbeit zu reden"),
        Meeting("birkhoff_games", "birkhoff", "Retro-Spieleabend mit Birkhoff", "Alte Konsolen, Pizza und sehr viel Prahlerei.", 30, cost = 10, memory = "Retro-Spieleabend bei Birkhoff", stress = -12),
        Meeting("birkhoff_market", "birkhoff", "Elektronikmarkt mit Birkhoff", "Birkhoff erklärt dir jedes Bauteil. Jedes.", 40, memory = "Ein Nachmittag auf dem Elektronikmarkt"),
        Meeting("nikita_roof", "nikita", "Treffen auf einem Dach", "Ein vereinbartes Zeichen, ein Dach, zehn Minuten. Nikita redet, wenn sie dir traut.", 20, minAge = 18, flag = "nikita_contact", trust = 4, memory = "Zehn Minuten auf einem Dach mit Nikita", risk = true),
        Meeting("companion_hobby", "assigned_companion", "Gemeinsames Hobby", "Ihr verbringt Zeit mit einem Interesse, das ihr teilt.", 25, memory = "Ein Nachmittag mit unserem gemeinsamen Hobby"),
        Meeting("companion_study", "assigned_companion", "Lernabend", "Ihr bereitet euch gemeinsam auf Prüfungen vor.", 20, stress = -2, memory = "Ein Lernabend vor der Prüfung")
    )
    val meetingById = meetings.associateBy { it.id }

    fun meetingsFor(data: GameData, state: GameState): List<Meeting> {
        // Begleiter-Treffen gelten für die tatsächlich zugeteilte Begleitung (eigene Rekrutierungen)
        val expanded = meetings.flatMap { m ->
            if (m.npcId != "assigned_companion") listOf(m)
            else state.customNpcs.map { c -> m.copy(id = "${m.id}_${c.id}", npcId = c.id, name = "${m.name} mit ${c.firstName}") }
        }
        val known = expanded.filter { Relations.isKnownNpc(data, state, it.npcId) && World.revealed(data, state, it.npcId) }
        val generic = state.relations.keys.filter { id -> known.none { it.npcId == id } && Relations.isKnownNpc(data, state, id) && World.revealed(data, state, id) && id !in setOf("percy", "amanda") }
            .map { Meeting("coffee_$it", it, "Kaffee mit ${Relations.displayName(data, state, it).substringBefore(" ")}", "Ein Kaffee außerhalb des HQ.", 25, cost = 8, memory = "Ein Kaffee in der Stadt") }
        return known + generic
    }

    fun canMeet(data: GameData, state: GameState, m: Meeting): String? {
        val custom = state.customNpcs.firstOrNull { it.id == m.npcId }
        if (custom != null && state.clock.day < custom.createdDay) return "Noch nicht in der Division"
        if (CompanionAssignment.awaitingIntroduction(state)?.id == m.npcId) return "Noch nicht vorgestellt"
        if (m.flag != null && m.flag !in state.flags) return "Noch kein Kontakt"
        if (Ages.player(state) < m.minAge) return "Ab ${m.minAge} Jahren"
        if (m.npcId != "nikita" && !Ops.available(data, state, m.npcId)) return Ops.unavailableReason(data, state, m.npcId) ?: "nicht verfügbar"
        if (m.npcId == "nikita" && state.living.lives["nikita"]?.status == LifeStatus.TRANSFERRED) return "nicht erreichbar"
        if (Relations.get(data, state, m.npcId).friendship < m.minFriendship) return "Freundschaft ≥ ${m.minFriendship}"
        if (m.npcId in state.living.conflicts) return "Erst den Konflikt klären"
        if ((state.life.lastActivity["meet_${m.npcId}"] ?: -99) > state.clock.day - 3) return "Ihr habt euch gerade erst getroffen"
        if (leisureCount(state) >= LEISURE_PER_DAY) return "Für heute genug Freizeit"
        if (state.clock.slot !in 1..4) return "Treffen am Nachmittag oder Abend"
        if (state.player.credits < m.cost) return "${m.cost} Cr nötig"
        if (state.player.energy < 10) return "Zu erschöpft"
        return null
    }

    fun meet(data: GameData, state: GameState, id: String): Result {
        val m = meetingsFor(data, state).firstOrNull { it.id == id } ?: return state.fail("Treffen unbekannt")
        canMeet(data, state, m)?.let { return state.fail(it) }
        val host = "table" in state.life.furniture && housing(state).id != "quarters"
        val shared = Life.interests(data, state, m.npcId).toSet().intersect(state.player.interests.toSet())
        val bonus = (if (host) 2 else 0) + if (shared.isNotEmpty()) 2 else 0
        var s = state.copy(
            player = state.player.copy(credits = state.player.credits - m.cost, energy = (state.player.energy - 10).pct(), stress = (state.player.stress + m.stress).pct()),
            life = state.life.copy(lastActivity = state.life.lastActivity + ("meet_${m.npcId}" to state.clock.day), leisureToday = leisureCount(state) + 1, leisureDay = state.clock.day,
                meetings = (state.life.meetings + "${state.clock.day}:${m.npcId}").takeLast(40))
        )
        s = Relations.update(data, s, m.npcId) { it.copy(friendship = it.friendship + m.friendship + bonus, trust = it.trust + m.trust, lastTalkDay = s.clock.day, memories = it.memories + m.memory) }
        val l = Life.life(data, s, m.npcId)
        s = s.copy(living = s.living.copy(lives = s.living.lives + (m.npcId to l.copy(mood = (l.mood + 5).pct(), stress = (l.stress - 5).pct()))))
        val msgs = mutableListOf("${m.name}: ${m.description}", "Freundschaft +${m.friendship + bonus}, Vertrauen +${m.trust}" + if (shared.isNotEmpty()) " (gemeinsames Interesse: ${shared.first()})" else "")
        if (m.risk) {
            val arcs = s.living.arcs
            s = s.copy(living = s.living.copy(arcs = arcs + ("percy_pressure" to (arcs["percy_pressure"] ?: 0) + if (Sites.siteLevel(data, s, "safehouse") >= 3) 0 else 1)))
            msgs += if (Sites.siteLevel(data, s, "safehouse") >= 3) "Das Safehouse schirmt das Treffen ab." else "Das Treffen bleibt nicht völlig unbemerkt (Percys Druck +1)."
        }
        s = log(s, m.name)
        val t = World.passTime(data, s, 1)
        return Result(t.state, msgs + t.messages)
    }

    // --- Alltagsereignisse -------------------------------------------------------------

    data class EventChoice(
        val id: String, val text: String, val result: String, val credits: Int = 0, val stress: Int = 0, val energy: Int = 0,
        val npcId: String? = null, val friendship: Int = 0, val trust: Int = 0, val memory: String? = null, val flag: String? = null,
        val cover: Int = 0, val needsFurniture: String? = null
    )
    data class LifeEventDef(
        val id: String, val title: String, val text: String, val home: Boolean = false, val minAge: Int = 16,
        val npcId: String? = null, val needsJob: Boolean = false, val minHousing: String? = null, val choices: List<EventChoice>
    )

    val events = listOf(
        LifeEventDef("neighbor_noise", "Laute Nachbarn", "Über dir läuft um zwei Uhr nachts ein Bass, der die Gläser im Schrank zittern lässt.", home = true, choices = listOf(
            EventChoice("knock", "Freundlich klopfen", "Der Nachbar entschuldigt sich verlegen und lädt dich zu seiner nächsten Party ein.", stress = -2, flag = "neighbor_friend"),
            EventChoice("endure", "Kissen über den Kopf", "Du schläfst schlecht.", energy = -15, stress = 5),
            EventChoice("complain", "Bei der Hausverwaltung beschweren", "Ab nächster Woche ist Ruhe – und dein Nachbar grüßt nicht mehr.", stress = -1)
        )),
        LifeEventDef("pipe_burst", "Wasserrohrbruch", "Als du nach Hause kommst, steht das Bad unter Wasser.", home = true, minHousing = "small", choices = listOf(
            EventChoice("plumber", "Notdienst rufen", "Teuer, aber schnell erledigt.", credits = -120, stress = 3),
            EventChoice("fix", "Selbst reparieren", "Mit Werkzeug aus der Ausrüstungskammer klappt es – nach zwei Stunden.", energy = -20, stress = 4),
            EventChoice("landlord", "Vermieter informieren und warten", "Drei Tage ohne Dusche zu Hause. Die Kantine des HQ ist dein Bad.", stress = 8)
        )),
        LifeEventDef("old_friend", "Ein bekanntes Gesicht", "Im Café spricht dich jemand an: „Bist du nicht …?“ Ein Name aus deinem Leben vor der Division.", choices = listOf(
            EventChoice("deny", "Freundlich verneinen", "„Verwechslung.“ Die Person entschuldigt sich. Deine Legende hält.", stress = 4),
            EventChoice("cover", "Mit der Tarnidentität antworten", "Du erzählst die Geschichte deines Tarnjobs. Sie klingt glaubwürdig.", stress = 2, cover = 1),
            EventChoice("talk", "Kurz reden", "Ein paar Minuten echtes altes Leben. Es tut gut – und es ist riskant.", stress = -8, flag = "met_old_friend")
        )),
        LifeEventDef("extra_shift", "Eine Zusatzschicht", "Dein Chef im Tarnjob fragt, ob du am Wochenende einspringen kannst.", needsJob = true, choices = listOf(
            EventChoice("yes", "Einspringen", "Das Geld ist willkommen, das Wochenende ist weg.", credits = 90, energy = -20, stress = 4),
            EventChoice("no", "Ablehnen", "Der Chef nickt, aber er wirkt enttäuscht.", stress = -2)
        )),
        LifeEventDef("stray_cat", "Eine Katze vor der Tür", "Eine magere Katze sitzt vor deiner Wohnungstür und sieht dich an.", home = true, minHousing = "small", choices = listOf(
            EventChoice("adopt", "Aufnehmen", "Sie heißt jetzt Protokoll. Dein Stress sinkt jeden Abend ein bisschen.", credits = -40, stress = -10, flag = "has_cat"),
            EventChoice("shelter", "Ins Tierheim bringen", "Die Pflegerin verspricht, ein gutes Zuhause zu finden.", energy = -5),
            EventChoice("feed", "Nur füttern", "Sie kommt ab jetzt jeden Abend vorbei.", credits = -5, stress = -4)
        )),
        LifeEventDef("lost_wallet", "Eine verlorene Geldbörse", "Auf einer Parkbank liegt eine Geldbörse mit Ausweis und 80 Cr.", choices = listOf(
            EventChoice("return", "Beim Fundbüro abgeben", "Zwei Tage später ein Brief mit einem Dank – und 10 Cr Finderlohn.", credits = 10, stress = -2),
            EventChoice("find", "Den Besitzer selbst suchen", "Eine alte Dame weint vor Erleichterung. Das bleibt hängen.", energy = -10, stress = -8)
        )),
        LifeEventDef("rent_letter", "Post vom Vermieter", "Ein Brief: Die Hausverwaltung plant eine Modernisierung – mit Mieterhöhung.", home = true, minHousing = "small", choices = listOf(
            EventChoice("accept", "Akzeptieren", "Ein neuer Anstrich, ein neuer Herd, eine höhere Miete. Du zahlst einmalig 60 Cr Umlage.", credits = -60, stress = 2),
            EventChoice("object", "Widerspruch einlegen", "Mit einem freundlichen, präzisen Schreiben erreichst du einen Aufschub.", energy = -10, stress = 3)
        )),
        LifeEventDef("street_musician", "Straßenmusik", "Eine junge Geigerin spielt an der U-Bahn so gut, dass die Menschen stehen bleiben.", choices = listOf(
            EventChoice("listen", "Stehen bleiben und zuhören", "Zehn Minuten, in denen nichts zu tun ist. Selten.", stress = -10),
            EventChoice("tip", "Etwas geben", "Sie lächelt und spielt ein Stück extra.", credits = -5, stress = -12)
        )),
        LifeEventDef("power_outage", "Stromausfall", "Das ganze Viertel liegt im Dunkeln. Draußen ist es ungewöhnlich still.", home = true, choices = listOf(
            EventChoice("candles", "Kerzen und ein Buch", "Ein Abend ohne Bildschirm. Es fühlt sich gut an.", stress = -8),
            EventChoice("check", "Nachsehen, ob es Zufall ist", "Ein Umspannwerk ist ausgefallen – kein Angriff. Du schläfst trotzdem schlecht.", energy = -10, stress = 3)
        )),
        LifeEventDef("parked_car", "Ein Wagen vor dem Haus", "Seit zwei Tagen steht derselbe dunkle Wagen gegenüber deiner Wohnung. Mit laufendem Motor.", home = true, minAge = 18, choices = listOf(
            EventChoice("report", "Der Division melden", "Ein Team prüft den Wagen. Ein Privatdetektiv – im Auftrag einer Versicherung. Entwarnung.", stress = 2),
            EventChoice("watch", "Selbst beobachten", "Du notierst Kennzeichen und Zeiten. Am dritten Tag ist der Wagen weg.", energy = -15, stress = 6),
            EventChoice("lock", "Das Sicherheitsschloss vertrauen", "Du schläfst ruhig. Am Morgen ist der Wagen verschwunden.", stress = -2, needsFurniture = "lock")
        )),
        LifeEventDef("hobby_contest", "Ein kleiner Wettbewerb", "In deinem Hobbyverein wird ein Wettbewerb ausgeschrieben. Die anderen drängen dich mitzumachen.", choices = listOf(
            EventChoice("join", "Mitmachen", "Du landest auf Platz drei – und hast einen Abend lang etwas völlig Normales gemacht.", credits = -10, stress = -10, flag = "hobby_podium"),
            EventChoice("watch", "Nur zuschauen", "Du feuerst die anderen an. Auch schön.", stress = -5)
        )),
        LifeEventDef("birthday_invite", "Eine Einladung", "Die Nachbarin von gegenüber lädt dich zu ihrem 60. Geburtstag ein.", home = true, minHousing = "small", choices = listOf(
            EventChoice("go", "Hingehen, mit Kuchen", "Ein Abend mit Fremden, die keine Fragen stellen. Du bekommst Reste mit nach Hause.", credits = -15, stress = -12, energy = -5),
            EventChoice("card", "Eine Karte schreiben", "Sie bedankt sich am nächsten Morgen strahlend im Treppenhaus.", stress = -3)
        )),
        LifeEventDef("alex_photos", "Alex' Fotos", "Alex schickt dir drei Fotos von der Stadt im Regen. Darunter steht nur: „Für dich gesehen.“", npcId = "alex", choices = listOf(
            EventChoice("reply", "Ein eigenes Foto zurückschicken", "Ein stiller Austausch, der ein paar Tage lang weitergeht.", npcId = "alex", friendship = 4, memory = "Fotos von der Stadt im Regen"),
            EventChoice("call", "Anrufen", "Ihr redet eine halbe Stunde über nichts. Genau richtig.", npcId = "alex", friendship = 3, trust = 2, stress = -5)
        )),
        LifeEventDef("michael_invite", "Michael fragt nach", "Michael schreibt kurz: „Alles in Ordnung bei dir? Du wirkst müde.“", npcId = "michael", choices = listOf(
            EventChoice("honest", "Ehrlich antworten", "Er hört zu und schlägt einen freien Tag vor. Er meint es ernst.", npcId = "michael", trust = 4, stress = -8),
            EventChoice("fine", "„Alles bestens.“", "„Wenn du meinst.“ Er glaubt dir nicht ganz.", npcId = "michael", trust = -1)
        )),
        LifeEventDef("birkhoff_gadget", "Birkhoffs Geschenk", "Birkhoff steht mit einem selbstgebauten Gerät vor dir: „Ein Wecker, der dich nur weckt, wenn wirklich etwas passiert.“", npcId = "birkhoff", choices = listOf(
            EventChoice("try", "Ausprobieren", "Er funktioniert. Meistens. Du schläfst besser.", npcId = "birkhoff", friendship = 4, energy = 10),
            EventChoice("polite", "Höflich ablehnen", "„Dein Verlust.“ Er grinst trotzdem.", npcId = "birkhoff", friendship = 1)
        ))
    )
    val eventById = events.associateBy { it.id }

    private val housingOrder = listOf("quarters", "small", "large", "upscale")

    private fun eligible(data: GameData, state: GameState, e: LifeEventDef, home: Boolean): Boolean {
        if (e.home != home && e.home) return false
        if (Ages.player(state) < e.minAge) return false
        if (e.needsJob && state.life.coverJob == null) return false
        if (e.minHousing != null && housingOrder.indexOf(state.life.housing) < housingOrder.indexOf(e.minHousing)) return false
        if (e.npcId != null && (!Relations.isKnownNpc(data, state, e.npcId) || ("met_${e.npcId}" !in state.flags && !state.relations.containsKey(e.npcId)) || Relations.get(data, state, e.npcId).friendship < 30)) return false
        val last = state.life.eventsSeen[e.id]
        return last == null || state.clock.day - last >= 45
    }

    private fun trigger(data: GameData, state: GameState, rng: Rng, home: Boolean): GameState {
        val pool = events.filter { eligible(data, state, it, home) }
        if (pool.isEmpty()) return state
        val e = rng.pick(pool)
        return state.copy(life = state.life.copy(pendingEvent = e.id, eventDay = state.clock.day))
    }

    fun pending(state: GameState) = state.life.pendingEvent?.let { eventById[it] }

    fun choiceAvailable(state: GameState, c: EventChoice) = c.needsFurniture == null || c.needsFurniture in state.life.furniture

    fun resolve(data: GameData, state: GameState, choiceId: String): Result {
        val e = pending(state) ?: return state.fail("Kein Ereignis offen.")
        val c = e.choices.firstOrNull { it.id == choiceId } ?: return state.fail("Option fehlt")
        if (!choiceAvailable(state, c)) return state.fail("Benötigt: ${furnitureById[c.needsFurniture]?.name}")
        if (c.credits < 0 && state.player.credits < -c.credits) return state.fail("${-c.credits} Cr nötig")
        val p = state.player
        var s = state.copy(
            player = p.copy(credits = (p.credits + c.credits).coerceAtLeast(0), stress = (p.stress + c.stress).pct(), energy = (p.energy + c.energy).pct()),
            life = state.life.copy(pendingEvent = null, eventsSeen = (state.life.eventsSeen + (e.id to state.clock.day)))
        )
        if (c.flag != null) s = s.copy(flags = s.flags + c.flag)
        c.npcId?.let { npc -> s = Relations.update(data, s, npc) { it.copy(friendship = it.friendship + c.friendship, trust = it.trust + c.trust, memories = if (c.memory != null) it.memories + c.memory else it.memories) } }
        s = log(s, "${e.title}: ${c.text}")
        val effects = listOfNotNull(
            c.credits.takeIf { it != 0 }?.let { "${Effs.signed(it)} Cr" }, c.stress.takeIf { it != 0 }?.let { "Stress ${Effs.signed(it)}" },
            c.energy.takeIf { it != 0 }?.let { "Energie ${Effs.signed(it)}" }, c.friendship.takeIf { it != 0 }?.let { "Freundschaft ${Effs.signed(it)}" }
        ).joinToString(" · ")
        return Result(s, listOf(c.result) + listOfNotNull(effects.ifBlank { null }))
    }

    /** Täglich: offene Ereignisse verfallen nach 3 Tagen ohne Folgen; die Katze wirkt. */
    fun daily(state: GameState): GameState {
        var s = state
        if (s.life.pendingEvent != null && s.clock.day - s.life.eventDay > 3)
            s = s.copy(life = s.life.copy(pendingEvent = null, eventsSeen = s.life.eventsSeen + (s.life.pendingEvent!! to s.clock.day)))
        if ("has_cat" in s.flags && s.life.housing != "quarters") s = s.copy(player = s.player.copy(stress = (s.player.stress - 1).pct()))
        return s
    }

    fun log(state: GameState, line: String) =
        state.copy(life = state.life.copy(log = (state.life.log + "${Clock.dateLabel(state.clock.day)}: $line").takeLast(LOG_CAP)))
}

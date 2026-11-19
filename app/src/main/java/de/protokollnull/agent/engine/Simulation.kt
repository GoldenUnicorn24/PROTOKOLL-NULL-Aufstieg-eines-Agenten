package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * Zeitplan-Simulation (v0.5): Tag für Tag nach dem Wochenplan des Spielers.
 * Fortschritt entsteht nur aus simulierten Tätigkeiten, Beziehungen nur aus
 * tatsächlichen Begegnungen. Deterministisch (Seed + Tag), bricht bei
 * wichtigen Ereignissen ab.
 */
object Simulation {
    data class Activity(
        val id: String, val name: String, val description: String,
        val attributes: List<String>, val energy: Int, val stress: Int, val xp: Int
    )

    val activities = listOf(
        Activity("combat", "Kampftraining", "Fitness & Präzision mit Ausbildern. Anstrengend.", listOf(Attr.FITNESS, Attr.PRECISION), 32, 6, 12),
        Activity("tech", "Technik", "Systeme, Elektronik, Analyse im Labor.", listOf(Attr.INTELLECT), 22, 4, 11),
        Activity("recon", "Aufklärung", "Beobachtungsparcours, Spurenkunde.", listOf(Attr.PERCEPTION), 22, 4, 11),
        Activity("social", "Sozialleben", "Zeit mit anwesenden Kollegen – nur wer da ist, zählt.", listOf(Attr.CHARISMA), 14, -6, 6),
        Activity("rest", "Erholung", "Schlaf, Klinik, Ruhe. Heilt Wunden schneller.", emptyList(), 0, -16, 0),
        Activity("duty", "Dienst", "Schichten in Zentrale und Kammer. Ruf und Zulagen.", emptyList(), 24, 5, 7),
        Activity("exam", "Prüfungsvorbereitung", "Theorie, Szenarien, Willenskraft.", listOf(Attr.WILLPOWER), 20, 5, 8)
    )

    fun activity(id: String) = activities.firstOrNull { it.id == id } ?: activities.first { it.id == "rest" }

    val weekdayNames = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

    fun setPlan(state: GameState, weekday: Int, activityId: String): GameState {
        val plan = normalizedPlan(state).toMutableList()
        if (weekday in 0..6 && activities.any { it.id == activityId }) plan[weekday] = activityId
        return state.copy(weekPlan = plan)
    }

    fun normalizedPlan(state: GameState): List<String> =
        (state.weekPlan + List(7) { "rest" }).take(7).map { if (activities.any { a -> a.id == it }) it else "rest" }

    /** Grobe Wochenprognose für die Planansicht. */
    data class Forecast(val energyBalance: Int, val stressPerWeek: Int, val trainingDays: Int, val restDays: Int, val warning: String?)

    fun forecast(state: GameState): Forecast {
        val plan = normalizedPlan(state).map { activity(it) }
        val recovery = 55 + maxOf(if (state.player.apartment) 10 else 0, Home.sleepEnergyBonus(state))
        val energy = plan.sumOf { recovery - it.energy - 45 }
        val stress = plan.sumOf { it.stress } - 7 * 2
        val training = plan.count { it.attributes.isNotEmpty() && it.id != "social" }
        val rest = plan.count { it.id == "rest" }
        val warning = when {
            rest == 0 -> "Kein Ruhetag: Stress steigt, Verletzungen heilen langsamer."
            stress > 20 -> "Hohe Belastung: Ab Stress 85 erzwingt die Krankenstation Pausen."
            training == 0 -> "Kein Training geplant: kaum Fortschritt."
            else -> null
        }
        return Forecast(energy, stress, training, rest, warning)
    }

    // --- Zufallsbegegnungen (eigene Ereignisse, je höchstens 3×) ---------------------

    private data class SimEvent(val id: String, val text: String, val npc: String? = null, val minAge: Int = 16,
                                val friendship: Int = 0, val stress: Int = 0, val credits: Int = 0, val attr: String? = null)

    private val events = listOf(
        SimEvent("thom_spar", "Thom fordert dich spontan zum Sparring heraus.", "thom", friendship = 2, attr = Attr.FITNESS),
        SimEvent("birkhoff_coffee", "Birkhoff teilt seinen geheimen Kaffeevorrat mit dir.", "birkhoff", friendship = 2, stress = -4),
        SimEvent("alarm_drill", "Nächtlicher Probealarm – niemand schläft.", stress = 6),
        SimEvent("maya_sketch", "Maya zeigt dir ein neues Porträt aus ihrem Skizzenbuch.", "maya", friendship = 2),
        SimEvent("jaden_rank", "Jaden prahlt mit ihrer Platzierung. Du nimmst es als Ansporn.", "jaden", stress = 2, attr = Attr.WILLPOWER),
        SimEvent("owen_tip", "Owen zeigt dir einen Kniff am Präzisionsstand.", "owen", friendship = 1, attr = Attr.PRECISION),
        SimEvent("sonya_bug", "Sonya bittet dich, einen Fehler im Schließsystem zu testen.", "sonya", friendship = 2, attr = Attr.INTELLECT),
        SimEvent("lost_wallet", "Du findest in der Stadt ein Portemonnaie und gibst es ab. Finderlohn.", minAge = 17, credits = 20),
        SimEvent("amanda_check", "Amanda lädt dich unangekündigt zu einem Gespräch ein.", "amanda", stress = 3),
        SimEvent("ethan_letter", "Ethan bittet dich, einen Brief an seine Schwester gegenzulesen.", "ethan", friendship = 2)
    )

    // --- Simulation -----------------------------------------------------------------

    private fun known(data: GameData, s: GameState, id: String): Boolean =
        World.revealed(data, s, id) && ("met_$id" in s.flags || s.relations.containsKey(id)) &&
            CompanionAssignment.awaitingIntroduction(s)?.id != id

    /**
     * Simuliert bis zu [days] Tage. [stopOnEvents] hält bei Geburtstag 18,
     * Gefährtenankunft, neuen Story-Ereignissen und gesperrter Gesundheit an.
     */
    fun run(data: GameData, state: GameState, days: Int, stopOnEvents: Boolean = true): Result {
        if (state.activeMission != null) return state.fail("Erst den laufenden Einsatz beenden.")
        if (state.activeExam != null) return state.fail("Erst die laufende Prüfung abschließen.")
        val total = days.coerceIn(1, 3650)
        var s = state
        // Angebrochenen Tag beenden: Simulation startet am nächsten Morgen
        if (s.clock.slot > 0) s = s.copy(clock = GameClock(s.clock.day + 1, 0))
        val startDay = s.clock.day
        val p0 = s.player
        val attrsBefore = p0.attributes
        val relBefore = s.relations.mapValues { it.value.friendship }
        val storyBefore = Dialogues.pendingStory(data, s)?.id
        var income = 0; var expenses = 0; var xp = 0; var rep = 0; var prep = 0; var failed = 0
        val activityDays = mutableMapOf<String, Int>()
        val trainerSessions = mutableMapOf<String, Int>()
        val log = mutableListOf<String>()
        var stopReason = ""
        var simulated = 0
        val plan = normalizedPlan(s)
        for (i in 0 until total) {
            val day = s.clock.day
            val r = simulateDay(data, s, plan, log, trainerSessions)
            s = r.state
            simulated++
            income += r.income; expenses += r.expenses; xp += r.xp; rep += r.rep; prep += r.prep
            if (r.failed) failed++
            activityDays[r.activity] = (activityDays[r.activity] ?: 0) + 1
            // nächster Tag
            val oldAge = Ages.player(s)
            s = s.copy(clock = GameClock(day + 1, 0))
            val newAge = Ages.player(s)
            if (newAge > oldAge) {
                s = s.copy(flags = s.flags + "age_$newAge")
                log += "${Clock.dateLabel(day + 1)}: Geburtstag – du bist jetzt $newAge."
                s = Career.journal(s, "birthday", "$newAge. Geburtstag", if (newAge == 18) "Volljährig: operative Einsätze und Rekrutierungseditor freigeschaltet." else "Ein Jahr mehr in der Division.")
                if (newAge == 18) {
                    val before = s.customNpcs.size
                    s = CompanionAssignment.arrive(data, s)
                    if (s.customNpcs.size > before) log += "Michael kündigt eine neue Kollegin bzw. einen neuen Kollegen an."
                    if (stopOnEvents) { stopReason = "Volljährigkeit: 18. Geburtstag"; break }
                }
            }
            if (s.clock.day % 7 == 0) {
                val before = s.player.credits
                val w = World.weeklyTick(data, s)
                s = w.state
                val diff = s.player.credits - before
                if (diff >= 0) income += diff else expenses -= diff
                s = Ops.weeklyFactions(data, s)
            }
            s = Cases.daily(data, s)
            if (s.clock.day % Career.EVAL_INTERVAL == 0 && "met_michael" in s.flags) {
                val ev = Career.evaluate(data, s, auto = true)
                if (ev.ok) { s = ev.state; log += "${Clock.dateLabel(s.clock.day)}: Monatsbeurteilung durch Michael – Note ${s.career.evaluations.last().grade}" }
            }
            s = Career.checkGoals(s, log)
            // Lebenssimulation läuft mit; HQ-Ereignisse entstehen nur im Tagesspiel
            s = Life.advance(data, s, log, hqEvents = false)
            s = Directorate.ensure(data, s)
            s = World.daily08(data, s, log)
            if (stopOnEvents) {
                val story = Dialogues.pendingStory(data, s)
                if (story != null && story.id != storyBefore) { stopReason = "Neues Ereignis: ${story.title}"; break }
                if (s.player.health < 30) { stopReason = "Gesundheit kritisch – Krankenstation"; break }
                if (s.career.examPrepRequired > 0 && s.career.examPrep >= s.career.examPrepRequired && s.clock.day >= s.career.examCooldownUntil && s.career.examCooldownUntil >= startDay) {
                    stopReason = "Prüfungsauflage erfüllt – Wiederholung möglich"; break
                }
            }
        }
        if (stopReason.isEmpty()) stopReason = "Zeitraum vollständig simuliert"
        s = Quests.update(data, s).state
        s = Career.checkAwards(s)
        s = MissionGenerator.refreshBoard(data, s)
        // v0.7: Missionsboard nach dem Zeitraffer – abgelaufene Aufträge werden verbucht, neue nicht dupliziert
        val opsBefore = s.ops.history.size
        s = Ops.refresh(data, s)
        s.ops.history.drop(opsBefore).forEach { log += "Auftrag ${ContractStatus.label(it.status).lowercase()}: ${it.title}" }
        val p = s.player
        val gains = Attr.all.associateWith { (p.attributes[it] ?: 0) - (attrsBefore[it] ?: 0) }.filterValues { it != 0 }
        val relChanges = s.relations.mapValues { (k, v) -> v.friendship - (relBefore[k] ?: Relations.get(data, state, k).friendship) }.filterValues { it != 0 }
        val report = SimReport(
            startDay = startDay, endDay = s.clock.day, plannedDays = total, stopReason = stopReason,
            attributeGains = gains, activityDays = activityDays, failedSessions = failed, xp = xp,
            levelBefore = p0.level, levelAfter = p.level, income = income, expenses = expenses, reputation = rep,
            examPrep = prep, woundsBefore = p0.wounds, woundsAfter = p.wounds, healthBefore = p0.health, healthAfter = p.health,
            stressBefore = p0.stress, stressAfter = p.stress, relationChanges = relChanges, trainerSessions = trainerSessions,
            events = log.takeLast(40)
        )
        s = s.copy(simReports = (s.simReports + report).takeLast(5))
        s = s.copy(log = (s.log + LogEntry(s.clock.day, 0, "Zeitplan: $simulated Tage simuliert – $stopReason")).takeLast(80))
        val msgs = listOf("$simulated Tage simuliert", stopReason)
        return Result(s, msgs)
    }

    private class DayResult(val state: GameState, val activity: String, val income: Int, val expenses: Int, val xp: Int, val rep: Int, val prep: Int, val failed: Boolean)

    private fun simulateDay(data: GameData, state: GameState, plan: List<String>, log: MutableList<String>, trainers: MutableMap<String, Int>): DayResult {
        var s = state
        val day = s.clock.day
        val rng = Rng(Rng.mix(s.seed, day.toLong(), 5150))
        var p = s.player
        var act = activity(plan[(Clock.weekday(day) - 1).coerceIn(0, 6)])
        // Erzwungene Ruhe: Verletzung + Körpertraining, Erschöpfung, Stress
        if (p.wounds > 0 && act.id == "combat") { act = activity("rest"); if (rng.nextInt(4) == 0) log += "${Clock.dateLabel(day)}: Verletzung – Kampftraining durch Erholung ersetzt." }
        if (p.stress >= 85 || p.health < 35) act = activity("rest")
        // Morgendliche Energie aus der Nacht
        val recovery = 55 + maxOf(if (p.apartment) 10 else 0, Home.sleepEnergyBonus(state)) - p.stress / 5 - p.wounds * 5
        var energy = (p.energy + recovery).coerceIn(10, 100)
        var stress = p.stress
        var health = p.health
        var xp = 0; var rep = 0; var prep = 0; var income = 0; var expenses = 0
        var failed = false
        val cap = Stats.attributeCap(p.level)
        var attrs = p.attributes
        var partial = p.trainingProgress
        var career = s.career
        if (act.attributes.isNotEmpty() && act.id != "social") {
            val shortfall = (act.energy - energy).coerceAtLeast(0)
            val chance = (88 - stress / 2 - shortfall * 2).coerceIn(15, 95)
            if (rng.percent() <= chance) {
                val attr = act.attributes[day % act.attributes.size]
                // Ausbilder: wer an diesem Tag laut Dienstplan im passenden Raum ist
                val trainerRoom = when (act.id) { "combat" -> listOf("training", "range"); "tech" -> listOf("tech"); "recon" -> listOf("ops", "archives"); else -> listOf("psych", "training") }
                val candidates = Progression.trainers.keys.filter { id ->
                    known(data, s, id) && attr in (Progression.trainers[id] ?: emptySet()) &&
                        (1..3).any { slot -> World.npcLocation(data, s.copy(clock = GameClock(day, slot)), id) in trainerRoom }
                }
                val trainer = candidates.maxByOrNull { Progression.trainerBonus(data, s, it, attr) }
                val bonus = Progression.trainerBonus(data, s, trainer, attr) + (Directorate.power(data, s, "training") - 1) * 6
                val cur = attrs[attr] ?: 30
                val (nv, prog) = Progression.attributeProgress(cur, partial[attr] ?: 0, cap, rng.nextInt(25), bonus, 0.9)
                attrs = attrs + (attr to nv); partial = partial + (attr to prog)
                xp = Progression.trainingXp(act.xp, p.level)
                career = career.copy(trainingDays = (career.trainingDays.filter { it > day - 90 } + day).distinct())
                if (trainer != null && Progression.trainerBonus(data, s, trainer, attr) > 0) {
                    trainers[trainer] = (trainers[trainer] ?: 0) + 1
                    s = Relations.update(data, s, trainer) { it.copy(respect = it.respect + if (rng.nextInt(5) == 0) 1 else 0) }
                }
                if (act.id == "exam") prep = 6
                // Verletzungsrisiko bei Kampftraining unter Erschöpfung
                if (act.id == "combat" && energy < act.energy && rng.percent() <= 5) {
                    p = p.copy(wounds = p.wounds + 1)
                    log += "${Clock.dateLabel(day)}: Beim Training verletzt (Wunden +1)."
                }
            } else {
                failed = true
                if (act.id == "exam") prep = 2
            }
            energy -= act.energy
            stress += act.stress
        } else when (act.id) {
            "social" -> {
                energy -= act.energy; stress += act.stress
                // Nur anwesende, bekannte NPCs am Mittag oder Abend
                val present = (data.agents.map { it.id } + s.customNpcs.map { it.id }).filter { id ->
                    known(data, s, id) && listOf(2, 4).any { slot -> World.npcLocation(data, s.copy(clock = GameClock(day, slot)), id) in setOf("canteen", "dorm", "lobby", "city") }
                }
                if (present.isNotEmpty()) {
                    val who = present[rng.nextInt(present.size)]
                    val rel = Relations.get(data, s, who)
                    val gain = if (rel.friendship >= 70) (if (rng.nextInt(2) == 0) 1 else 0) else 2
                    s = Relations.update(data, s, who) { it.copy(friendship = it.friendship + gain, trust = it.trust + if (gain > 0 && rng.nextInt(3) == 0) 1 else 0, lastTalkDay = day) }
                    val cha = attrs[Attr.CHARISMA] ?: 30
                    val (nv, prog) = Progression.attributeProgress(cha, partial[Attr.CHARISMA] ?: 0, cap, rng.nextInt(15), 0, 0.5)
                    attrs = attrs + (Attr.CHARISMA to nv); partial = partial + (Attr.CHARISMA to prog)
                    xp = Progression.trainingXp(act.xp, p.level)
                } else failed = true
            }
            "duty" -> {
                energy -= act.energy; stress += act.stress
                xp = Progression.trainingXp(act.xp, p.level)
                val bonusPay = if (p.rank >= 2) 10 + p.rank * 2 else 0
                income += bonusPay
                // Ruf aus Dienst: höchstens 2 pro Woche, gedeckelt durch Rang
                val weekDuty = (Clock.weekday(day) <= 5)
                if (weekDuty && rng.nextInt(3) == 0 && p.reputation < 60 + p.rank * 80) rep = 1
            }
            else -> { // rest
                stress += act.stress
                health += 4
            }
        }
        // Natürliche Erholung & Heilung
        stress = (stress - 2).pct()
        var wounds = p.wounds
        var woundProgress = p.woundProgress
        if (wounds > 0) {
            woundProgress += if (act.id == "rest") 2 else 1
            if (woundProgress >= 7) {
                wounds -= 1; woundProgress = 0
                log += "${Clock.dateLabel(day)}: Eine Verletzung ist verheilt."
            }
            health += 1
        } else { woundProgress = 0; health += 3 }
        health = health.coerceIn(1, 100)
        // Zufallsbegegnung (höchstens alle paar Wochen, jedes Ereignis max. 3×)
        if (rng.percent() <= 4) {
            val age = Ages.player(s)
            val options = events.filter { e -> (s.simEventCounts[e.id] ?: 0) < 3 && age >= e.minAge && (e.npc == null || known(data, s, e.npc)) }
            if (options.isNotEmpty()) {
                val e = options[rng.nextInt(options.size)]
                s = s.copy(simEventCounts = s.simEventCounts + (e.id to (s.simEventCounts[e.id] ?: 0) + 1))
                if (e.npc != null && e.friendship != 0) s = Relations.update(data, s, e.npc) { it.copy(friendship = it.friendship + e.friendship, lastTalkDay = day) }
                stress = (stress + e.stress).pct()
                income += e.credits
                if (e.attr != null) {
                    val cur = attrs[e.attr] ?: 30
                    val (nv, prog) = Progression.attributeProgress(cur, partial[e.attr] ?: 0, cap, 10, 0, 0.6)
                    attrs = attrs + (e.attr to nv); partial = partial + (e.attr to prog)
                }
                log += "${Clock.dateLabel(day)}: ${e.text}"
            }
        }
        // Kontakte schlafen ein: einmal pro Monat, nur bei länger fehlendem Kontakt
        if (day % 30 == 15) {
            s.relations.forEach { (id, r) ->
                if (r.friendship > 30 && r.lastTalkDay >= 0 && day - r.lastTalkDay > 60 && World.revealed(data, s, id)) {
                    s = Relations.update(data, s, id) { it.copy(friendship = it.friendship - 1) }
                }
            }
        }
        val credits = (p.credits + income).coerceAtLeast(0)
        p = s.player.copy(
            attributes = attrs, trainingProgress = partial, energy = energy.coerceIn(0, 100), stress = stress.pct(),
            health = health, wounds = wounds, woundProgress = woundProgress, credits = credits,
            reputation = s.player.reputation + rep
        )
        career = career.copy(examPrep = (career.examPrep + prep).coerceAtMost(100))
        s = s.copy(player = p, career = career)
        if (xp > 0) s = Progression.grantXp(data, s, xp).state
        return DayResult(s, act.id, income, expenses, xp, rep, prep, failed)
    }
}

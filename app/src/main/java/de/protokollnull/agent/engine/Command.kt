package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v0.8 Teamführung ab Rang 8: Rollen, Bereitschaft, delegierbare Teamarbeit,
 * Kohäsion und Teamkonflikte. Nutzt Life (Status, Kompetenzen, Stimmung,
 * Stress) und Relations – keine dekorativen Werte: jede Größe wirkt auf
 * Einsätze, Karrieren oder Beziehungen.
 */
object Command {
    const val LEAD_RANK = 8
    const val DELEGATE_RANK = 9
    const val LOG_CAP = 40

    data class Role(val id: String, val label: String, val area: String)
    val roles = listOf(
        Role("recon", "Aufklärung", "intel"),
        Role("tech", "Technik", "tech"),
        Role("medic", "Medizin", "medic"),
        Role("comms", "Kommunikation", "social"),
        Role("support", "Operative Unterstützung", "combat")
    )
    val roleById = roles.associateBy { it.id }

    data class TaskKind(
        val id: String, val label: String, val description: String, val days: Int,
        val minorOk: Boolean, val needsPartner: Boolean = false, val energy: Int = 10, val budget: Int = 0
    )
    val taskKinds = listOf(
        TaskKind("training", "Training", "Gezieltes Training im Schwerpunkt: Kompetenz und Karrierepunkte steigen, Stress etwas.", 3, true),
        TaskKind("prep", "Einsatzvorbereitung", "Material und Pläne vorbereiten: nächster eigener Einsatz startet mit +1 Intel.", 2, false),
        TaskKind("recon", "Vorab-Aufklärung", "Erkundet den dringendsten offenen Auftrag: +1 Intel für diesen Auftrag.", 2, false),
        TaskKind("mentoring", "Mentoring", "Eine erfahrene Figur betreut eine jüngere: Karrierepunkte und Bindung.", 5, true, needsPartner = true),
        TaskKind("roster", "Dienstplanung", "Schichten und Pausen ordnen: Teamkohäsion +, Stress im Team −.", 1, true),
        TaskKind("debrief", "Nachbereitung", "Den letzten Einsatz aufarbeiten: Stress −, Stimmung +, Kohäsion +.", 1, true),
        TaskKind("recovery", "Erholung", "Freistellung für einige Tage: deutlich weniger Stress, danach wieder verfügbar.", 4, true, energy = 5),
        TaskKind("course", "Weiterbildung", "Externer Lehrgang (800 Cr Etat): +1 Kompetenzstufe im Schwerpunkt.", 5, true, budget = 800)
    )
    val taskById = taskKinds.associateBy { it.id }

    fun isLeader(state: GameState) = state.player.rank >= LEAD_RANK

    // --- Status und Verfügbarkeit -----------------------------------------------------

    data class Status(val code: String, val label: String, val available: Boolean, val trainingOnly: Boolean = false)

    /** Aktuelle Einsetzbarkeit einer Figur mit nachvollziehbarer Begründung. */
    fun status(data: GameData, state: GameState, id: String, forOps: Boolean = true): Status {
        val custom = state.customNpcs.firstOrNull { it.id == id }
        if (!Relations.isKnownNpc(data, state, id) || !World.revealed(data, state, id) || (custom != null && state.clock.day < custom.createdDay))
            return Status("unknown", "unbekannt", false)
        val l = state.living.lives[id]
        when (l?.status) {
            LifeStatus.INJURED -> return Status("injured", if (l.statusNote.contains("krank", true)) "krank" else "verletzt", false)
            LifeStatus.MISSION -> return Status("mission", "im Einsatz", false)
            LifeStatus.VACATION -> return Status("leave", when {
                l.statusNote.startsWith("Weiterbildung") -> "in Weiterbildung"
                l.statusNote.startsWith("Erholung") || l.statusNote.contains("Beurlaub") -> "freigestellt"
                else -> "im Urlaub"
            }, false)
            LifeStatus.ABSENT -> return Status("absent", "abwesend", false)
            LifeStatus.TRANSFERRED -> return Status("transferred", "versetzt", false)
        }
        Personnel.assignmentOf(data, state, id)?.let { return Status("assigned", "eingesetzt: $it", false) }
        state.lead.tasks.firstOrNull { (it.npcId == id || it.partnerId == id) && !it.done }?.let { return Status("task", "beschäftigt: " + (taskById[it.kind]?.label ?: it.kind), false) }
        if (Ages.npc(data, state, id) < 18) return Status("minor", "minderjährig – nur Training", true, trainingOnly = true)
        if (forOps && Life.rank(data, state, id) < 2) return Status("rank", "rangbedingt ungeeignet für Einsätze", true, trainingOnly = true)
        return Status("ok", "verfügbar", true)
    }

    /** Personal, über das der Spieler als Teamleitung verfügen kann. */
    fun staff(data: GameData, state: GameState): List<String> =
        (data.agents.filter { it.factionId == "division" && it.id !in setOf("percy", "amanda") && it.lifeProfile != "leader" }.map { it.id } +
            state.customNpcs.map { it.id }).filter { World.revealed(data, state, it) }

    fun roleFit(data: GameData, state: GameState, id: String, role: String): Int =
        roleById[role]?.let { Life.competence(data, state, id, it.area) } ?: 0

    /** Bereitschaft einer Figur (0..100) aus Stimmung, Stress und Verletzungsfreiheit. */
    fun readiness(data: GameData, state: GameState, id: String): Int {
        val l = Life.life(data, state, id)
        val st = status(data, state, id)
        val base = (l.mood + (100 - l.stress)) / 2
        return if (!st.available) (base / 3) else base.coerceIn(0, 100)
    }

    fun teamReadiness(data: GameData, state: GameState): Int =
        state.team.map { readiness(data, state, it) }.ifEmpty { listOf(0) }.average().toInt()

    // --- Rollen -------------------------------------------------------------------------

    fun setRole(data: GameData, state: GameState, id: String, role: String?): Result {
        if (!isLeader(state)) return state.fail("Teamrollen ab Rang 8 (Teamleiter).")
        if (id !in state.team) return state.fail("Nur Mitglieder deines Teams.")
        if (role != null && role !in roleById) return state.fail("Unbekannte Rolle")
        val roles = if (role == null) state.lead.roles - id else state.lead.roles.filterValues { it != role } + (id to role)
        val name = Relations.displayName(data, state, id).substringBefore(" ")
        val fit = role?.let { roleFit(data, state, id, it) } ?: 0
        val s = state.copy(lead = state.lead.copy(roles = roles))
        return Result(s, listOf(if (role == null) "$name hat keine feste Rolle mehr." else "$name übernimmt ${roleById.getValue(role).label} (Kompetenz $fit/10)."))
    }

    /** Einsatzbonus aus Rollenbesetzung und Kohäsion; schlechte Führung erschwert, verhindert aber nichts. */
    fun missionBonus(data: GameData, state: GameState, team: List<String>): Int {
        if (!isLeader(state) || team.isEmpty()) return 0
        val filled = team.mapNotNull { id -> state.lead.roles[id]?.let { r -> if (roleFit(data, state, id, r) >= 4) r else null } }.toSet().size
        val cohesion = (state.lead.cohesion - 50) / 10
        return (filled * 2 + cohesion).coerceIn(-8, 12)
    }

    // --- Aufgaben ------------------------------------------------------------------------

    fun canAssign(data: GameData, state: GameState, npcId: String, kind: String, partnerId: String? = null): String? {
        val k = taskById[kind] ?: return "Unbekannte Aufgabe"
        if (!isLeader(state) && kind != "training") return "Teamaufgaben ab Rang 8 (Teamleiter)."
        if (npcId !in staff(data, state)) return "Nicht in deinem Personal"
        val st = status(data, state, npcId, forOps = false)
        if (!st.available) return st.label
        if (Ages.npc(data, state, npcId) < 18 && !k.minorOk) return "Unter 18 nur Training und ungefährliche Tätigkeiten"
        if (state.player.energy < k.energy) return "Zu erschöpft (Energie ≥ ${k.energy})"
        if (k.budget > 0 && Directorate.budget(state) < k.budget) return "Etat reicht nicht (${k.budget} Cr)"
        if (kind == "debrief" && state.reports.none { state.clock.day - it.day <= 7 }) return "Kein Einsatz in den letzten 7 Tagen"
        if (kind == "recon" && state.ops.contracts.none { it.status == ContractStatus.OPEN || it.status == ContractStatus.POSTPONED }) return "Kein offener Auftrag"
        if (k.needsPartner) {
            if (partnerId == null) return "Mentor bzw. Mentorin wählen"
            if (partnerId == npcId) return "Niemand betreut sich selbst"
            val ps = status(data, state, partnerId, forOps = false)
            if (!ps.available) return "Mentor: ${ps.label}"
            if (Ages.npc(data, state, partnerId) < 18) return "Mentoren müssen volljährig sein"
            if (Life.rank(data, state, partnerId) <= Life.rank(data, state, npcId)) return "Mentor braucht einen höheren Rang"
        }
        return null
    }

    fun assign(data: GameData, state: GameState, npcId: String, kind: String, partnerId: String? = null): Result {
        canAssign(data, state, npcId, kind, partnerId)?.let { return state.fail(it) }
        val k = taskById.getValue(kind)
        val day = state.clock.day
        val id = "T${state.lead.counter}"
        val task = TeamTask(id, npcId, kind, day, day + k.days - 1, partnerId)
        var s = state.copy(lead = state.lead.copy(tasks = (state.lead.tasks + task).takeLast(40), counter = state.lead.counter + 1),
            player = state.player.copy(energy = (state.player.energy - k.energy).pct()))
        if (k.budget > 0) s = Directorate.spend(s, k.budget)
        val (status, note) = when (kind) {
            "training" -> LifeStatus.TRAINING to "Teamtraining"
            "recovery" -> LifeStatus.VACATION to "Erholung"
            "course" -> LifeStatus.VACATION to "Weiterbildung"
            else -> LifeStatus.DUTY to "Auftrag: ${k.label}"
        }
        s = Life.apply(data, s, LifeChange(npcId, status = status, days = k.days, note = note))
        if (partnerId != null) s = Life.apply(data, s, LifeChange(partnerId, status = LifeStatus.DUTY, days = k.days, note = "Mentoring"))
        val name = Relations.displayName(data, s, npcId).substringBefore(" ")
        s = log(s, "$name: ${k.label} bis ${Clock.dateLabel(task.endDay)}")
        val t = World.passTime(data, s, 1)
        return Result(t.state, listOf("$name übernimmt „${k.label}“ (${k.days} Tag${if (k.days == 1) "" else "e"}).") + t.messages)
    }

    fun log(state: GameState, line: String) =
        state.copy(lead = state.lead.copy(log = (state.lead.log + "${Clock.dateLabel(state.clock.day)}: $line").takeLast(LOG_CAP)))

    private fun putLife(state: GameState, id: String, f: (NpcLife) -> NpcLife, data: GameData) =
        state.copy(living = state.living.copy(lives = state.living.lives + (id to f(Life.life(data, state, id)))))

    /** Täglich: abgelaufene Aufgaben auswerten (genau einmal). */
    fun daily(data: GameData, state: GameState): GameState {
        var s = state
        val day = s.clock.day
        s.lead.tasks.filter { !it.done && it.endDay < day }.forEach { t ->
            s = resolveTask(data, s, t)
        }
        // Ausbauten wirken auf trainierendes Personal
        val hall = Sites.moduleLevel(s, "training_hall") + Sites.siteLevel(data, s, "base")
        if (hall > 0) s.living.lives.filter { it.value.status == LifeStatus.TRAINING }.keys.forEach { id ->
            s = putLife(s, id, { it.copy(careerXp = it.careerXp + hall) }, data)
        }
        return s
    }

    private fun resolveTask(data: GameData, state: GameState, t: TeamTask): GameState {
        var s = state
        val name = Relations.displayName(data, s, t.npcId).substringBefore(" ")
        val l = Life.life(data, s, t.npcId)
        val boost = if ("proj_sim_course" in s.flags) 1 else 0
        val result = when (t.kind) {
            "training" -> {
                val area = l.focus
                val prog = (l.skillProgress[area] ?: 0) + 25 + boost * 10
                val lvl = l.skillGain[area] ?: 0
                val up = prog >= 40 * (lvl + 1) && lvl < 5
                s = putLife(s, t.npcId, { it.copy(careerXp = it.careerXp + 20, stress = (it.stress + 6).pct(),
                    skillProgress = it.skillProgress + (area to if (up) 0 else prog), skillGain = if (up) it.skillGain + (area to lvl + 1) else it.skillGain) }, data)
                s = s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion + 1).pct()))
                "$name: Training abgeschlossen (+20 Karrierepunkte" + if (up) ", ${Life.focusLabel(area)} Stufe ${lvl + 1})" else ")"
            }
            "prep" -> {
                s = s.copy(lead = s.lead.copy(prepReady = (s.lead.prepReady + 1).coerceAtMost(3)))
                s = putLife(s, t.npcId, { it.copy(careerXp = it.careerXp + 8) }, data)
                "$name: Vorbereitung abgeschlossen – nächster Einsatz +1 Intel"
            }
            "recon" -> {
                val target = s.ops.contracts.filter { it.status == ContractStatus.OPEN || it.status == ContractStatus.POSTPONED }
                    .sortedWith(compareBy({ -it.urgency }, { it.expiresDay })).firstOrNull()
                if (target != null) {
                    s = s.copy(ops = s.ops.copy(contracts = s.ops.contracts.map { if (it.id == target.id) it.copy(carriedIntel = it.carriedIntel + 1 + Life.competence(data, s, t.npcId, "intel") / 5) else it }))
                    "$name: Vorab-Aufklärung zu „${target.title}“ – zusätzliche Intel gesichert"
                } else "$name: Vorab-Aufklärung ohne Ziel – der Auftrag war schon vergeben"
            }
            "mentoring" -> {
                val mentor = t.partnerId
                val factor = if ("proj_mentor_program" in s.flags) 2 else 1
                s = putLife(s, t.npcId, { it.copy(careerXp = it.careerXp + 30 * factor, mood = (it.mood + 5).pct()) }, data)
                if (mentor != null) {
                    s = putLife(s, mentor, { it.copy(stress = (it.stress + 5).pct()) }, data)
                    val key = LivingState.key(t.npcId, mentor)
                    s = s.copy(living = s.living.copy(bonds = s.living.bonds + (key to (Life.bond(s, t.npcId, mentor) + 10).coerceIn(-100, 100))),
                        lead = s.lead.copy(mentors = s.lead.mentors + (t.npcId to mentor)))
                }
                "$name: Mentoring abgeschlossen (+${30 * factor} Karrierepunkte)"
            }
            "roster" -> {
                s.team.forEach { id -> s = putLife(s, id, { it.copy(stress = (it.stress - 5).pct()) }, data) }
                s = s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion + 4 + Life.competence(data, s, t.npcId, "social") / 3).pct()))
                "$name: Dienstplan steht – Team entlastet, Kohäsion steigt"
            }
            "debrief" -> {
                s.team.forEach { id -> s = putLife(s, id, { it.copy(stress = (it.stress - 10).pct(), mood = (it.mood + 5).pct()) }, data) }
                s = s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion + 2).pct()))
                "$name: Nachbereitung abgeschlossen – das Team verarbeitet den Einsatz"
            }
            "recovery" -> {
                s = putLife(s, t.npcId, { it.copy(stress = (it.stress - 30).pct(), mood = (it.mood + 10).pct()) }, data)
                "$name ist erholt zurück"
            }
            "course" -> {
                val area = l.focus
                val lvl = l.skillGain[area] ?: 0
                s = putLife(s, t.npcId, { it.copy(skillGain = it.skillGain + (area to (lvl + 1).coerceAtMost(5)), careerXp = it.careerXp + 15) }, data)
                s = Relations.update(data, s, t.npcId) { it.copy(loyalty = it.loyalty + 3) }
                "$name: Weiterbildung abgeschlossen – ${Life.focusLabel(area)} Stufe ${(lvl + 1).coerceAtMost(5)}"
            }
            else -> "$name: Aufgabe erledigt"
        }
        s = s.copy(lead = s.lead.copy(tasks = s.lead.tasks.map { if (it.id == t.id) it.copy(done = true, result = result) else it }))
        s = log(s, result)
        return s
    }

    // --- Wöchentliche Teamentwicklung -------------------------------------------------------

    fun weekly(data: GameData, state: GameState): GameState {
        if (!isLeader(state)) return state
        var s = state
        var c = s.lead.cohesion
        val team = s.team
        // Spannungen im Team kosten Kohäsion und erzeugen einen Eintrag
        for (i in team.indices) for (j in i + 1 until team.size) {
            val b = Life.bond(s, team[i], team[j])
            if (b <= -20) {
                c -= 3
                s = log(s, "Spannungen zwischen ${Relations.displayName(data, s, team[i]).substringBefore(" ")} und ${Relations.displayName(data, s, team[j]).substringBefore(" ")} belasten das Team.")
            } else if (b >= 40) c += 1
        }
        val stress = team.map { Life.life(data, s, it).stress }.ifEmpty { listOf(0) }.average()
        if (stress > 65) { c -= 2; s = log(s, "Das Team ist überlastet (Ø Stress ${stress.toInt()}). Erholung einplanen.") }
        c += (50 - c).coerceIn(-1, 1) // langsames Einpendeln
        return s.copy(lead = s.lead.copy(cohesion = c.pct()))
    }

    /** Moderierte Aussprache zwischen zwei zerstrittenen Teammitgliedern. */
    fun mediate(data: GameData, state: GameState, a: String, b: String): Result {
        if (!isLeader(state)) return state.fail("Ab Rang 8.")
        if (a == b || a !in state.team || b !in state.team) return state.fail("Zwei Mitglieder deines Teams wählen.")
        if (state.player.energy < 15) return state.fail("Zu erschöpft.")
        val key = "mediate_${LivingState.key(a, b)}"
        if ((state.life.lastActivity[key] ?: -99) > state.clock.day - 7) return state.fail("Diese Aussprache hat erst kürzlich stattgefunden.")
        val (s0, roll) = Checks.roll(data, state, CheckDef(Attr.CHARISMA, "empathy", 40), salt = key.hashCode().toLong())
        val delta = if (roll.success) 18 else 4
        var s = s0.copy(living = s0.living.copy(bonds = s0.living.bonds + (LivingState.key(a, b) to (Life.bond(s0, a, b) + delta).coerceIn(-100, 100))),
            player = s0.player.copy(energy = (s0.player.energy - 15).pct()), life = s0.life.copy(lastActivity = s0.life.lastActivity + (key to s0.clock.day)))
        if (roll.success) s = s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion + 5).pct()))
        val na = Relations.displayName(data, s, a).substringBefore(" "); val nb = Relations.displayName(data, s, b).substringBefore(" ")
        s = log(s, if (roll.success) "Aussprache zwischen $na und $nb geglückt." else "Aussprache zwischen $na und $nb ohne Durchbruch.")
        val t = World.passTime(data, s, 1)
        return Result(t.state, listOf(if (roll.success) "$na und $nb räumen ihre Differenzen aus. (${roll.roll}/${roll.chance})" else "Das Gespräch bleibt zäh. (${roll.roll}/${roll.chance})") + t.messages, ok = roll.success)
    }

    /** Teambilanz nach einem Einsatz mit Team. */
    fun recordMission(state: GameState, team: List<String>, success: Boolean): GameState {
        if (!isLeader(state) || team.isEmpty()) return state
        val l = state.lead
        return state.copy(lead = l.copy(
            cohesion = (l.cohesion + if (success) 4 else -5).pct(),
            teamWins = l.teamWins + if (success) 1 else 0, teamLosses = l.teamLosses + if (success) 0 else 1
        ))
    }
}

/**
 * v0.8 Delegation echter v0.7-Aufträge ab Rang 9. Status: vorbereitet → läuft →
 * abgeschlossen/gescheitert/abgebrochen. Der Abschluss erfolgt ausschließlich
 * am Rückkehrtag über den Tageswechsel und wird genau einmal verbucht.
 */
object Delegations {
    fun delegable(data: GameData, state: GameState): List<Contract> = state.ops.contracts.filter {
        (it.status == ContractStatus.OPEN || it.status == ContractStatus.POSTPONED) && it.chainId == null && it.caseId == null &&
            state.lead.delegations.none { d -> d.contractId == it.id && !DelegationStatus.closed(d.status) }
    }

    fun maxTeam(data: GameData, state: GameState, c: Contract) = minOf(c.maxTeam + 1, Progression.teamSize(data, state)).coerceAtLeast(1)

    fun duration(c: Contract) = (1 + c.phases + c.difficulty / 2).coerceAtMost(5)

    /** Erfolgschance aus Kompetenzen, Rang, Bereitschaft, Kohäsion und Abteilungen. */
    fun chance(data: GameData, state: GameState, c: Contract, team: List<String>, leaderId: String?): Int {
        if (team.isEmpty()) return 0
        val fit = team.map { id -> c.skills.maxOfOrNull { Life.competence(data, state, id, it) } ?: 3 }.average()
        val combat = team.map { Life.competence(data, state, it, "combat") }.average()
        val ranks = team.map { Life.rank(data, state, it) }.average()
        val ready = team.map { Command.readiness(data, state, it) }.average()
        val lead = leaderId?.let { Life.rank(data, state, it) * 2 + Relations.get(data, state, it).loyalty / 10 } ?: 0
        val intel = Directorate.power(data, state, "intel") * 2 + Sites.siteLevel(data, state, "analysis") * 3
        val base = 30 + (fit * 5).toInt() + (combat * 2).toInt() + (ranks * 2).toInt() + (ready / 10).toInt() + lead + intel +
            team.size * 4 + (state.lead.cohesion - 50) / 5 - c.difficulty * 9
        return base.coerceIn(10, 92)
    }

    fun validate(data: GameData, state: GameState, c: Contract, team: List<String>, leaderId: String?): String? {
        if (state.player.rank < Command.DELEGATE_RANK) return "Delegieren ab Rang 9 (Einsatzkommandant)."
        if (c !in delegable(data, state)) return "Dieser Auftrag kann nicht delegiert werden."
        if (team.isEmpty()) return "Mindestens ein Teammitglied wählen."
        if (team.distinct().size != team.size) return "Doppelte Auswahl"
        if (team.size > maxTeam(data, state, c)) return "Höchstens ${maxTeam(data, state, c)} Personen für diesen Auftrag."
        if (leaderId != null && leaderId !in team) return "Die Leitung muss zum Team gehören."
        team.forEach { id ->
            val st = Command.status(data, state, id)
            if (!st.available || st.trainingOnly) return "${Relations.displayName(data, state, id).substringBefore(" ")}: ${st.label}"
            if (Ages.npc(data, state, id) < 18) return "Minderjährige werden nicht in Einsätze geschickt."
            if (c.difficulty >= 4 && Life.rank(data, state, id) < 3) return "${Relations.displayName(data, state, id).substringBefore(" ")}: Rang zu niedrig für Gefahrenstufe ${c.difficulty}"
        }
        if (leaderId != null && Life.rank(data, state, leaderId) < (c.difficulty - 1).coerceAtLeast(2)) return "Die Leitung braucht mindestens Rang ${(c.difficulty - 1).coerceAtLeast(2)}."
        return null
    }

    fun prepare(data: GameData, state: GameState, contractId: String, team: List<String>, leaderId: String?): Result {
        val c = Ops.contract(state, contractId) ?: return state.fail("Auftrag unbekannt")
        validate(data, state, c, team, leaderId)?.let { return state.fail(it) }
        val id = "D${state.lead.counter}"
        val d = Delegation(id, c.id, c.title, team, leaderId, DelegationStatus.PREPARED, preparedDay = state.clock.day, chance = chance(data, state, c, team, leaderId))
        val s = state.copy(lead = state.lead.copy(delegations = (state.lead.delegations + d).takeLast(30), counter = state.lead.counter + 1))
        return Result(Command.log(s, "Delegation vorbereitet: ${c.title}"), listOf("Delegation vorbereitet: „${c.title}“ · Erfolgsaussicht ${d.chance} %"))
    }

    fun start(data: GameData, state: GameState, delegationId: String): Result {
        val d = state.lead.delegations.firstOrNull { it.id == delegationId } ?: return state.fail("Delegation unbekannt")
        if (d.status != DelegationStatus.PREPARED) return state.fail("Bereits ${DelegationStatus.label(d.status).lowercase()}.")
        val c = Ops.contract(state, d.contractId) ?: return state.fail("Auftrag nicht mehr verfügbar")
        validate(data, state.copy(lead = state.lead.copy(delegations = state.lead.delegations.filter { it.id != d.id })), c, d.team, d.leaderId)?.let { return state.fail(it) }
        val days = duration(c)
        val chance = chance(data, state, c, d.team, d.leaderId)
        val nd = d.copy(status = DelegationStatus.RUNNING, startDay = state.clock.day, returnDay = state.clock.day + days, chance = chance)
        var s = state.copy(
            lead = state.lead.copy(delegations = state.lead.delegations.map { if (it.id == d.id) nd else it }),
            ops = state.ops.copy(contracts = state.ops.contracts.map { if (it.id == c.id) it.copy(status = ContractStatus.DELEGATED, expiresDay = maxOf(it.expiresDay, nd.returnDay + 3)) else it })
        )
        d.team.forEach { id -> s = Life.apply(data, s, LifeChange(id, status = LifeStatus.MISSION, days = days + 1, note = "Delegierter Einsatz")) }
        s = Command.log(s, "Delegation gestartet: ${c.title} (Rückkehr ${Clock.dateLabel(nd.returnDay)})")
        return Result(s, listOf("Das Team bricht auf: „${c.title}“. Rückkehr voraussichtlich ${Clock.dateLabel(nd.returnDay)}."))
    }

    fun abort(data: GameData, state: GameState, delegationId: String): Result {
        val d = state.lead.delegations.firstOrNull { it.id == delegationId } ?: return state.fail("Delegation unbekannt")
        if (DelegationStatus.closed(d.status)) return state.fail("Bereits beendet.")
        var s = state
        if (d.status == DelegationStatus.PREPARED) {
            s = s.copy(lead = s.lead.copy(delegations = s.lead.delegations.filter { it.id != d.id }))
            return Result(s, listOf("Vorbereitung verworfen: „${d.title}“."))
        }
        val c = Ops.contract(s, d.contractId)
        d.team.forEach { id ->
            s = Life.apply(data, s, LifeChange(id, status = LifeStatus.DUTY, days = 0, mood = -5))
            s = Relations.update(data, s, id) { it.copy(trust = it.trust - 2, memories = it.memories + "Abgebrochener Einsatz: ${d.title}") }
        }
        s = s.copy(lead = s.lead.copy(delegations = s.lead.delegations.map { if (it.id == d.id) it.copy(status = DelegationStatus.ABORTED, settled = true, report = listOf("Vom Kommando abgebrochen.")) else it },
            cohesion = (s.lead.cohesion - 3).pct()))
        if (c != null) s = Ops.settleContract(data, s.copy(ops = s.ops.copy(contracts = s.ops.contracts.map { if (it.id == c.id) it.copy(status = ContractStatus.OPEN) else it })), c, "abort", null).first
        s = Command.log(s, "Delegation abgebrochen: ${d.title}")
        return Result(s, listOf("Einsatz „${d.title}“ abgebrochen. Das Team kehrt zurück, der Auftraggeber ist verärgert.", "Teamkohäsion −3 · Vertrauen −2"))
    }

    /** Täglich: Rückkehr, Verzögerungen und einmalige Abrechnung. */
    fun daily(data: GameData, state: GameState): GameState {
        var s = state
        val day = s.clock.day
        s.lead.delegations.filter { it.status == DelegationStatus.RUNNING && it.returnDay <= day && !it.settled }.forEach { d ->
            val rng = Rng(Rng.mix(s.seed, d.id.hashCode().toLong(), day.toLong(), 8080))
            if (!d.delayed && rng.percent() <= 20) {
                val extra = rng.range(1, 2)
                val nd = d.copy(delayed = true, returnDay = d.returnDay + extra, report = d.report + "Verzögerung: ${listOf("Die Zielperson änderte ihre Route", "Ein Kontakt erschien nicht", "Die Ausweichroute war gesperrt").let { rng.pick(it) }} – Rückkehr ${extra} Tag(e) später.")
                s = s.copy(lead = s.lead.copy(delegations = s.lead.delegations.map { if (it.id == d.id) nd else it }))
                nd.team.forEach { id -> s = Life.apply(data, s, LifeChange(id, status = LifeStatus.MISSION, days = extra + 1, note = "Delegierter Einsatz")) }
                s = Command.log(s, "Delegation „${d.title}“ verzögert sich.")
                return@forEach
            }
            s = resolve(data, s, d, rng)
        }
        return s
    }

    private fun resolve(data: GameData, state: GameState, d: Delegation, rng: Rng): GameState {
        var s = state
        val c = Ops.contract(s, d.contractId)
        val roll = rng.percent()
        val success = roll <= d.chance
        val report = d.report.toMutableList()
        report += if (success) "Auftrag erfüllt ($roll/${d.chance})." else "Auftrag gescheitert ($roll/${d.chance})."
        d.team.forEach { id ->
            val hurt = rng.percent() <= (if (success) 10 else 35) - Sites.siteLevel(data, s, "base") * 3
            val name = Relations.displayName(data, s, id).substringBefore(" ")
            val l = Life.life(data, s, id)
            var nl = if (success) l.copy(missions = l.missions + 1, careerXp = l.careerXp + 20 + (c?.difficulty ?: 1) * 6, mood = (l.mood + 5).pct())
                else l.copy(missionsFailed = l.missionsFailed + 1, careerXp = l.careerXp + 6, mood = (l.mood - 8).pct(), stress = (l.stress + 10).pct())
            nl = if (hurt) {
                report += "$name wurde verletzt."
                nl.copy(status = LifeStatus.INJURED, statusUntil = s.clock.day + 3 + (c?.difficulty ?: 1) - (if ("proj_trauma_unit" in s.flags) 2 else 0), statusNote = "Einsatzverletzung", injuries = nl.injuries + 1)
            } else nl.copy(status = LifeStatus.DUTY, statusUntil = -1, statusNote = "")
            s = s.copy(living = s.living.copy(lives = s.living.lives + (id to nl)))
            s = Relations.update(data, s, id) {
                it.copy(loyalty = it.loyalty + if (success) 3 else -1, trust = it.trust + if (success) 2 else -2,
                    memories = it.memories + "Delegierter Einsatz: ${d.title}" + if (success) "" else " – gescheitert")
            }
        }
        s = Command.recordMission(s, d.team, success)
        if (c != null) {
            val restored = s.copy(ops = s.ops.copy(contracts = s.ops.contracts.map { if (it.id == c.id) it.copy(status = ContractStatus.OPEN, phase = it.phases - 1) else it }))
            val (ns, notes) = Ops.settleContract(data, restored, c.copy(phase = c.phases - 1), if (success) "success" else "fail",
                MissionRun(c.id, d.id.hashCode().toLong(), "delegated", intel = if (success) 2 else 0))
            s = ns; report += notes
            if (success) {
                s = Directorate.earn(s, c.credits)
                report += "Organisationsbudget +${c.credits} Cr"
                val xr = Progression.grantXp(data, s, c.xp / 3)
                s = xr.state
                s = s.copy(player = s.player.copy(reputation = s.player.reputation + c.reputation / 2))
            } else s = s.copy(player = s.player.copy(reputation = (s.player.reputation - 3).coerceAtLeast(0)))
        }
        s = s.copy(
            lead = s.lead.copy(delegations = s.lead.delegations.map { if (it.id == d.id) it.copy(status = if (success) DelegationStatus.DONE else DelegationStatus.FAILED, settled = true, report = report) else it }),
            reports = (s.reports + MissionReport(d.contractId, d.title, if (success) "Delegiert – Erfolg" else "Delegiert – Fehlschlag", s.clock.day, 0, 0, 0, report)).takeLast(40)
        )
        return Command.log(s, "Delegation „${d.title}“: ${if (success) "Erfolg" else "Fehlschlag"}")
    }
}

/** v0.8 Personalwesen: Posten, Annahme von Angeboten, Beurteilungen, Versetzungen, Beförderungsempfehlungen. */
object Personnel {
    /** Aktueller fester Posten einer Figur (Abteilungsleitung, Außenstelle, Delegation) oder null. */
    fun assignmentOf(data: GameData, state: GameState, id: String): String? {
        state.director?.heads?.entries?.firstOrNull { it.value == id }?.let { return "Leitung ${Director.deptName(it.key)}" }
        state.lead.sites.entries.firstOrNull { it.value.open && id in it.value.staff }?.let { return Sites.siteName(it.key) }
        state.lead.delegations.firstOrNull { !DelegationStatus.closed(it.status) && id in it.team }?.let { return "Delegation „${it.title.substringAfter(": ")}“" }
        return null
    }

    private val transferable = setOf("thom", "jaden", "ryan", "helena", "sonya", "owen")

    /** Nimmt die Figur einen Posten an? Ziele, Stimmung, Loyalität und Eigenständigkeit zählen. */
    fun willAccept(data: GameData, state: GameState, id: String, area: String, salt: Long): Pair<Boolean, String> {
        val until = state.lead.refusals[id]
        if (until != null && state.clock.day < until) return false to "hat kürzlich abgelehnt – erst ab ${Clock.dateLabel(until)} wieder fragen"
        val l = Life.life(data, state, id)
        val r = Relations.get(data, state, id)
        val fit = Life.competence(data, state, id, area)
        val goalMatch = if (l.focus == area) 12 else 0
        val custom = state.customNpcs.firstOrNull { it.id == id }
        val independence = if (custom != null && custom.careerWish.isNotBlank() && Life.focusForWish(custom.careerWish, custom.profession) != area) 20 else 0
        val score = r.loyalty / 2 + l.mood / 3 + fit * 5 + goalMatch - l.stress / 4 - independence + Rng(Rng.mix(state.seed, id.hashCode().toLong(), salt)).range(-8, 8)
        return (score >= 45) to (if (score >= 45) "nimmt an" else if (independence > 0) "möchte bei ${Life.focusLabel(l.focus)} bleiben" else "lehnt ab – die Aufgabe passt gerade nicht")
    }

    fun refuse(state: GameState, id: String) = state.copy(lead = state.lead.copy(refusals = state.lead.refusals + (id to state.clock.day + 14)))

    /** Leistungsbeurteilung: wirkt auf Karrierepunkte, Stimmung und spätere Berichte. */
    fun review(data: GameData, state: GameState, id: String): Result {
        if (!Command.isLeader(state)) return state.fail("Beurteilungen ab Rang 8.")
        if (id !in Command.staff(data, state)) return state.fail("Nicht in deinem Personal.")
        val last = state.lead.reviews[id]
        if (last != null && state.clock.day - last.day < 30) return state.fail("Letzte Beurteilung vor weniger als 30 Tagen.")
        val l = Life.life(data, state, id)
        val score = l.grades.takeLast(3).map { when (it) { "A" -> 90; "B" -> 72; "C" -> 55; "D" -> 38; else -> 22 } }.ifEmpty { listOf(55) }.average() +
            (l.missions - l.missionsFailed) * 2 + (l.mood - 50) / 5
        val grade = when { score >= 80 -> "A"; score >= 64 -> "B"; score >= 48 -> "C"; score >= 32 -> "D"; else -> "E" }
        val note = when (grade) { "A" -> "Herausragend – bereit für mehr Verantwortung"; "B" -> "Verlässlich und engagiert"; "C" -> "Solide, mit Luft nach oben"; "D" -> "Braucht Unterstützung und klare Ziele"; else -> "Ernsthafte Leistungsprobleme" }
        var s = state.copy(lead = state.lead.copy(reviews = state.lead.reviews + (id to PerformanceReview(state.clock.day, grade, note))))
        val (xp, mood) = when (grade) { "A" -> 40 to 8; "B" -> 25 to 4; "C" -> 10 to 0; "D" -> 5 to -6; else -> 0 to -10 }
        s = s.copy(living = s.living.copy(lives = s.living.lives + (id to l.copy(careerXp = l.careerXp + xp, mood = (l.mood + mood).pct()))))
        s = Relations.update(data, s, id) { it.copy(respect = it.respect + if (grade <= "B") 3 else -2, memories = it.memories + "Leistungsbeurteilung: $grade") }
        if (grade >= "D") s = Bonds.addConflict(data, s, id, "Die schlechte Beurteilung", 1, "review")
        s = Command.log(s, "Beurteilung ${Relations.displayName(data, s, id).substringBefore(" ")}: $grade")
        return Result(s, listOf("Beurteilung: $grade – $note", "Karrierepunkte +$xp · Stimmung ${Effs.signed(mood)}"))
    }

    /** Beförderung empfehlen (ab Rang 10): Prüfung wird sofort zugelassen, wenn die Leistung stimmt. */
    fun recommend(data: GameData, state: GameState, id: String): Result {
        if (state.player.rank < 10) return state.fail("Beförderungsempfehlungen ab Rang 10 (Sektionsleitung).")
        val l = Life.life(data, state, id)
        val need = Life.examThreshold(l.rank)
        if (l.rank >= Life.rankCap(data, state, id)) return state.fail("Höchster Rang dieser Laufbahn erreicht.")
        if (l.careerXp < need * 7 / 10) return state.fail("Leistung reicht noch nicht (${l.careerXp}/${need * 7 / 10} Karrierepunkte).")
        val nl = l.copy(careerXp = maxOf(l.careerXp, need), examCooldownUntil = state.clock.day)
        var s = state.copy(living = state.living.copy(lives = state.living.lives + (id to nl)))
        s = Relations.update(data, s, id) { it.copy(loyalty = it.loyalty + 5, memories = it.memories + "Du hast mich zur Beförderung empfohlen") }
        s = Command.log(s, "Beförderungsempfehlung für ${Relations.displayName(data, s, id).substringBefore(" ")}")
        return Result(s, listOf("Empfehlung ausgesprochen: ${Relations.displayName(data, s, id)} tritt in den nächsten Tagen zur Prüfung an."))
    }

    /** Versetzung (ab Rang 10): Figur verlässt die Division für eine Weile – mit Folgen. */
    fun transfer(data: GameData, state: GameState, id: String): Result {
        if (state.player.rank < 10) return state.fail("Versetzungen ab Rang 10.")
        val companion = state.customNpcs.firstOrNull { it.id == id }
        if (id !in transferable && companion == null) return state.fail("Diese Figur kann nicht versetzt werden.")
        if (companion != null) {
            val (ok, why) = willAccept(data, state, id, Life.life(data, state, id).focus, 777)
            if (!ok || Relations.get(data, state, id).trust < 60) return state.fail("${companion.firstName} entscheidet selbst über ihren Weg: $why.")
        }
        var s = Life.apply(data, state, LifeChange(id, status = LifeStatus.TRANSFERRED, days = 60, note = "Versetzung (Personalentscheid)", mood = -10))
        s = s.copy(team = s.team - id, lead = s.lead.copy(roles = s.lead.roles - id, cohesion = (s.lead.cohesion - if (id in state.team) 6 else 0).pct()))
        s = Relations.update(data, s, id) { it.copy(loyalty = it.loyalty - 10, memories = it.memories + "Die Versetzung") }
        s = Life.feed(data, s, LifeEvent(s.clock.day, id, "transferred", "${Relations.displayName(data, s, id)} wird auf deine Anordnung für 60 Tage versetzt."))
        s = Command.log(s, "Versetzung: ${Relations.displayName(data, s, id)}")
        return Result(s, listOf("${Relations.displayName(data, s, id)} wird für 60 Tage an eine Außenstelle versetzt.", "Loyalität −10"))
    }
}

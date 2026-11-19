package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v0.8 Sektionsleitung (Rang 10), stellvertretende Direktion (11) und Direktion (12)
 * auf Basis des bestehenden [DirectorState]. Organisationsbudget und persönliche
 * Credits sind strikt getrennt; die Wochenabrechnung läuft genau einmal pro Woche.
 */
object Directorate {
    const val REPORT_CAP = 16

    fun tier(state: GameState) = when { state.player.rank >= 12 -> 3; state.player.rank >= 11 -> 2; state.player.rank >= 10 -> 1; else -> 0 }
    fun tierLabel(t: Int) = when (t) { 3 -> "Direktion"; 2 -> "Stellvertretende Direktion"; 1 -> "Sektionsleitung"; else -> "Teamleitung" }

    val deptArea = mapOf("training" to "combat", "tech" to "tech", "medical" to "medic", "intel" to "intel", "security" to "combat")

    /** Legt das Direktorat ab Rang 10 an (auch für ältere Spielstände). */
    fun ensure(data: GameData, state: GameState): GameState {
        if (state.player.rank < 10 || state.director != null) return state
        val start = if (state.player.rank >= 12) 50000 else 15000
        return state.copy(director = DirectorState(budget = start + state.lead.teamBudget), lead = state.lead.copy(teamBudget = 0))
    }

    // --- Budget -------------------------------------------------------------------------

    fun budget(state: GameState): Int = state.director?.budget ?: state.lead.teamBudget

    fun spend(state: GameState, amount: Int): GameState {
        val d = state.director
        return if (d != null) state.copy(director = d.copy(budget = (d.budget - amount).coerceAtLeast(0)))
        else state.copy(lead = state.lead.copy(teamBudget = (state.lead.teamBudget - amount).coerceAtLeast(0)))
    }

    fun earn(state: GameState, amount: Int): GameState {
        val d = state.director
        return if (d != null) state.copy(director = d.copy(budget = (d.budget + amount).coerceAtMost(999_999)))
        else state.copy(lead = state.lead.copy(teamBudget = (state.lead.teamBudget + amount).coerceAtMost(999_999)))
    }

    // --- Abteilungen ----------------------------------------------------------------------

    fun maxLevel(state: GameState) = when (tier(state)) { 3 -> 5; 2 -> 4; 1 -> 3; else -> 1 }

    fun canManage(state: GameState, dept: String): Boolean = when (tier(state)) {
        3, 2 -> true
        1 -> state.lead.sectionDept == dept
        else -> false
    }

    /** Wirksame Stärke einer Abteilung: Ausbaustufe + passende, verfügbare Leitung. */
    fun power(data: GameData, state: GameState, dept: String): Int {
        val d = state.director ?: return 1
        val lvl = d.departments[dept] ?: 1
        val head = d.heads[dept] ?: return lvl
        val avail = state.living.lives[head]?.status.let { it == null || LifeStatus.inHq(it) && it != LifeStatus.INJURED }
        val fit = Life.competence(data, state, head, deptArea[dept] ?: "intel")
        return lvl + if (avail && fit >= 6) 1 else 0
    }

    fun chooseSection(data: GameData, state: GameState, dept: String): Result {
        if (tier(state) != 1) return state.fail("Nur in der Sektionsleitung.")
        if (dept !in deptArea) return state.fail("Unbekannte Abteilung")
        val prev = state.lead.sectionDept
        if (prev == dept) return Result(state)
        if (prev != null && state.lead.projects.any { !it.finished && projectById[it.id]?.dept == prev }) return state.fail("Erst laufende Projekte der bisherigen Sektion abschließen oder beenden.")
        val s = state.copy(lead = state.lead.copy(sectionDept = dept))
        return Result(Command.log(s, "Sektion übernommen: ${Director.deptName(dept)}"), listOf("Du leitest jetzt die Sektion ${Director.deptName(dept)}."))
    }

    fun upgradeCost(level: Int) = Director.upgradeCost(level)

    fun upgrade(data: GameData, state: GameState, dept: String): Result {
        val d = state.director ?: return state.fail("Ab Rang 10.")
        if (!canManage(state, dept)) return state.fail("Keine Befugnis für ${Director.deptName(dept)}.")
        val lvl = d.departments[dept] ?: 1
        if (lvl >= maxLevel(state)) return state.fail("Höchste Ausbaustufe für ${tierLabel(tier(state))} erreicht (${maxLevel(state)}).")
        val r = Director.upgrade(data, state, dept)
        return if (r.ok) r.copy(state = Command.log(r.state, "${Director.deptName(dept)} auf Stufe ${lvl + 1}")) else r
    }

    fun assignHead(data: GameData, state: GameState, dept: String, npcId: String?): Result {
        val d = state.director ?: return state.fail("Ab Rang 10.")
        if (!canManage(state, dept)) return state.fail("Keine Befugnis für ${Director.deptName(dept)}.")
        if (npcId == null) return Director.assignHead(data, state, dept, null)
        if (Ages.npc(data, state, npcId) < 18) return state.fail("Leitungsfunktionen nur für volljähriges Personal.")
        val st = Command.status(data, state, npcId)
        val current = Personnel.assignmentOf(data, state, npcId)
        if (current != null && d.heads[dept] != npcId) return state.fail("${Relations.displayName(data, state, npcId).substringBefore(" ")} ist bereits eingesetzt: $current")
        if (!st.available && st.code != "assigned") return state.fail("${Relations.displayName(data, state, npcId).substringBefore(" ")}: ${st.label}")
        if (Life.rank(data, state, npcId) < 3) return state.fail("Leitungen brauchen mindestens Rang 3.")
        val (ok, why) = Personnel.willAccept(data, state, npcId, deptArea[dept] ?: "intel", dept.hashCode().toLong() + state.clock.day / 14)
        if (!ok) return Result(Personnel.refuse(state, npcId), listOf("${Relations.displayName(data, state, npcId)} $why."), ok = false)
        var r = Director.assignHead(data, state, dept, npcId)
        r = r.copy(state = r.state.copy(team = r.state.team - npcId, lead = r.state.lead.copy(roles = r.state.lead.roles - npcId)))
        return r.copy(state = Command.log(r.state, "${Relations.displayName(data, state, npcId)} leitet ${Director.deptName(dept)}"))
    }

    // --- Leitlinien ---------------------------------------------------------------------

    val policyEffects = mapOf(
        "balanced" to "Moral +1/Woche · keine Sondereffekte",
        "transparent" to "Aufsicht +3/Woche · Lecks wiegen schwerer, Prüfungen seltener",
        "secrecy" to "Aufsicht −3/Woche · eigene Einsätze starten mit Alarm −1, Prüfungen häufiger",
        "people" to "Moral +3/Woche · Stress des Personals −3/Woche · Personalkosten +600 Cr"
    )

    fun allowedPolicies(state: GameState): List<String> = when (tier(state)) {
        3 -> Director.policies.map { it.first }
        2 -> listOf("balanced", "transparent", "people")
        else -> emptyList()
    }

    fun setPolicy(data: GameData, state: GameState, policy: String): Result {
        if (policy !in allowedPolicies(state)) return state.fail(if (tier(state) < 2) "Leitlinien ab Rang 11." else "„Strikte Geheimhaltung“ kann nur die Direktion anordnen.")
        val r = Director.setPolicy(state, policy)
        return r.copy(state = Command.log(r.state, "Leitlinie: ${Director.policies.first { it.first == policy }.second}"))
    }

    /** Haushaltsantrag (Rang 10–11): Entscheidung bei der nächsten Wochenabrechnung. */
    fun requestBudget(data: GameData, state: GameState, amount: Int): Result {
        val t = tier(state)
        if (t !in 1..2) return state.fail("Haushaltsanträge stellen Sektionsleitung und stellvertretende Direktion.")
        if (amount !in 2000..20000) return state.fail("Antrag zwischen 2.000 und 20.000 Cr.")
        if (state.lead.budgetRequest > 0) return state.fail("Ein Antrag ist bereits in Prüfung.")
        if (state.lead.budgetRequestDay >= 0 && state.clock.day - state.lead.budgetRequestDay < 28) return state.fail("Neuer Antrag frühestens 28 Tage nach dem letzten.")
        val s = state.copy(lead = state.lead.copy(budgetRequest = amount, budgetRequestDay = state.clock.day))
        return Result(Command.log(s, "Haushaltsantrag über $amount Cr gestellt"), listOf("Antrag über $amount Cr eingereicht. Entscheidung mit der nächsten Wochenabrechnung."))
    }

    // --- Projekte ------------------------------------------------------------------------

    data class Project(val id: String, val dept: String, val name: String, val description: String, val weeks: Int, val weeklyCost: Int, val minTier: Int)
    val projects = listOf(
        Project("sim_course", "training", "Simulationsparcours", "Teamtraining wirkt stärker (Kompetenzfortschritt +10).", 4, 1500, 1),
        Project("mentor_program", "training", "Mentorenprogramm", "Mentoring bringt doppelte Karrierepunkte.", 3, 1000, 1),
        Project("secure_net", "tech", "Abgesichertes Netz", "Eigene Einsätze starten mit Alarm −1.", 5, 2000, 2),
        Project("field_kit", "tech", "Feldausrüstung 2.0", "+4 auf Intellekt-Proben im Einsatz.", 3, 1200, 1),
        Project("trauma_unit", "medical", "Trauma-Einheit", "Einsatzverletzungen heilen 2 Tage schneller.", 4, 1800, 1),
        Project("stress_program", "medical", "Stressprävention", "Wöchentlich −5 Stress für Team und Leitungen.", 3, 900, 1),
        Project("source_network", "intel", "Quellennetzwerk", "Jeder eigene Einsatz startet mit +1 Intel.", 5, 1600, 2),
        Project("pattern_lab", "intel", "Musterlabor", "Erfüllte Aufträge liefern häufiger zusätzliche Hinweise.", 4, 1400, 1),
        Project("counter_intel", "security", "Spionageabwehr", "Black Meridian verliert wöchentlich 2 Einfluss; Lecks seltener.", 4, 1500, 2),
        Project("perimeter", "security", "Neuer Perimeter", "Aufsicht +5 bei Abschluss; HQ-Sicherheit verbessert.", 3, 1100, 1)
    )
    val projectById = projects.associateBy { it.id }

    fun maxProjects(state: GameState) = when (tier(state)) { 3 -> 4; 2 -> 3; 1 -> 2; else -> 0 }
    fun activeProjects(state: GameState) = state.lead.projects.filter { !it.finished }

    fun startProject(data: GameData, state: GameState, id: String): Result {
        val p = projectById[id] ?: return state.fail("Unbekanntes Projekt")
        if (state.director == null) return state.fail("Projekte ab Rang 10.")
        if (tier(state) < p.minTier) return state.fail("Erfordert ${tierLabel(p.minTier)}.")
        if (!canManage(state, p.dept)) return state.fail("Keine Befugnis für ${Director.deptName(p.dept)}.")
        if ("proj_$id" in state.flags) return state.fail("Bereits abgeschlossen.")
        if (state.lead.projects.any { it.id == id && !it.finished }) return state.fail("Läuft bereits.")
        if (activeProjects(state).size >= maxProjects(state)) return state.fail("Höchstens ${maxProjects(state)} Projekte gleichzeitig.")
        if (budget(state) < p.weeklyCost) return state.fail("Budget reicht nicht für die erste Woche (${p.weeklyCost} Cr).")
        val s = state.copy(lead = state.lead.copy(projects = state.lead.projects.filter { it.id != id } + DeptProject(id, state.clock.day)))
        return Result(Command.log(s, "Projekt gestartet: ${p.name}"), listOf("Projekt „${p.name}“ gestartet: ${p.weeks} Wochen à ${p.weeklyCost} Cr."))
    }

    fun cancelProject(data: GameData, state: GameState, id: String): Result {
        val p = projectById[id] ?: return state.fail("Unbekanntes Projekt")
        if (state.lead.projects.none { it.id == id && !it.finished }) return state.fail("Kein laufendes Projekt.")
        val s = state.copy(lead = state.lead.copy(projects = state.lead.projects.filter { it.id != id }),
            director = state.director?.let { it.copy(morale = (it.morale - 2).pct()) })
        return Result(Command.log(s, "Projekt beendet: ${p.name}"), listOf("Projekt „${p.name}“ beendet. Bisherige Ausgaben sind verloren. Moral −2"))
    }

    // --- Wochenabrechnung ---------------------------------------------------------------------

    data class Line(val label: String, val amount: Int)

    /** Vorschau bzw. Grundlage der Wochenabrechnung (nachvollziehbare Posten). */
    fun weeklyLines(data: GameData, state: GameState): Pair<List<Line>, List<Line>> {
        val t = tier(state)
        val d = state.director
        val income = mutableListOf<Line>()
        val costs = mutableListOf<Line>()
        when (t) {
            0 -> income += Line("Teametat", 800 + (state.player.rank - 8).coerceAtLeast(0) * 400)
            1 -> income += Line("Sektionsetat", 4000)
            2 -> income += Line("Etat stellv. Direktion", 9000)
            else -> income += Line("Divisionsetat", 14000 + ((d?.oversight ?: 50) - 50) * 80)
        }
        if (d != null) {
            val levels = when (t) {
                1 -> state.lead.sectionDept?.let { d.departments[it] ?: 1 } ?: 0
                else -> d.departments.values.sum()
            }
            val rate = when (t) { 3 -> 1500; 2 -> 900; else -> 1200 }
            if (levels > 0) costs += Line("Abteilungsbetrieb", levels * rate)
            if (d.heads.isNotEmpty()) costs += Line("Leitungsgehälter (${d.heads.size})", d.heads.size * 400)
            if (d.policy == "people") costs += Line("Fürsorgeprogramm", 600)
            if (Ops.faction(state, "syndicate").arc == 1 && power(data, state, "security") < 3) costs += Line("Sicherheitslage (Meridian-Offensive)", 1500)
            if (d.oversight < 20) costs += Line("Prüfauflagen der Aufsicht", 2000)
        }
        val siteStaff = state.lead.sites.values.filter { it.open }.sumOf { it.staff.size }
        if (siteStaff > 0) costs += Line("Personal Außenstellen ($siteStaff)", siteStaff * 250)
        Sites.modules.forEach { m -> val lvl = Sites.moduleLevel(state, m.id); if (lvl > 0) costs += Line("Unterhalt ${m.name}", lvl * m.upkeep) }
        Sites.outposts.forEach { o -> state.lead.sites[o.id]?.takeIf { it.open }?.let { costs += Line("Unterhalt ${o.name}", it.level * o.upkeep) } }
        activeProjects(state).forEach { p -> projectById[p.id]?.let { costs += Line("Projekt ${it.name}", it.weeklyCost) } }
        return income to costs
    }

    /** Genau einmal pro Woche (auch im Zeitraffer): Einnahmen, Kosten, Projekte, Moral, Aufsicht, Dilemmata. */
    fun weekly(data: GameData, state: GameState): Result {
        if (state.player.rank < Command.LEAD_RANK) return Result(state)
        val week = Clock.week(state.clock.day)
        if (state.lead.lastFinanceWeek >= week) return Result(state)
        var s = ensure(data, state).let { it.copy(lead = it.lead.copy(lastFinanceWeek = week)) }
        val msgs = mutableListOf<String>()
        val lines = mutableListOf<String>()
        val (income, costs) = weeklyLines(data, s)
        val inSum = income.sumOf { it.amount }
        val projectCost = costs.filter { it.label.startsWith("Projekt ") }.sumOf { it.amount }
        val fixed = costs.sumOf { it.amount } - projectCost
        val available = budget(s) + inSum
        val shortage = available < fixed + projectCost
        val paidProjects = if (available - fixed >= projectCost) projectCost else 0
        val paid = (fixed.coerceAtMost(available)) + paidProjects
        s = earn(s, inSum)
        s = spend(s, paid)
        income.forEach { lines += "+${it.amount} Cr ${it.label}" }
        costs.forEach { if (!it.label.startsWith("Projekt ") || paidProjects > 0) lines += "−${it.amount} Cr ${it.label}" }
        if (shortage) lines += "Engpass: ${if (paidProjects == 0 && projectCost > 0) "Projekte pausieren, " else ""}offene Kosten konnten nicht vollständig gedeckt werden."
        // Projektfortschritt nur bei bezahlter Woche
        if (paidProjects > 0 || projectCost == 0) {
            val done = mutableListOf<String>()
            val updated = s.lead.projects.map { p ->
                if (p.finished) p else {
                    val def = projectById[p.id]
                    val w = p.weeksDone + 1
                    if (def != null && w >= def.weeks) { done += p.id; p.copy(weeksDone = w, finished = true) } else p.copy(weeksDone = w)
                }
            }
            s = s.copy(lead = s.lead.copy(projects = updated))
            done.forEach { id ->
                s = s.copy(flags = s.flags + "proj_$id")
                if (id == "perimeter") s = s.copy(director = s.director?.let { it.copy(oversight = (it.oversight + 5).pct()) })
                lines += "Projekt abgeschlossen: ${projectById[id]?.name}"
                msgs += "Projekt abgeschlossen: ${projectById[id]?.name}"
            }
        }
        // Moral und Aufsicht
        s.director?.let { d ->
            val (m, o) = when (d.policy) { "transparent" -> 0 to 3; "secrecy" -> -1 to -3; "people" -> 3 to 0; else -> 1 to 0 }
            val heads = d.heads.size / 2
            val nd = d.copy(
                morale = (d.morale + m + heads - (if (shortage) 4 else 0)).pct(),
                oversight = (d.oversight + o - (if (shortage) 1 else 0)).pct(),
                lastWeekReport = "Einnahmen $inSum Cr · Kosten $paid Cr · Budget ${s.director?.budget}"
            )
            s = s.copy(director = nd)
            lines += "Moral ${d.morale} → ${nd.morale} · Aufsicht ${d.oversight} → ${nd.oversight}"
        }
        // Haushaltsantrag
        if (s.lead.budgetRequest > 0) {
            val amount = s.lead.budgetRequest
            val rng = Rng(Rng.mix(s.seed, week.toLong(), 4711))
            val percy = if (Relations.isKnownNpc(data, s, "percy")) Relations.get(data, s, "percy").trust else 40
            val chance = (35 + (s.director?.oversight ?: 50) / 2 + percy / 4 - amount / 1000).coerceIn(10, 90)
            val approved = rng.percent() <= chance
            s = s.copy(lead = s.lead.copy(budgetRequest = 0))
            if (approved) {
                s = earn(s, amount)
                s = s.copy(director = s.director?.let { it.copy(oversight = (it.oversight - 2).pct()) })
                lines += "Haushaltsantrag bewilligt: +$amount Cr (Aufsicht −2)"; msgs += "Haushaltsantrag bewilligt: +$amount Cr"
            } else {
                s = s.copy(director = s.director?.let { it.copy(morale = (it.morale - 2).pct()) })
                lines += "Haushaltsantrag abgelehnt (Moral −2)"; msgs += "Haushaltsantrag abgelehnt"
            }
        }
        // Wirkungen der Standorte, Projekte und Leitlinie auf Personal und Weltlage
        s = Sites.weekly(data, s, lines)
        if ("proj_stress_program" in s.flags || s.director?.policy == "people") {
            val amount = (if ("proj_stress_program" in s.flags) 5 else 0) + (if (s.director?.policy == "people") 3 else 0)
            (s.team + (s.director?.heads?.values ?: emptyList())).distinct().forEach { id ->
                val l = Life.life(data, s, id)
                s = s.copy(living = s.living.copy(lives = s.living.lives + (id to l.copy(stress = (l.stress - amount).pct()))))
            }
        }
        if ("proj_counter_intel" in s.flags) s = Ops.shiftInfluence(s, "syndicate", -2)
        s = Command.weekly(data, s)
        // Dilemma auslösen
        s = maybeDilemma(data, s, week)
        if (s.lead.pendingDilemma != null && state.lead.pendingDilemma == null) msgs += "Führungsentscheidung: ${dilemmaById[s.lead.pendingDilemma]?.title}"
        val report = PeriodReport(s.clock.day, "week", "Wochenbericht · Woche ${week + 1}", lines, inSum, paid, budget(s))
        s = s.copy(lead = s.lead.copy(reports = (s.lead.reports + report).takeLast(REPORT_CAP)))
        // Monatsbericht alle vier Wochen
        if (week % 4 == 3 && s.lead.lastMonthReport < week) s = monthly(data, s, week)
        msgs += "${if (s.director != null) "Organisationsbudget" else "Teametat"}: +$inSum / −$paid Cr"
        return Result(s, msgs)
    }

    private fun monthly(data: GameData, state: GameState, week: Int): GameState {
        val s = state
        val since = s.clock.day - 28
        val weeks = s.lead.reports.filter { it.kind == "week" && it.day > since }
        val ops = s.ops.history.filter { it.day > since }
        val dels = s.lead.delegations.filter { DelegationStatus.closed(it.status) && it.returnDay > since }
        val injuries = s.living.feed.count { it.type == "injured" && it.day > since }
        val leisure = s.life.log.count { it.isNotBlank() }.coerceAtMost(99)
        val lines = buildList {
            add("Einnahmen ${weeks.sumOf { it.income }} Cr · Ausgaben ${weeks.sumOf { it.expenses }} Cr · Stand ${budget(s)} Cr")
            add("Eigene Aufträge: ${ops.count { it.status == ContractStatus.DONE }} erfüllt, ${ops.count { it.status == ContractStatus.FAILED }} gescheitert, ${ops.count { it.status == ContractStatus.EXPIRED }} abgelaufen")
            add("Delegationen: ${dels.count { it.status == DelegationStatus.DONE }} erfolgreich, ${dels.count { it.status == DelegationStatus.FAILED }} gescheitert, ${dels.count { it.status == DelegationStatus.ABORTED }} abgebrochen")
            add("Personal: Teamkohäsion ${s.lead.cohesion} · Verletzungen $injuries · Beurteilungen ${s.lead.reviews.values.count { it.day > since }}")
            s.director?.let { add("Moral ${it.morale} · Aufsicht ${it.oversight} · Leitlinie ${Director.policies.firstOrNull { p -> p.first == it.policy }?.second}") }
            add("Black Meridian: Einfluss ${Ops.faction(s, "syndicate").influence} · laufende Projekte ${activeProjects(s).size} · Außenstellen ${s.lead.sites.values.count { it.open }}")
            add("Privatleben: Wohnung ${Home.housingName(s.life.housing)} · Freizeiteinträge $leisure")
            s.lead.dilemmaHistory.filterValues { it > since }.keys.forEach { add("Entscheidung getroffen: ${dilemmaById[it.substringBefore("#")]?.title ?: it}") }
        }
        val report = PeriodReport(s.clock.day, "month", "Monatsbericht · bis ${Clock.dateLabel(s.clock.day)}", lines, weeks.sumOf { it.income }, weeks.sumOf { it.expenses }, budget(s))
        return s.copy(lead = s.lead.copy(reports = (s.lead.reports + report).takeLast(REPORT_CAP), lastMonthReport = week))
    }

    // --- Führungsdilemmata --------------------------------------------------------------------

    data class Choice(
        val id: String, val text: String, val result: String,
        val budget: Int = 0, val morale: Int = 0, val oversight: Int = 0,
        val influence: Map<String, Int> = emptyMap(), val attitude: Map<String, Int> = emptyMap(),
        val trust: Map<String, Int> = emptyMap(), val fallout: Fallout? = null, val check: CheckDef? = null,
        val failResult: String = "", val failBudget: Int = 0, val failOversight: Int = 0, val healInjured: Int = 0, val cancelProject: Boolean = false
    )
    data class Dilemma(val id: String, val title: String, val text: String, val minTier: Int, val choices: List<Choice>)

    val dilemmas = listOf(
        Dilemma("budget_cut", "Etatkürzung", "Die Aufsicht kündigt eine Kürzung des Etats an. Begründung: „Mangelnde Transparenz bei Ausgaben.“", 1, listOf(
            Choice("accept", "Kürzung akzeptieren", "Der Etat sinkt, die Aufsicht wertet die Kooperation positiv.", budget = -4000, oversight = 5),
            Choice("fight", "Mit Zahlen dagegenhalten", "Du legst Leistungsnachweise vor. Die Kürzung wird zurückgenommen.", oversight = -3,
                check = CheckDef(Attr.CHARISMA, "negotiation", 50), failResult = "Die Aufsicht bleibt hart – und kürzt stärker.", failBudget = -7000, failOversight = -6),
            Choice("project", "Ein Projekt opfern", "Das teuerste Projekt wird eingestellt. Das Personal ist enttäuscht, die Aufsicht zufrieden.", oversight = 3, morale = -3, cancelProject = true)
        )),
        Dilemma("leak", "Sicherheitsleck", "Interne Einsatzpläne sind bei Black Meridian aufgetaucht. Irgendwo in der Division gibt es ein Leck.", 1, listOf(
            Choice("investigate", "Interne Untersuchung", "Die Untersuchung kostet Geld und Vertrauen – aber sie läuft.", budget = -2000, morale = -5, influence = mapOf("syndicate" to -4),
                fallout = Fallout(7, "leak_found", "Die interne Untersuchung findet die Quelle des Lecks.", morale = 4, influence = -4, faction = "syndicate")),
            Choice("ignore", "Nicht eskalieren", "Du hältst die Sache klein. Fürs Erste.",
                fallout = Fallout(10, "leak_grows", "Das Leck war größer als gedacht: weitere Pläne sind aufgetaucht.", oversight = -6, influence = 8, faction = "syndicate")),
            Choice("blame", "Die Technik verantwortlich machen", "Die Technikabteilung muss nachbessern. Birkhoff ist sauer.", oversight = 2, trust = mapOf("birkhoff" to -10), morale = -2)
        )),
        Dilemma("staff_dispute", "Personalstreit", "Zwei deiner Leute streiten offen über Zuständigkeiten. Das Team wartet auf deine Entscheidung.", 0, listOf(
            Choice("mediate", "Gemeinsames Gespräch", "Du moderierst. Beide fühlen sich gehört.", morale = 3),
            Choice("decide", "Klare Entscheidung treffen", "Du ziehst eine Linie. Der Streit ist beendet – nicht jeder ist glücklich.", morale = -1, oversight = 1),
            Choice("separate", "Beide trennen", "Die beiden arbeiten künftig in verschiedenen Schichten. Ruhe – aber weniger Zusammenhalt.", morale = -2)
        )),
        Dilemma("failed_mission", "Fehlgeschlagene Mission", "Ein Auftrag ist gescheitert. Die Aufsicht will wissen, wer die Verantwortung trägt.", 1, listOf(
            Choice("own", "Verantwortung übernehmen", "Du stellst dich vor dein Team. Die Aufsicht notiert es, das Team vergisst es nicht.", morale = 5, oversight = -4),
            Choice("team", "Das Team verantwortlich machen", "Die Aufsicht ist zufrieden, das Team nicht.", morale = -8, oversight = 2),
            Choice("cover", "Den Bericht glätten", "Der Bericht liest sich besser, als es war.",
                fallout = Fallout(14, "cover_exposed", "Der geglättete Einsatzbericht fällt bei einer Stichprobe auf.", oversight = -10, morale = -3))
        )),
        Dilemma("audit", "Aufsichtskontrolle", "Eine Prüfkommission der Regierungsaufsicht kündigt sich für nächste Woche an.", 1, listOf(
            Choice("open", "Bücher öffnen", "Die Prüfer finden Ordnung vor. Etwas Spielraum geht verloren.", oversight = 6, morale = -2),
            Choice("dossier", "Dossier vorbereiten lassen", "Ein sauberes Dossier kostet Geld – und beeindruckt.", budget = -1500, oversight = 3),
            Choice("stall", "Auf Zeit spielen", "Die Prüfer kommen nicht weiter. Sie werden wiederkommen.", oversight = -6, attitude = mapOf("government" to -5))
        )),
        Dilemma("medical_crisis", "Medizinische Krise", "Eine Infektionswelle und mehrere Einsatzverletzungen überlasten die Krankenstation.", 1, listOf(
            Choice("invest", "Zusätzliche Mittel bereitstellen", "Extra-Personal und Material: Verletzte erholen sich schneller.", budget = -3000, morale = 4, healInjured = 3),
            Choice("external", "Externe Klinik einbinden", "Eine Klinik der Aufsicht hilft – und erfährt mehr, als dir lieb ist.", budget = -1000, attitude = mapOf("government" to 3), oversight = 2, healInjured = 2),
            Choice("wait", "Mit Bordmitteln durchhalten", "Das Personal schuftet. Die Moral leidet.", morale = -6,
                fallout = Fallout(5, "med_burnout", "Das Sanitätspersonal ist erschöpft – zwei Kräfte fallen aus.", morale = -3))
        )),
        Dilemma("young_volunteer", "Ein junger Freiwilliger", "Ein 16-jähriger Rekrut bittet darum, bei einem Außeneinsatz mitzukommen. Er will sich beweisen.", 0, listOf(
            Choice("training", "Ablehnen und Ausbildungsplan anbieten", "Er ist enttäuscht, aber er bekommt einen klaren Weg. Das Personal respektiert die Linie.", morale = 2),
            Choice("amanda", "Ablehnen und ein Gespräch mit Amanda vermitteln", "Amanda nimmt sich Zeit für ihn. Er fühlt sich ernst genommen.", trust = mapOf("amanda" to 3), morale = 1)
        )),
        Dilemma("meridian_offer", "Ein Angebot von Black Meridian", "Über einen Mittelsmann bietet Black Meridian Informationen über PHOENIX an – gegen Nachsicht im Hafen.", 2, listOf(
            Choice("reject", "Ablehnen", "Keine Geschäfte mit Meridian. Die Aufsicht hört davon und ist zufrieden.", oversight = 2, influence = mapOf("syndicate" to 3)),
            Choice("report", "Der Aufsicht melden", "Die Aufsicht nutzt den Kontakt selbst. Ihr Vertrauen in dich wächst.", attitude = mapOf("government" to 5), oversight = 3),
            Choice("accept", "Informationen annehmen", "Die Informationen sind echt. Der Preis wird später fällig.", influence = mapOf("phoenix" to -5),
                fallout = Fallout(14, "meridian_debt", "Black Meridian fordert die versprochene Nachsicht ein – der Hafen wird unruhiger.", influence = 10, faction = "syndicate", oversight = -4))
        ))
    )
    val dilemmaById = dilemmas.associateBy { it.id }

    private fun eligible(data: GameData, state: GameState, d: Dilemma): Boolean {
        if (tier(state) < d.minTier) return false
        val last = state.lead.dilemmaHistory.filterKeys { it.substringBefore("#") == d.id }.values.maxOrNull()
        if (last != null && state.clock.day - last < 60) return false
        return when (d.id) {
            "staff_dispute" -> state.team.size + (state.director?.heads?.size ?: 0) >= 2
            "failed_mission" -> state.ops.history.any { it.status == ContractStatus.FAILED && state.clock.day - it.day <= 14 } ||
                state.lead.delegations.any { it.status == DelegationStatus.FAILED && state.clock.day - it.returnDay <= 14 }
            "audit" -> (state.director?.policy == "secrecy") || (state.director?.oversight ?: 50) < 45 || state.clock.day % 3 == 0
            "leak" -> "proj_counter_intel" !in state.flags || state.clock.day % 2 == 0
            "medical_crisis" -> state.living.lives.values.count { it.status == LifeStatus.INJURED } >= 1
            "young_volunteer" -> state.customNpcs.any { Ages.npc(data, state, it.id) < 18 } || data.agents.any { it.lifeProfile == "recruit" && Ages.npc(data, state, it.id) < 18 }
            "meridian_offer" -> "phoenix_truth" in state.flags
            else -> true
        }
    }

    private fun maybeDilemma(data: GameData, state: GameState, week: Int): GameState {
        if (state.lead.pendingDilemma != null || !Command.isLeader(state)) return state
        val rng = Rng(Rng.mix(state.seed, week.toLong(), 6161))
        val chance = if (tier(state) >= 1) 40 else 20
        if (rng.percent() > chance) return state
        val pool = dilemmas.filter { eligible(data, state, it) }
        if (pool.isEmpty()) return state
        val d = rng.pick(pool)
        return state.copy(lead = state.lead.copy(pendingDilemma = d.id, dilemmaDay = state.clock.day))
    }

    fun pendingDilemma(state: GameState) = state.lead.pendingDilemma?.let { dilemmaById[it] }

    /** Beteiligte eines Personalstreits (deterministisch). */
    fun disputeParties(data: GameData, state: GameState): Pair<String, String>? {
        val pool = (state.team + (state.director?.heads?.values ?: emptyList())).distinct()
        if (pool.size < 2) return null
        val pairs = pool.flatMapIndexed { i, a -> pool.drop(i + 1).map { b -> a to b } }
        return pairs.minByOrNull { (a, b) -> Life.bond(state, a, b) }
    }

    fun resolveDilemma(data: GameData, state: GameState, choiceId: String): Result {
        val d = pendingDilemma(state) ?: return state.fail("Keine Entscheidung offen.")
        val c = d.choices.firstOrNull { it.id == choiceId } ?: return state.fail("Option fehlt")
        var s = state
        var success = true
        var roll = ""
        if (c.check != null) {
            val (s1, r) = Checks.roll(data, s, c.check, salt = d.id.hashCode().toLong())
            s = s1; success = r.success; roll = " (${r.roll}/${r.chance})"
        }
        val budgetDelta = if (success) c.budget else c.failBudget
        val oversight = if (success) c.oversight else c.failOversight
        if (budgetDelta < 0) s = spend(s, -budgetDelta) else if (budgetDelta > 0) s = earn(s, budgetDelta)
        s = s.copy(director = s.director?.let { it.copy(morale = (it.morale + c.morale).pct(), oversight = (it.oversight + oversight).pct()) })
        c.influence.forEach { (f, v) -> s = Ops.shiftInfluence(s, f, v) }
        c.attitude.forEach { (f, v) -> s = Ops.shiftAttitude(s, f, v) }
        c.trust.forEach { (npc, v) -> if (Relations.isKnownNpc(data, s, npc)) s = Relations.update(data, s, npc) { it.copy(trust = it.trust + v) } }
        if (c.healInjured > 0) s = s.copy(living = s.living.copy(lives = s.living.lives.mapValues { (_, l) ->
            if (l.status == LifeStatus.INJURED) l.copy(statusUntil = (l.statusUntil - c.healInjured).coerceAtLeast(s.clock.day)) else l }))
        if (c.cancelProject) {
            val most = activeProjects(s).maxByOrNull { projectById[it.id]?.weeklyCost ?: 0 }
            if (most != null) s = s.copy(lead = s.lead.copy(projects = s.lead.projects.filter { it.id != most.id }))
        }
        if (d.id == "staff_dispute") disputeParties(data, s)?.let { (a, b) ->
            val delta = when (c.id) { "mediate" -> 15; "decide" -> 0; else -> 5 }
            s = s.copy(living = s.living.copy(bonds = s.living.bonds + (LivingState.key(a, b) to (Life.bond(s, a, b) + delta).coerceIn(-100, 100))))
            if (c.id == "decide") s = Relations.update(data, s, b) { it.copy(trust = it.trust - 5, memories = it.memories + "Du hast im Streit gegen mich entschieden") }
            if (c.id == "separate") s = s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion - 3).pct()))
        }
        if (d.id == "young_volunteer") {
            val youngest = (s.customNpcs.map { it.id } + data.agents.filter { it.lifeProfile == "recruit" }.map { it.id }).filter { Ages.npc(data, s, it) < 18 }.minByOrNull { Ages.npc(data, s, it) }
            youngest?.let { id -> val l = Life.life(data, s, id); s = s.copy(living = s.living.copy(lives = s.living.lives + (id to l.copy(careerXp = l.careerXp + 20, mood = (l.mood + 3).pct())))) }
        }
        c.fallout?.let { f -> s = s.copy(lead = s.lead.copy(fallout = (s.lead.fallout + f.copy(day = s.clock.day + f.day)).takeLast(20))) }
        s = s.copy(lead = s.lead.copy(pendingDilemma = null, dilemmaHistory = (s.lead.dilemmaHistory + ("${d.id}#${s.clock.day}" to s.clock.day)).entries.toList().takeLast(30).associate { it.key to it.value }))
        val text = (if (success) c.result else c.failResult) + roll
        s = Command.log(s, "${d.title}: ${c.text}")
        s = Ops.worldLog(s, "Führungsentscheidung „${d.title}“: ${c.text}")
        val effects = listOfNotNull(
            budgetDelta.takeIf { it != 0 }?.let { "Budget ${Effs.signed(it)} Cr" },
            c.morale.takeIf { it != 0 }?.let { "Moral ${Effs.signed(it)}" },
            oversight.takeIf { it != 0 }?.let { "Aufsicht ${Effs.signed(it)}" },
            c.fallout?.let { "Folgen in ${it.day} Tagen möglich" }
        ).joinToString(" · ")
        return Result(s, listOf(text) + listOfNotNull(effects.ifBlank { null }))
    }

    /** Täglich: fällige Spätfolgen anwenden (genau einmal). */
    fun dailyFallout(data: GameData, state: GameState): Pair<GameState, List<String>> {
        val due = state.lead.fallout.filter { it.day <= state.clock.day }
        if (due.isEmpty()) return state to emptyList()
        var s = state.copy(lead = state.lead.copy(fallout = state.lead.fallout - due.toSet()))
        val msgs = mutableListOf<String>()
        due.forEach { f ->
            // Geglättete Berichte fliegen nur ohne starke Sicherheit auf
            if (f.id == "cover_exposed" && power(data, s, "security") >= 3) { msgs += "Der geglättete Bericht hält der Stichprobe stand."; return@forEach }
            if (f.budget != 0) s = if (f.budget < 0) spend(s, -f.budget) else earn(s, f.budget)
            s = s.copy(director = s.director?.let { it.copy(morale = (it.morale + f.morale).pct(), oversight = (it.oversight + f.oversight).pct()) })
            if (f.faction != null && f.influence != 0) s = Ops.shiftInfluence(s, f.faction, f.influence)
            if (f.npcId != null && Relations.isKnownNpc(data, s, f.npcId)) s = Relations.update(data, s, f.npcId) { it.copy(trust = it.trust + f.trust) }
            if (f.id == "med_burnout") s = s.copy(lead = s.lead.copy(cohesion = (s.lead.cohesion - 2).pct()))
            s = Command.log(s, f.text)
            msgs += f.text
        }
        return s to msgs
    }
}

package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * Karriere 2.0: Leistungsbeurteilungen durch Michael, Mentoring, mehrphasige
 * Beförderungsprüfungen mit Auflagen, persönliche Trainingsziele,
 * Auszeichnungen und Karrierejournal.
 */
object Career {
    const val EVAL_INTERVAL = 30
    const val EXAM_COOLDOWN = 14
    const val PASS_SCORE = 60

    // --- Journal ------------------------------------------------------------

    fun journal(state: GameState, type: String, title: String, text: String): GameState =
        state.copy(journal = (state.journal + JournalEntry(state.clock.day, type, title, text)).takeLast(250))

    // --- Leistungsbeurteilung -----------------------------------------------

    fun latestEvaluation(state: GameState, withinDays: Int = 9999): Evaluation? =
        state.career.evaluations.lastOrNull()?.takeIf { state.clock.day - it.day <= withinDays }

    fun canRequestEvaluation(state: GameState): Boolean {
        val last = state.career.evaluations.lastOrNull() ?: return true
        return state.clock.day - last.day >= EVAL_INTERVAL
    }

    data class EvalBreakdown(val training: Int, val missions: Int, val development: Int, val prep: Int, val stress: Int) {
        val total: Int get() = (training + missions + development + prep + stress).coerceIn(0, 100)
    }

    fun attrSum(state: GameState) = state.player.attributes.values.sum()

    /** Bewertung der letzten 30 Tage aus tatsächlich erbrachten Leistungen. */
    fun breakdown(state: GameState): EvalBreakdown {
        val day = state.clock.day
        val sessions = state.career.trainingDays.count { it > day - EVAL_INTERVAL }
        val training = (sessions * 2).coerceAtMost(36)
        val recent = state.reports.filter { it.day > day - EVAL_INTERVAL }
        val missions = (recent.count { it.result.contains("Erfolg") } * 12 - recent.count { it.result.contains("Fehl") } * 6).coerceIn(-12, 30)
        val base = if (state.career.attrSumAtLastEval == 0) attrSum(state) else state.career.attrSumAtLastEval
        val dev = ((attrSum(state) - base).coerceAtLeast(0) * 3).coerceAtMost(20)
        val prep = state.career.examPrep / 8
        val stress = if (state.player.stress > 75) -10 else if (state.player.stress < 40) 4 else 0
        return EvalBreakdown(training, missions, dev, prep, stress)
    }

    fun grade(score: Int) = when { score >= 80 -> "A"; score >= 60 -> "B"; score >= 40 -> "C"; score >= 20 -> "D"; else -> "E" }

    private fun comment(grade: String, b: EvalBreakdown) = when (grade) {
        "A" -> "„Herausragend. Du trainierst, als hinge dein Leben davon ab – irgendwann wird es das.“"
        "B" -> "„Solide Arbeit. " + (if (b.missions <= 0) "Zeig mir das auch im Einsatz." else "Bleib dran.") + "“"
        "C" -> "„Ausreichend. " + (if (b.training < 16) "Ich sehe dich zu selten auf der Matte." else "Mehr Biss.") + "“"
        "D" -> "„Das reicht nicht. Ich erwarte einen Trainingsplan auf meinem Tisch.“"
        else -> "„Wenn das so weitergeht, schalte ich dich selbst ab. Reiß dich zusammen.“"
    }

    fun evaluate(data: GameData, state: GameState, auto: Boolean = false): Result {
        if (!auto && !canRequestEvaluation(state)) return state.fail("Michael bewertet höchstens alle $EVAL_INTERVAL Tage.")
        if ("met_michael" !in state.flags) return state.fail("Du kennst Michael noch nicht.")
        val b = breakdown(state)
        val g = grade(b.total)
        val ev = Evaluation(state.clock.day, g, b.total, comment(g, b), auto)
        var s = state.copy(career = state.career.copy(evaluations = (state.career.evaluations + ev).takeLast(24), attrSumAtLastEval = attrSum(state)))
        val trust = when (g) { "A" -> 3; "B" -> 1; "D" -> -1; "E" -> -3; else -> 0 }
        s = Relations.update(data, s, "michael") { it.copy(trust = it.trust + trust, respect = it.respect + if (g <= "B") 1 else 0) }
        val prev = state.career.evaluations.lastOrNull()?.grade
        if (!auto || prev != g) s = journal(s, "evaluation", "Leistungsbeurteilung: Note $g", "Michael: ${ev.comment} (Training ${b.training}, Einsätze ${b.missions}, Entwicklung ${b.development}, Vorbereitung ${b.prep}, Verfassung ${b.stress})")
        if (g == "A") s = award(s, "grade_a", "Bestnote", "Erste Leistungsbeurteilung mit Note A.")
        return Result(s, listOf("Leistungsbeurteilung: Note $g (${b.total}/100)", ev.comment) + if (trust != 0) listOf("Michael: Vertrauen ${Effs.signed(trust)}") else emptyList())
    }

    // --- Mentoring mit Michael ------------------------------------------------

    fun mentorAvailable(data: GameData, state: GameState): String? {
        if ("met_michael" !in state.flags) return "Du kennst Michael noch nicht."
        if (World.npcLocation(data, state, "michael") != state.location) return "Michael ist nicht hier."
        if (Relations.get(data, state, "michael").trust < 45) return "Michael braucht mehr Vertrauen (≥ 45)."
        if (state.career.lastMentorDay == state.clock.day) return "Heute hattet ihr schon eine Einheit."
        return null
    }

    /** Einzeltraining: Michael arbeitet gezielt an deiner Schwäche und an der Prüfungsreife. */
    fun mentorTraining(data: GameData, state: GameState): Result {
        mentorAvailable(data, state)?.let { return state.fail(it) }
        if (state.player.energy < 25) return state.fail("Für Michaels Einzeltraining brauchst du 25 Energie.")
        val p = state.player
        val attr = p.weakness.takeIf { it in Attr.all } ?: Attr.all.minBy { p.attributes[it] ?: 30 }
        val rng = Rng(Rng.mix(state.seed, state.clock.day.toLong(), 909))
        val bonus = Progression.trainerBonus(data, state, "michael", Attr.FITNESS) + 8
        val cur = p.attributes[attr] ?: 30
        val (nv, prog) = Progression.attributeProgress(cur, p.trainingProgress[attr] ?: 0, Stats.attributeCap(p.level), rng.nextInt(25), bonus, 1.2)
        var s = state.copy(
            player = p.copy(attributes = p.attributes + (attr to nv), trainingProgress = p.trainingProgress + (attr to prog),
                energy = (p.energy - 25).pct(), stress = (p.stress + 4).pct()),
            career = state.career.copy(lastMentorDay = state.clock.day, examPrep = (state.career.examPrep + 8).coerceAtMost(100),
                trainingDays = (state.career.trainingDays.filter { it > state.clock.day - 90 } + state.clock.day).distinct())
        )
        s = Relations.update(data, s, "michael") { it.copy(trust = it.trust + 1, friendship = it.friendship + 1, lastTalkDay = state.clock.day) }
        val xp = Progression.grantXp(data, s, Progression.trainingXp(30, p.level))
        val t = World.passTime(data, xp.state, 1)
        return Result(t.state, listOf("Einzeltraining mit Michael: ${Attr.label(attr)} $cur → $nv", "Prüfungsvorbereitung +8") + xp.messages + t.messages)
    }

    /** Prüfungsvorbereitung mit Michael (Theorie, Szenarien, Feedback). */
    fun mentorExamPrep(data: GameData, state: GameState): Result {
        mentorAvailable(data, state)?.let { return state.fail(it) }
        if (state.player.energy < 20) return state.fail("Für die Prüfungsvorbereitung brauchst du 20 Energie.")
        var s = state.copy(
            player = state.player.copy(energy = (state.player.energy - 20).pct(), stress = (state.player.stress + 3).pct()),
            career = state.career.copy(lastMentorDay = state.clock.day, examPrep = (state.career.examPrep + 15).coerceAtMost(100))
        )
        s = Relations.update(data, s, "michael") { it.copy(trust = it.trust + 1, lastTalkDay = state.clock.day) }
        val t = World.passTime(data, s, 2)
        return Result(t.state, listOf("Michael geht mit dir Prüfungsszenarien durch. Prüfungsvorbereitung +15 (jetzt ${s.career.examPrep}).") + t.messages)
    }

    // --- Ziele ------------------------------------------------------------------

    fun setGoal(state: GameState, attribute: String, target: Int?): GameState {
        val goals = if (target == null) state.career.goals - attribute else state.career.goals + (attribute to target.coerceIn(10, Int.MAX_VALUE / 1024))
        return state.copy(career = state.career.copy(goals = goals))
    }

    /** Erreichte Ziele werden ins Journal übertragen und entfernt. */
    fun checkGoals(state: GameState, msgs: MutableList<String>? = null): GameState {
        var s = state
        state.career.goals.forEach { (a, target) ->
            if ((s.player.attributes[a] ?: 0) >= target) {
                s = s.copy(career = s.career.copy(goals = s.career.goals - a))
                s = journal(s, "goal", "Ziel erreicht: ${Attr.label(a)} $target", "Persönliches Trainingsziel erfüllt.")
                s = s.copy(player = s.player.copy(reputation = s.player.reputation + 2))
                msgs?.add("Trainingsziel erreicht: ${Attr.label(a)} $target (Ruf +2)")
            }
        }
        return s
    }

    // --- Auszeichnungen -----------------------------------------------------------

    fun award(state: GameState, id: String, title: String, text: String): GameState {
        if (id in state.career.awards) return state
        val s = state.copy(career = state.career.copy(awards = state.career.awards + id))
        return journal(s, "award", "Auszeichnung: $title", text)
    }

    val awardTitles = mapOf(
        "first_mission" to "Erster Einsatz", "missions_5" to "Bewährt (5 Einsätze)", "missions_15" to "Veteran (15 Einsätze)",
        "chapter_1" to "Neues Blut", "grade_a" to "Bestnote", "skills_10" to "Vielseitig", "all_attr_50" to "Allround-Agent",
        "team_lead" to "Teamführung", "first_exam" to "Erste Prüfung", "director" to "Direktion"
    )

    fun checkAwards(state: GameState): GameState {
        var s = state
        val wins = state.reports.count { it.result.contains("Erfolg") }
        if (wins >= 1) s = award(s, "first_mission", "Erster Einsatz", "Der erste erfolgreich abgeschlossene Auftrag.")
        if (wins >= 5) s = award(s, "missions_5", "Bewährt", "Fünf erfolgreiche Einsätze.")
        if (wins >= 15) s = award(s, "missions_15", "Veteran", "Fünfzehn erfolgreiche Einsätze.")
        if ("ch1_done" in s.flags) s = award(s, "chapter_1", "Neues Blut", "Kapitel 1 abgeschlossen.")
        if (s.player.skills.values.sum() >= 10) s = award(s, "skills_10", "Vielseitig", "Zehn Fähigkeitsstufen gelernt.")
        if (Attr.all.all { (s.player.attributes[it] ?: 0) >= 50 }) s = award(s, "all_attr_50", "Allround-Agent", "Alle Attribute mindestens 50.")
        if (s.player.rank >= 8) s = award(s, "team_lead", "Teamführung", "Rang Teamleiter erreicht.")
        if (s.player.rank >= 12) s = award(s, "director", "Direktion", "Leitung der Division übernommen.")
        return s
    }

    // --- Mehrphasige Beförderungsprüfung --------------------------------------------

    data class ScenarioOption(val id: String, val text: String, val points: Int, val feedback: String)
    data class Scenario(val title: String, val text: String, val options: List<ScenarioOption>)

    val phaseNames = listOf("Theorie", "Praxis", "Lageentscheidung", "Urteil")

    fun scenario(rank: Int): Scenario = when {
        rank <= 4 -> Scenario(
            "Zwischenfall im Training",
            "Bei einer Übung verletzt sich eine Rekrutin. Die Zeit läuft, Michael beobachtet über die Kamera. Was tust du?",
            listOf(
                ScenarioOption("aid", "Übung unterbrechen, Erstversorgung, Meldung an die Aufsicht", 30, "Richtig: Menschen vor Wertung."),
                ScenarioOption("finish", "Übung beenden und danach Hilfe holen", 10, "Zu spät: Die Verletzung wurde schlimmer."),
                ScenarioOption("ignore", "Weitermachen – die Wertung zählt", 0, "Disqualifizierend: Team zuerst.")
            )
        )
        rank <= 7 -> Scenario(
            "Kompromittierte Quelle",
            "Eine Informantin bittet um Schutz, doch der Einsatzleiter will sie als Köder benutzen. Deine Empfehlung?",
            listOf(
                ScenarioOption("protect", "Schutz gewähren und eine alternative Spur vorschlagen", 30, "Überzeugend: Ziel erreicht, Quelle geschützt."),
                ScenarioOption("bait", "Köderplan mit zusätzlicher Sicherung", 15, "Vertretbar, aber riskant für die Quelle."),
                ScenarioOption("drop", "Quelle fallen lassen – zu unsicher", 5, "Kurzsichtig: Vertrauen künftiger Quellen verspielt.")
            )
        )
        else -> Scenario(
            "Führungsentscheidung",
            "Zwei Teams melden gleichzeitig Kontakt. Du hast Kapazität für einen sicheren Rückzug. Wie führst du?",
            listOf(
                ScenarioOption("triage", "Lage beider Teams abfragen, dann priorisieren und Unterstützung umleiten", 30, "Souverän: Informationen vor Reflex."),
                ScenarioOption("closest", "Das nähere Team unterstützen, das andere sofort abziehen", 18, "Pragmatisch, aber ohne Lagebild."),
                ScenarioOption("self", "Selbst hinausfahren", 4, "Führung bedeutet, die Zentrale nicht zu verlassen.")
            )
        )
    }

    fun examLock(data: GameData, state: GameState): String? {
        val check = Progression.nextRankCheck(data, state) ?: return "Höchster Rang erreicht."
        if (!check.ready) return "Noch nicht alle Voraussetzungen für „${check.rank.name}“ erfüllt."
        val c = state.career
        if (state.clock.day < c.examCooldownUntil) return "Nächster Prüfungstermin in ${c.examCooldownUntil - state.clock.day} Tagen."
        if (c.examPrep < c.examPrepRequired) return "Auflage: Prüfungsvorbereitung ${c.examPrep}/${c.examPrepRequired}."
        if (state.location != "director") return "Prüfungen finden im Direktorat statt (Ebene −3)."
        if (state.player.energy < 30) return "Für die Prüfung brauchst du mindestens 30 Energie."
        if (state.activeMission != null) return "Erst den laufenden Einsatz beenden."
        return null
    }

    fun startExam(data: GameData, state: GameState): Result {
        examLock(data, state)?.let { return state.fail(it) }
        val rank = Progression.nextRankCheck(data, state)!!.rank.rank
        return Result(state.copy(activeExam = ExamRun(rank)), listOf("Prüfung für „${data.rank(rank).name}“ beginnt."))
    }

    fun phaseCheck(state: GameState, phase: Int, rank: Int): CheckDef = when (phase) {
        0 -> CheckDef(Attr.INTELLECT, "planning", 30 + rank * 4)
        else -> when {
            rank <= 4 -> CheckDef(Attr.FITNESS, "defense", 30 + rank * 4)
            rank <= 7 -> CheckDef(Attr.PRECISION, "weapon_handling", 30 + rank * 4)
            else -> CheckDef(Attr.PERCEPTION, "inspire", 30 + rank * 4)
        }
    }

    fun phaseBonus(state: GameState, phase: Int): Int = if (phase == 0) state.career.examPrep / 4 else state.career.examPrep / 8

    fun phaseChance(data: GameData, state: GameState): Int? {
        val e = state.activeExam ?: return null
        if (e.phase > 1) return null
        return Stats.checkChance(data, state, phaseCheck(state, e.phase, e.rank), phaseBonus(state, e.phase))
    }

    /** Phase 0 (Theorie) und 1 (Praxis): Proben mit Vorbereitungsbonus; Teilpunkte nach Abstand. */
    fun takePhase(data: GameData, state: GameState): Result {
        val e = state.activeExam ?: return state.fail("Keine Prüfung aktiv.")
        if (e.phase > 1 || e.finished) return state.fail("Diese Phase ist bereits abgeschlossen.")
        val check = phaseCheck(state, e.phase, e.rank)
        val (s1, roll) = Checks.roll(data, state, check, phaseBonus(state, e.phase), salt = 4100L + e.phase)
        val margin = roll.chance - roll.roll
        val pts = if (roll.success) (25 + (margin / 4).coerceAtMost(10)) else (8 + (margin / 6).coerceAtLeast(-8)).coerceAtLeast(0)
        val label = phaseNames[e.phase]
        val note = "$label: ${if (roll.success) "bestanden" else "schwach"} (${roll.roll}/${roll.chance}) → +$pts"
        val s = s1.copy(activeExam = e.copy(phase = e.phase + 1, score = e.score + pts, notes = e.notes + note),
            player = s1.player.copy(energy = (s1.player.energy - 8).pct(), stress = (s1.player.stress + 4).pct()))
        return Result(s, listOf(note))
    }

    /** Phase 2: Lageentscheidung; danach Phase 3 (Urteil) automatisch. */
    fun chooseScenario(data: GameData, state: GameState, optionId: String): Result {
        val e = state.activeExam ?: return state.fail("Keine Prüfung aktiv.")
        if (e.phase != 2) return state.fail("Erst Theorie und Praxis absolvieren.")
        val opt = scenario(e.rank).options.firstOrNull { it.id == optionId } ?: return state.fail("Option fehlt")
        val michael = Relations.get(data, state, "michael").trust
        val ev = latestEvaluation(state, 120)
        val evBonus = when (ev?.grade) { "A" -> 8; "B" -> 4; "C" -> 0; "D" -> -4; "E" -> -8; else -> -4 }
        val judge = ((michael - 50) / 5).coerceIn(-6, 8) + evBonus
        val total = e.score + opt.points + judge
        val passed = total >= PASS_SCORE
        val notes = e.notes + "Lageentscheidung: ${opt.feedback} → +${opt.points}" + "Urteil (Michael, Beurteilung ${ev?.grade ?: "–"}): ${Effs.signed(judge)}" + "Gesamt: $total / $PASS_SCORE"
        var s = state.copy(activeExam = e.copy(phase = 4, score = total, notes = notes, finished = true, passed = passed))
        val rankName = data.rank(e.rank).name
        s = award(s, "first_exam", "Erste Prüfung", "Die erste Beförderungsprüfung abgelegt.")
        s = if (passed) {
            var p = Progression.applyPromotion(data, s, e.rank)
            p = p.copy(career = p.career.copy(examPrep = 0, examPrepRequired = 0, examCooldownUntil = -1))
            journal(p, "promotion", "Beförderung: $rankName", "Prüfung mit $total Punkten bestanden. " + notes.dropLast(1).joinToString(" · "))
        } else {
            val c = s.career
            val req = (30 + c.failedExams * 10).coerceAtMost(80)
            var f = s.copy(
                career = c.copy(examPrep = 0, examPrepRequired = req, examCooldownUntil = s.clock.day + EXAM_COOLDOWN, failedExams = c.failedExams + 1),
                player = s.player.copy(stress = (s.player.stress + 12).pct())
            )
            f = journal(f, "exam_failed", "Prüfung nicht bestanden: $rankName", "$total von $PASS_SCORE Punkten. Auflage: Prüfungsvorbereitung $req, Wiederholung frühestens in $EXAM_COOLDOWN Tagen.")
            f
        }
        s = checkAwards(s)
        val t = World.passTime(data, s, 2)
        val msg = if (passed) "Bestanden mit $total Punkten! Neuer Rang: $rankName. +1 Fähigkeitspunkt" else "Nicht bestanden ($total/$PASS_SCORE). Wiederholung in $EXAM_COOLDOWN Tagen nach weiterer Vorbereitung."
        return Result(t.state, listOf(msg) + t.messages)
    }

    fun closeExam(state: GameState): GameState = state.copy(activeExam = null)

    // --- Freischaltungen je Rang ------------------------------------------------------

    fun shopDiscount(state: GameState): Int = if (state.player.rank >= 6) 10 else 0
}

package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v0.7 Ermittlungsjournal: Fälle mit Hinweisen unterschiedlicher Quellenqualität,
 * Widersprüchen, Verbindungen zwischen Personen und Orten und drei
 * Lösungswegen (Beweislage, Konfrontation, Falle stellen). Hinweise stammen aus
 * erfüllten Aufträgen und Einsatzketten und bleiben im Spielstand erhalten.
 */
object Cases {
    data class ClueDef(
        val id: String, val text: String, val quality: Int, val points: String?, val people: List<String>,
        val places: List<String>, val contradicts: String? = null
    )

    data class CaseDef(
        val id: String, val title: String, val summary: String, val faction: String, val openDay: Int,
        val openFlag: String? = null, val suspects: List<String>, val truth: String, val clues: List<ClueDef>,
        val solvedText: String, val wrongText: String
    )

    val all = listOf(
        CaseDef("harbor_mole", "Der Maulwurf im Hafen", "Jemand im Industriehafen verrät Liefertermine an Black Meridian.", "syndicate", 10,
            suspects = listOf("Hafenmeister Krüger", "Zollbeamtin Wendt", "Kranführer Ostrowski"), truth = "Zollbeamtin Wendt",
            clues = listOf(
                ClueDef("hm1", "Ein Hafenarbeiter behauptet, Krüger habe nachts Besuch von Fremden bekommen.", 1, "Hafenmeister Krüger", listOf("Hafenmeister Krüger"), listOf("Hafenmeisterei")),
                ClueDef("hm2", "Die Zollbox war an drei Lieferabenden außerhalb des Dienstplans geöffnet – registriert auf Wendts Karte.", 3, "Zollbeamtin Wendt", listOf("Zollbeamtin Wendt"), listOf("Zollbox")),
                ClueDef("hm3", "Ostrowski hat Spielschulden, sagt sein Schichtleiter.", 1, "Kranführer Ostrowski", listOf("Kranführer Ostrowski"), listOf("Kranführerhaus")),
                ClueDef("hm4", "Krügers Besucher waren laut Gästebuch Inspektoren der Hafenbehörde.", 3, null, listOf("Hafenmeister Krüger"), listOf("Hafenmeisterei"), contradicts = "hm1"),
                ClueDef("hm5", "Ein Prepaid-Telefon im Spind von Wendt wählte zweimal eine Nummer von Black Meridian.", 3, "Zollbeamtin Wendt", listOf("Zollbeamtin Wendt"), listOf("Spinde")),
                ClueDef("hm6", "Ostrowski war in der fraglichen Nacht nachweislich im Krankenhaus.", 2, null, listOf("Kranführer Ostrowski"), listOf("Städtisches Krankenhaus"), contradicts = "hm3"),
                ClueDef("hm7", "Wendt hat kürzlich einen Kredit in bar getilgt, berichtet eine Kollegin.", 2, "Zollbeamtin Wendt", listOf("Zollbeamtin Wendt"), listOf("Industriehafen"))
            ),
            solvedText = "Wendt wird diskret aus dem Verkehr gezogen. Black Meridian verliert seine Augen im Hafen.",
            wrongText = "Die falsche Person steht unter Verdacht. Der wahre Maulwurf liefert weiter – und ist gewarnt."),
        CaseDef("ghost_account", "Das Geisterkonto", "Über ein Konto ohne Inhaber fließt Geld an Abtrünnige – oder an jemanden, der sich als solche ausgibt.", "independent", 90,
            suspects = listOf("Buchhalter Lenz", "Analystin Varga", "Berater Holm"), truth = "Berater Holm",
            clues = listOf(
                ClueDef("ga1", "Die Kontoeröffnung erfolgte mit einer Kopie von Lenz' Ausweis.", 2, "Buchhalter Lenz", listOf("Buchhalter Lenz"), listOf("Innenstadt")),
                ClueDef("ga2", "Lenz meldete den Ausweis drei Wochen vorher als gestohlen – das Protokoll existiert.", 3, null, listOf("Buchhalter Lenz"), listOf("Innenstadt"), contradicts = "ga1"),
                ClueDef("ga3", "Varga hatte Zugriff auf die Überweisungsdaten der Division.", 1, "Analystin Varga", listOf("Analystin Varga"), listOf("Division-Hauptquartier")),
                ClueDef("ga4", "Holm hat jede Überweisung mit derselben Uhrzeit signiert – derselben, zu der er laut Kalender im Diplomatenviertel war.", 3, "Berater Holm", listOf("Berater Holm"), listOf("Diplomatenviertel")),
                ClueDef("ga5", "Ein Kurier erkennt Holm auf einem Foto als Auftraggeber.", 2, "Berater Holm", listOf("Berater Holm"), listOf("Internationaler Flughafen")),
                ClueDef("ga6", "Vargas Zugriffsprotokolle zeigen keinen Abruf der fraglichen Daten.", 3, null, listOf("Analystin Varga"), listOf("Division-Hauptquartier"), contradicts = "ga3")
            ),
            solvedText = "Holm hat Gelder abgezweigt und Abtrünnige vorgeschoben. Die Abtrünnigen erfahren, wer ihnen das anhängen wollte.",
            wrongText = "Eine Unschuldige wird zur Rede gestellt. Holm verschwindet mit dem Geld."),
        CaseDef("phoenix_trace", "Die PHOENIX-Spur", "In der Klinik tauchen Akten auf, die zu deinem Programm passen.", "phoenix", 300, openFlag = "phoenix_truth",
            suspects = listOf("Dr. Reiter", "Klinikleiterin Sand", "Pfleger Amir"), truth = "Dr. Reiter",
            clues = listOf(
                ClueDef("pt1", "Pfleger Amir hat nachts Akten aus dem Archivraum getragen – sagt die Nachtschicht.", 1, "Pfleger Amir", listOf("Pfleger Amir"), listOf("Archivraum")),
                ClueDef("pt2", "Amir brachte die Akten auf Anweisung zur Digitalisierung, belegt durch einen Auftrag mit Reiters Unterschrift.", 3, "Dr. Reiter", listOf("Pfleger Amir", "Dr. Reiter"), listOf("Archivraum"), contradicts = "pt1"),
                ClueDef("pt3", "Sand hat die Akten nie angefordert – die Ausleihliste ist lückenlos.", 2, null, listOf("Klinikleiterin Sand"), listOf("Städtisches Krankenhaus")),
                ClueDef("pt4", "Reiter taucht als Gutachter in drei PHOENIX-Dokumenten auf.", 3, "Dr. Reiter", listOf("Dr. Reiter"), listOf("Geheimarchiv")),
                ClueDef("pt5", "Ein anonymer Hinweis beschuldigt Sand, Patientendaten zu verkaufen.", 1, "Klinikleiterin Sand", listOf("Klinikleiterin Sand"), listOf("Städtisches Krankenhaus")),
                ClueDef("pt6", "Reiter hat ein Schließfach am Flughafen, bezahlt aus einem PHOENIX-Budget.", 2, "Dr. Reiter", listOf("Dr. Reiter"), listOf("Internationaler Flughafen"))
            ),
            solvedText = "Reiter war ein Glied der PHOENIX-Kette. Seine Unterlagen bringen dich deiner eigenen Geschichte näher.",
            wrongText = "Die Spur zerfällt. PHOENIX räumt die Klinik, bevor du wiederkommst."),
        CaseDef("directorate_leak", "Das Leck im Direktorat", "Interna des Direktorats landen bei der Regierungsaufsicht. Percy will einen Namen.", "government", 220,
            suspects = listOf("Techniker Mallory", "Sekretärin Hart", "Agent Kessler"), truth = "Agent Kessler",
            clues = listOf(
                ClueDef("dl1", "Mallory hatte Zugang zu den Servern, auf denen die Berichte lagen.", 1, "Techniker Mallory", listOf("Techniker Mallory"), listOf("Techniklabor")),
                ClueDef("dl2", "Die geleakten Berichte enthalten handschriftliche Randnotizen, die nur in Papierausdrucken existierten.", 3, null, listOf("Techniker Mallory"), listOf("Direktorat"), contradicts = "dl1"),
                ClueDef("dl3", "Hart hat die Ausdrucke vernichtet – das Vernichtungsprotokoll ist vollständig.", 2, null, listOf("Sekretärin Hart"), listOf("Direktorat")),
                ClueDef("dl4", "Kessler war an allen Tagen der Leaks zur Besprechung im Direktorat.", 2, "Agent Kessler", listOf("Agent Kessler"), listOf("Direktorat")),
                ClueDef("dl5", "Kessler traf sich zweimal mit einer Referentin der Aufsicht im Diplomatenviertel.", 3, "Agent Kessler", listOf("Agent Kessler"), listOf("Diplomatenviertel")),
                ClueDef("dl6", "Ein Gerücht im Aufenthaltsraum nennt Hart als Quelle.", 1, "Sekretärin Hart", listOf("Sekretärin Hart"), listOf("Aufenthaltsbereich"))
            ),
            solvedText = "Kessler gesteht: Er wollte die Aufsicht vor Percys Plänen warnen. Was du mit dieser Wahrheit machst, bleibt in deiner Akte.",
            wrongText = "Eine Unschuldige verliert ihren Posten. Das Leck bleibt offen – und Percy misstraut dir.")
    )
    val byId = all.associateBy { it.id }

    fun clueDef(id: String): Pair<CaseDef, ClueDef>? = all.firstNotNullOfOrNull { c -> c.clues.firstOrNull { it.id == id }?.let { c to it } }

    fun isOpen(state: GameState, c: CaseDef) = state.ops.cases[c.id]?.let { !it.solved } ?: false

    fun known(state: GameState, caseId: String) = state.ops.clues.filter { it.caseId == caseId }

    /** Öffnet Fälle nach Spieltag bzw. Storyflag. */
    fun daily(data: GameData, state: GameState): GameState {
        var s = state
        all.forEach { c ->
            if (s.ops.cases.containsKey(c.id)) return@forEach
            if (s.clock.day < c.openDay) return@forEach
            if (c.openFlag != null && c.openFlag !in s.flags) return@forEach
            s = s.copy(ops = s.ops.copy(cases = s.ops.cases + (c.id to CaseState(c.id, openedDay = s.clock.day))))
            s = Ops.worldLog(s, "Neue Ermittlung: ${c.title}")
        }
        return s
    }

    fun addClue(state: GameState, clueId: String, source: String): Pair<GameState, String?> {
        if (state.ops.clues.any { it.id == clueId }) return state to null
        val (case, def) = clueDef(clueId) ?: return state to null
        val cl = Clue(def.id, case.id, def.text, source, def.quality, def.points, def.people, def.places, def.contradicts, state.clock.day)
        var s = state
        if (!s.ops.cases.containsKey(case.id)) s = s.copy(ops = s.ops.copy(cases = s.ops.cases + (case.id to CaseState(case.id, openedDay = s.clock.day))))
        s = s.copy(ops = s.ops.copy(clues = s.ops.clues + cl))
        return s to "Neuer Hinweis (${case.title}): ${qualityLabel(def.quality)}"
    }

    fun qualityLabel(q: Int) = when (q) { 3 -> "Beweis"; 2 -> "Aussage"; else -> "Gerücht" }

    private val investigative = setOf("data", "intel", "observation", "negotiation", "recon", "undercover")

    fun onContractSuccess(data: GameData, state: GameState, c: Contract, run: MissionRun): Pair<GameState, List<String>> {
        var s = state
        val notes = mutableListOf<String>()
        // Falle eines Falls: löst ihn mit der Wahrheit
        if (c.caseId != null) {
            val case = byId[c.caseId]
            if (case != null && isOpen(s, case)) {
                val r = conclude(data, s, case, case.truth, "trap")
                return r.state to (notes + r.messages)
            }
        }
        if (c.archetype !in investigative) return s to notes
        val open = all.filter { isOpen(s, it) }.sortedBy { if (it.faction == c.targetFaction) 0 else 1 }
        val case = open.firstOrNull() ?: return s to notes
        val missing = case.clues.filter { cl -> s.ops.clues.none { it.id == cl.id } }
        val extra = Sites.siteLevel(data, s, "analysis") >= 2 || Sites.moduleLevel(s, "analysis_room") >= 3 || ("proj_pattern_lab" in s.flags && (c.seed and 1L) == 0L)
        val count = (if (run.intel >= 4) 2 else 1) + if (extra) 1 else 0
        missing.take(count).forEach { cl -> addClue(s, cl.id, c.title).let { (ns, n) -> s = ns; n?.let { notes += it } } }
        return s to notes
    }

    /** Gewichtete Beweislage je Verdächtigem; widerlegte Hinweise zählen nicht. */
    fun scores(state: GameState, caseId: String): Map<String, Int> {
        val clues = known(state, caseId)
        val refuted = clues.mapNotNull { it.contradicts }.toSet()
        val case = byId[caseId] ?: return emptyMap()
        return case.suspects.associateWith { sus -> clues.filter { it.points == sus && it.id !in refuted }.sumOf { it.quality } }
    }

    fun contradictionsFound(state: GameState, caseId: String): List<Pair<Clue, Clue>> {
        val clues = known(state, caseId)
        return clues.mapNotNull { c -> c.contradicts?.let { other -> clues.firstOrNull { it.id == other }?.let { c to it } } }
    }

    data class Method(val id: String, val label: String, val requirement: String)

    fun methods(state: GameState, caseId: String, suspect: String): List<Pair<Method, Boolean>> {
        val score = scores(state, caseId)[suspect] ?: 0
        val pointing = known(state, caseId).count { it.points == suspect }
        val total = known(state, caseId).size
        return listOf(
            Method("evidence", "Beweislage vorlegen", "Gewichtete Beweislage ≥ 5 gegen diese Person") to (score >= 5),
            Method("confront", "Konfrontieren", "≥ 2 Hinweise auf diese Person · Probe Willenskraft/Vernehmung") to (pointing >= 2),
            Method("trap", "Falle stellen", "≥ 3 Hinweise im Fall · erzeugt einen Einsatz, der die Wahrheit zeigt") to (total >= 3)
        )
    }

    fun solve(data: GameData, state: GameState, caseId: String, suspect: String, method: String): Result {
        val case = byId[caseId] ?: return state.fail("Fall unbekannt")
        if (!isOpen(state, case)) return state.fail("Fall ist abgeschlossen.")
        if (suspect !in case.suspects) return state.fail("Unbekannte Person")
        val ok = methods(state, caseId, suspect).firstOrNull { it.first.id == method }?.second ?: false
        if (!ok) return state.fail("Voraussetzungen für diesen Weg fehlen noch.")
        return when (method) {
            "trap" -> {
                if (state.ops.contracts.any { it.caseId == caseId }) return state.fail("Die Falle ist bereits vorbereitet.")
                val arch = Ops.archetypeById.getValue(if (Ages.player(state) >= 18) "intel" else "observation")
                val base = Ops.generate(data, state, state.clock.day, state.ops.counter, Rng.mix(state.seed, caseId.hashCode().toLong(), 313), forced = arch)
                    ?: return state.fail("Keine Falle möglich")
                val trap = base.copy(title = "Falle: ${case.title}", targetFaction = case.faction, caseId = caseId, urgency = 2, complicationId = "none",
                    briefing = "Ihr streut eine präparierte Information. Wer danach greift, ist der Verräter. ${base.briefing}")
                val s = state.copy(ops = state.ops.copy(contracts = state.ops.contracts + trap, counter = state.ops.counter + 1))
                Result(s, listOf("Die Falle ist vorbereitet: Auftrag „${trap.title}“ auf dem Missionsboard."))
            }
            "confront" -> {
                val (s1, roll) = Checks.roll(data, state, CheckDef(Attr.WILLPOWER, "interview", 45), salt = caseId.hashCode().toLong())
                if (!roll.success) Result(s1.copy(player = s1.player.copy(stress = (s1.player.stress + 8).coerceIn(0, 100))),
                    listOf("Die Konfrontation bringt nichts – die Person schweigt. (${roll.roll}/${roll.chance}) Sammle weitere Hinweise."), ok = false)
                else conclude(data, s1, case, suspect, method)
            }
            else -> conclude(data, state, case, suspect, method)
        }
    }

    private fun conclude(data: GameData, state: GameState, case: CaseDef, accused: String, method: String): Result {
        val correct = accused == case.truth
        var s = state
        val msgs = mutableListOf<String>()
        s = s.copy(ops = s.ops.copy(cases = s.ops.cases + (case.id to (s.ops.cases[case.id] ?: CaseState(case.id)).copy(
            solved = true, solution = method, accused = accused, resultText = if (correct) case.solvedText else case.wrongText))))
        if (correct) {
            s = s.copy(flags = s.flags + "case_${case.id}_solved")
            s = Ops.shiftInfluence(s, case.faction, -10, resources = -5)
            s = s.copy(player = s.player.copy(reputation = s.player.reputation + 8))
            msgs += case.solvedText
            msgs += "${Ops.factionName(data, case.faction)} Einfluss −10 · Ruf +8"
            val xr = Progression.grantXp(data, s, 150)
            s = xr.state; msgs += xr.messages
        } else {
            s = s.copy(flags = s.flags + "case_${case.id}_wrong")
            s = Ops.shiftInfluence(s, case.faction, 5)
            s = Ops.shiftAttitude(s, "civilian", -5)
            msgs += case.wrongText
            msgs += "${Ops.factionName(data, case.faction)} Einfluss +5 · Zivilbevölkerung Haltung −5"
        }
        // Folgen für die Divisionspolitik
        if (case.id == "directorate_leak" && Relations.isKnownNpc(data, s, "percy")) s = Relations.update(data, s, "percy") { it.copy(trust = it.trust + if (correct) 6 else -6) }
        if (case.id == "phoenix_trace" && correct) s = s.copy(flags = s.flags + "phoenix_reiter_files")
        s = Ops.worldLog(s, "Ermittlung abgeschlossen: ${case.title} – ${if (correct) "aufgeklärt" else "falsche Spur"}.")
        return Result(s, msgs)
    }
}

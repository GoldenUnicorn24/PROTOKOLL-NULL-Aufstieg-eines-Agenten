package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/** Seed-basierter Missionsgenerator für das wöchentliche Einsatzboard. */
object MissionGenerator {
    private val codewords = listOf(
        "Nachtfalke", "Graufeld", "Eisvogel", "Kupferdraht", "Leuchtturm", "Scherbe", "Morgenrot",
        "Sandsturm", "Glasherz", "Kranich", "Nordlicht", "Basalt", "Fernglas", "Seidenfaden", "Ankerplatz"
    )

    val typeLabels = mapOf(
        "dialogue" to "Gespräch", "investigation" to "Untersuchung", "undercover" to "Undercover",
        "observation" to "Beobachtung", "rescue" to "Rettung", "tactical" to "Taktik",
        "training" to "Ausbildung", "social" to "Sozial", "story" to "Story", "management" to "Führung"
    )

    private val maps = listOf(
        listOf(
            "#.E..E#",
            "#.c.c.#",
            "#.....#",
            "#c.#.c#",
            "#..#..#",
            "#.c..c#",
            "#.....#",
            "#c...c#",
            "#.PPP.#"
        ),
        listOf(
            ".E...E.",
            ".cc.cc.",
            ".......",
            "##.c.##",
            "...c...",
            ".c...c.",
            "...E...",
            ".c.c.c.",
            "PP...PP"
        ),
        listOf(
            "##.X.##",
            "#..c..#",
            "#E...E#",
            "#.c#c.#",
            "#..#..#",
            "#c...c#",
            "#..E..#",
            "#.c.c.#",
            "#.PPP.#"
        )
    )

    private fun spotsFor(placeType: String, placeId: String): List<String> = when (placeId) {
        "harbor" -> listOf("Container 14", "Kranführerhaus", "Frachtliste", "Hafenmeisterei", "Lagerhalle C", "Laderampe", "Gabelstapler", "Spinde der Arbeiter", "Zollbox")
        "airport" -> listOf("Gepäckband 3", "Mietwagenschalter", "Lounge", "Schließfächer", "Überwachungsraum", "Toiletten", "Café-Quittungen", "Parkdeck", "Fundbüro")
        "hospital" -> listOf("Patientenakten", "Schwesternzimmer", "Medikamentenschrank", "Besucherliste", "Kapelle", "Wäscherei", "Notaufnahme", "Aufzug B", "Kiosk")
        "embassy" -> listOf("Empfang", "Garderobe", "Arbeitszimmer", "Bibliothek", "Wintergarten", "Weinkeller", "Fahrzeugpool", "Sicherheitsbüro", "Konferenzraum")
        "safehouse" -> listOf("Küche", "Matratze", "Lüftungsschacht", "Briefkasten", "Kühlschrank", "Papierkorb", "Dielenbrett", "Badezimmer", "Fensterbank")
        "archives" -> listOf("Regal A-17", "Mikrofilm-Leser", "Ausleihjournal", "Panzerschrank", "Lesetisch", "Kopierer", "Rohrpost", "Zugangsprotokoll", "Aktenvernichter")
        else -> listOf("Schreibtisch", "Schrank", "Terminal", "Papierkorb", "Mantelablage", "Notizwand", "Safe", "Bücherregal", "Postfach")
    }

    fun refreshBoard(data: GameData, state: GameState): GameState {
        val week = Clock.week(state.clock.day)
        if (state.boardWeek == week && state.board.isNotEmpty()) return state
        val count = if (state.player.rank >= 5) 4 else 3
        val list = (0 until count).map { i -> generate(data, state, Rng.mix(state.seed, week.toLong(), i.toLong()), "G${week}_$i") }
        return state.copy(board = list, boardWeek = week)
    }

    fun generate(data: GameData, state: GameState, seed: Long, id: String): MissionDefinition {
        val rng = Rng(seed)
        val age = Ages.player(state)
        val adult = age >= 18
        val types = if (adult) listOf("dialogue", "investigation", "undercover", "observation", "rescue", "tactical")
        else listOf("dialogue", "investigation", "observation")
        val type = rng.pick(types)
        val threat = if (!adult) rng.range(1, 2) else (state.player.rank / 2 + rng.range(0, 2)).coerceIn(1, 5)
        val placePool = data.places.filter { it.type == "mission" || it.type == "city" }
        val place = rng.pick(placePool)
        val faction = rng.pick(listOf("syndicate", "syndicate", "independent", "civilian", "government"))
        val factionName = data.factionById[faction]?.name ?: faction
        val witnesses = rng.range(0, 3)
        val code = rng.pick(codewords)
        val title = when (type) {
            "dialogue" -> "Gespräch: $code"
            "investigation" -> "Spurensuche „$code“"
            "undercover" -> "Legende „$code“"
            "observation" -> "Observation „$code“"
            "rescue" -> "Rettung „$code“"
            else -> "Operation $code"
        }
        val target = rng.pick(listOf("einen Kurier", "eine Buchhalterin", "einen Zollbeamten", "eine Journalistin", "einen Ex-Agenten", "einen IT-Dienstleister", "eine Übersetzerin"))
        val stages = when (type) {
            "dialogue" -> listOf(
                StageDefinition("s1", "choice", "Erster Eindruck", "Du triffst $target im ${place.name}. Die Person ist nervös und will schnell wieder weg.", options = listOf(
                    StageOption("calm", "Ruhig und verständnisvoll beginnen", CheckDef(Attr.CHARISMA, "empathy", 35 + threat * 5), success = Outcome("Die Schultern entspannen sich.", intel = 1), failure = Outcome("Misstrauen bleibt.", alarm = 1)),
                    StageOption("legend", "Mit deiner Legende als Studentin/Student auftreten", CheckDef(Attr.CHARISMA, "disguise", 40 + threat * 5), success = Outcome("Die Geschichte sitzt.", intel = 1, cover = 1), failure = Outcome("Ein Detail passt nicht.", cover = -1))
                )),
                StageDefinition("s2", "choice", "Der Kern", "Es geht um eine Verbindung zu $factionName. Wie bringst du die Person zum Reden?", options = listOf(
                    StageOption("listen", "Zuhören und Widersprüche sammeln", CheckDef(Attr.PERCEPTION, "lie_detector", 40 + threat * 5), success = Outcome("Ein Name fällt – beiläufig, aber eindeutig.", intel = 2), failure = Outcome("Du verlierst den Faden.")),
                    StageOption("pressure", "Freundlich, aber bestimmt Druck machen", CheckDef(Attr.WILLPOWER, "interview", 45 + threat * 5), success = Outcome("Die Person knickt ein.", intel = 2, stress = 5), failure = Outcome("Die Person blockt ab.", alarm = 1, stress = 5)),
                    StageOption("deal", "Schutz anbieten", CheckDef(Attr.CHARISMA, "negotiation", 40 + threat * 5), success = Outcome("Ein Deal: Informationen gegen Sicherheit.", intel = 2, effects = Effects(credits = -50)), failure = Outcome("Kein Vertrauen in die Division."))
                )),
                StageDefinition("s3", "choice", "Abschluss", "Das Gespräch neigt sich dem Ende zu.", options = listOf(
                    StageOption("wrap", "Sauber verabschieden und Kontakt halten", CheckDef(Attr.CHARISMA, null, 30), success = Outcome("Ein künftiger Informant.", next = "END_SUCCESS", effects = Effects(reputation = 3)), failure = Outcome("Die Person verschwindet hastig.", next = "END_SUCCESS")),
                    StageOption("abort", "Abbrechen – zu riskant", success = Outcome("Du ziehst dich zurück.", next = "END_ABORT"))
                ))
            )
            "investigation" -> listOf(
                StageDefinition("s1", "search", "Tatort sichern", "Du hast begrenzt Zeit im ${place.name}. Finde ${2 + threat / 2} Hinweise auf $factionName.", spots = rng.shuffled(spotsFor(place.type, place.id)).take(9), cluesNeeded = 2 + threat / 2, actions = 6 + (state.player.skills["evidence"] ?: 0)),
                StageDefinition("s2", "choice", "Auswertung", "Die Spuren müssen zusammengesetzt werden.", options = listOf(
                    StageOption("analyze", "Muster analysieren", CheckDef(Attr.INTELLECT, "analysis", 35 + threat * 6), success = Outcome("Die Spur führt zu einem Lagerplatz.", next = "END_SUCCESS", intel = 1), failure = Outcome("Teilergebnis – reicht für einen Bericht.", next = "END_SUCCESS")),
                    StageOption("call", "Birkhoff hinzuziehen", success = Outcome("Birkhoff seufzt hörbar, liefert aber.", next = "END_SUCCESS", effects = Effects(otherTrust = mapOf("birkhoff" to 1))))
                ))
            )
            "undercover" -> listOf(
                StageDefinition("s1", "choice", "Einschleusen", "Du betrittst ${place.name} als Aushilfe. Dein Ziel: $target.", options = listOf(
                    StageOption("blend", "Unauffällig bleiben", CheckDef(Attr.PERCEPTION, "blend_in", 40 + threat * 5), success = Outcome("Niemand beachtet dich.", cover = 1), failure = Outcome("Ein Wachmann merkt sich dein Gesicht.", cover = -1, alarm = 1)),
                    StageOption("charm", "Mit Charme Türen öffnen", CheckDef(Attr.CHARISMA, "persuasion", 40 + threat * 5), success = Outcome("Man winkt dich durch.", intel = 1), failure = Outcome("Zu dick aufgetragen.", cover = -1))
                )),
                StageDefinition("s2", "observe", "Gelegenheit abwarten", "Beobachte, wer die Informationen weitergibt.", targets = listOf("Barkeeper", "Gast mit Aktentasche", "Sicherheitschef", "Fahrer"), rounds = 3, intelNeeded = 2),
                StageDefinition("s3", "choice", "Der Moment", "Jetzt oder nie.", options = listOf(
                    StageOption("swap", "Datenträger unbemerkt austauschen", CheckDef(Attr.PRECISION, "shadowstep", 45 + threat * 5), success = Outcome("Austausch gelungen.", next = "END_SUCCESS", intel = 2), failure = Outcome("Du wirst bemerkt und musst improvisiert verschwinden.", next = "END_FAIL", alarm = 2)),
                    StageOption("talk", "Im Gespräch die Information entlocken", CheckDef(Attr.CHARISMA, "interview", 45 + threat * 5), success = Outcome("Die Information fließt.", next = "END_SUCCESS", intel = 1), failure = Outcome("Die Legende bekommt Risse.", next = "END_FAIL", cover = -2)),
                    StageOption("out", "Abbrechen und Legende schützen", success = Outcome("Kein Ergebnis, aber keine Enttarnung.", next = "END_ABORT"))
                ))
            )
            "observation" -> listOf(
                StageDefinition("s1", "observe", "Position beziehen", "Aus sicherer Entfernung beobachtest du ${place.name}. Wer ist der Kontakt von $factionName?", targets = listOf("Lieferwagen", "Frau am Telefon", "Mann mit Hund", "Parkwächter"), rounds = 4, intelNeeded = 2 + threat / 3),
                StageDefinition("s2", "choice", "Dokumentation", "Du hast genug gesehen.", options = listOf(
                    StageOption("photo", "Beweisfotos sichern", CheckDef(Attr.PRECISION, "evidence", 35 + threat * 5), success = Outcome("Gestochen scharf.", next = "END_SUCCESS", intel = 1), failure = Outcome("Verwackelt – der Bericht muss reichen.", next = "END_SUCCESS")),
                    StageOption("follow", "Dem Kontakt folgen", CheckDef(Attr.PERCEPTION, "shadowstep", 45 + threat * 5), success = Outcome("Du findest den Treffpunkt.", next = "END_SUCCESS", intel = 2), failure = Outcome("Du wirst bemerkt.", next = "END_FAIL", alarm = 2))
                ))
            )
            "rescue" -> listOf(
                StageDefinition("s1", "choice", "Zugang", "$target wird im ${place.name} festgehalten.", options = listOf(
                    StageOption("quiet", "Leise über den Hintereingang", CheckDef(Attr.PERCEPTION, "shadowstep", 40 + threat * 5), success = Outcome("Unbemerkt drinnen.", intel = 1), failure = Outcome("Ein Alarm piept.", alarm = 1)),
                    StageOption("tech", "Kameras stören", CheckDef(Attr.INTELLECT, "electronics", 40 + threat * 5), success = Outcome("Die Kameras zeigen Standbilder.", intel = 1), failure = Outcome("Die Störung fällt auf.", alarm = 1))
                )),
                StageDefinition("s2", "tactical", "Zur Geisel vordringen", "Erreiche den Raum (X).", tactical = TacticalSetup(maps[2], enemyName = "Wache", enemyHp = 5, objective = "reach", maxTurns = 12)),
                StageDefinition("s3", "choice", "Versorgung", "$target ist verletzt.", options = listOf(
                    StageOption("aid", "Stabilisieren und tragen", CheckDef(Attr.INTELLECT, "stabilize", 35 + threat * 5), success = Outcome("Stabil. Ihr kommt raus.", next = "END_SUCCESS"), failure = Outcome("Ihr kommt raus – knapp.", next = "END_SUCCESS", stress = 10)),
                    StageOption("run", "Sofort raus", CheckDef(Attr.FITNESS, "stamina", 40 + threat * 5), success = Outcome("Draußen wartet der Wagen.", next = "END_SUCCESS"), failure = Outcome("Ein Sturz – die Geisel ist sicher, du bist verletzt.", next = "END_SUCCESS", health = -15))
                ))
            )
            else -> listOf(
                StageDefinition("s1", "choice", "Vorbereitung", "Ein Verbindungsmann von $factionName wird im ${place.name} vermutet.", options = listOf(
                    StageOption("plan", "Gelände studieren", CheckDef(Attr.INTELLECT, "planning", 35 + threat * 5), success = Outcome("Du kennst jede Deckung.", intel = 2), failure = Outcome("Der Plan hat Lücken.")),
                    StageOption("rush", "Schnell zugreifen", success = Outcome("Kein Zeitverlust.", alarm = 1))
                )),
                StageDefinition("s2", "tactical", "Zugriff", "Setze die Gegner außer Gefecht.", tactical = TacticalSetup(rng.pick(maps.take(2)), enemyName = "Sicherheitsmann", enemyHp = 5 + threat / 2, objective = "eliminate", maxTurns = 14), next = "END_SUCCESS")
            )
        }
        return MissionDefinition(
            id = id, title = title, locationId = place.id, type = type, minRank = 1, minAge = if (threat >= 3) 18 else 16,
            threat = threat, xp = 90 + threat * 70 + state.player.level * 6,
            description = "${typeLabels[type]} im ${place.name}. Beteiligt: $factionName. Zielperson: $target.",
            factionId = faction, briefing = "Seed #${(seed and 0xFFFF).toString(16).uppercase()} · Risiko ${"▮".repeat(threat)}${"▯".repeat(5 - threat)} · Zeugen: $witnesses",
            credits = 80 + threat * 120, reputation = 3 + threat * 3, stages = stages, witnesses = witnesses, risk = threat,
            repeatable = false, maxTeam = if (threat >= 3) 3 else 1,
            recommendedItems = when (type) {
                "investigation" -> listOf("flashlight", "camera")
                "observation" -> listOf("binoculars", "camera")
                "undercover" -> listOf("smartphone", "passport")
                "rescue" -> listOf("medkit", "vest_light")
                "tactical" -> listOf("vest_light", "radio_secure")
                else -> listOf("notebook")
            },
            successEffects = Effects(faction = mapOf(faction to if (faction == "syndicate") -5 else 3)),
            debriefSuccess = "Auftrag erfüllt. Die Erkenntnisse fließen in die Lageanalyse.",
            debriefFail = "Der Auftrag ist gescheitert. Die Division wird das nicht vergessen."
        )
    }
}

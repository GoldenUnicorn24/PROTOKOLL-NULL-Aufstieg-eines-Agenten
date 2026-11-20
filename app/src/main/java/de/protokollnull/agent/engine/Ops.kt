package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v0.7 Missionsdirektor 2.0: deterministische Aufträge aus modularen Bausteinen
 * (Ort, Einstiegssituation, Auftraggeber, Ziel, Nebenbedingung, Konsequenz,
 * Handlungskette). Der Pool ist Teil des Spielstands – erneutes Öffnen oder
 * Laden erzeugt keine Duplikate; jeder Auftrag endet mit einem Eintrag in der
 * Historie (erfüllt, gescheitert, abgelaufen, abgebrochen, abgelehnt).
 */
object Ops {
    const val POOL_MAX = 7
    const val HISTORY_CAP = 60
    const val WORLD_LOG_CAP = 30
    const val PREFIX = "OP-"

    // --- Bausteine -------------------------------------------------------------------

    data class Objective(
        val id: String,
        val label: String,
        /** Briefing-Satz; Platzhalter {target}, {region}, {faction}. */
        val text: String,
        /** Ziel auf dem Taktikbrett (null = ohne Brett). */
        val tactical: String? = null,
        val vip: String = "",
        val closeA: Pair<String, String>,
        val closeB: Pair<String, String>
    )

    data class Archetype(
        val id: String,
        val label: String,
        val minorSafe: Boolean,
        val skills: List<String>,
        val objectives: List<Objective>,
        val approaches: List<ContractApproach>,
        /** Stage-Typ der leisen Vorgehensweise: search, observe, choice */
        val quietStage: String,
        val maxTeam: Int = 2
    )

    private fun ap(id: String, name: String, desc: String, attr: String, skill: String?, style: String, tactical: Boolean = false, risk: String = "") =
        ContractApproach(id, name, desc, attr, skill, style, tactical, risk)

    val archetypes: List<Archetype> = listOf(
        Archetype("observation", "Observation", true, listOf("intel", "tech"), listOf(
            Objective("meeting", "Treffen dokumentieren", "{Target} trifft im Bereich {region} einen Unbekannten. Das Treffen soll lückenlos dokumentiert werden.", null, "",
                "Fotos sichern und abziehen" to "Saubere Beweislage, die Zielperson bleibt ahnungslos.", "Dem Unbekannten folgen" to "Mehr Erkenntnisse, aber höheres Risiko, entdeckt zu werden."),
            Objective("courier", "Kurierroute", "Ein Kurier von {faction} pendelt durch den Bereich {region}. Finde heraus, wem er die Umschläge übergibt.", null, "",
                "Route melden" to "Die Division kann die Route später überwachen.", "Übergabeort markieren" to "Der Ort wird zur Falle – wenn niemand Verdacht schöpft."),
            Objective("contact", "Kontaktperson identifizieren", "{Target} trifft sich regelmäßig mit jemandem im Bereich {region}. Wer ist die Kontaktperson?", null, "",
                "Identität bestätigen" to "Ein Name für die Akten.", "Kontaktperson ansprechen" to "Eine mögliche Quelle – oder ein Warnsignal für die Gegenseite.")
        ), listOf(
            ap("static", "Feste Position", "Aus einem Versteck beobachten. Geduld statt Bewegung.", Attr.PERCEPTION, "observe", "stealth", risk = "Zeitdruck, wenn sich die Lage ändert"),
            ap("mobile", "Mobile Verfolgung", "Zu Fuß und mit Wechseln dranbleiben.", Attr.FITNESS, "stamina", "balanced", risk = "Entdeckung bei Fehlern"),
            ap("tech", "Technische Überwachung", "Kameras und Funkbilder auswerten.", Attr.INTELLECT, "electronics", "stealth", risk = "Technikausfall")
        ), "observe"),
        Archetype("undercover", "Undercover", false, listOf("social", "intel"), listOf(
            Objective("party", "Empfang unterwandern", "Bei einem Empfang im Bereich {region} verkehrt {target}. Gewinne Vertrauen und finde heraus, was {faction} plant.", null, "",
                "Legende pflegen" to "Ein Zugang für künftige Einsätze bleibt bestehen.", "Information abschöpfen und verschwinden" to "Schnelles Ergebnis, die Legende ist verbrannt."),
            Objective("job", "Aushilfsstelle", "Als Aushilfe im Bereich {region} sollst du herausfinden, wer bei {faction} die Lieferlisten führt.", null, "",
                "Unauffällig bleiben" to "Die Stelle kann weiter genutzt werden.", "Listen kopieren" to "Wertvolle Daten, aber der Verdacht steigt."),
            Objective("swap", "Datenträger tauschen", "{Target} trägt einen Datenträger mit Kontaktlisten. Tausche ihn unbemerkt aus.", "intel", "",
                "Austausch unbemerkt abschließen" to "Die Gegenseite arbeitet mit falschen Daten weiter.", "Original behalten, keine Attrappe" to "Mehr Daten, aber der Diebstahl fällt auf.")
        ), listOf(
            ap("legend", "Ausgearbeitete Legende", "Eine durchdachte Rolle mit Lebenslauf.", Attr.CHARISMA, "disguise", "social", risk = "Detailfragen"),
            ap("staff", "Unsichtbares Personal", "Als Servicekraft niemandem auffallen.", Attr.PERCEPTION, "blend_in", "stealth", risk = "Kontrollen"),
            ap("contact", "Über einen Kontakt", "Ein Bekannter stellt dich vor.", Attr.CHARISMA, "persuasion", "social", risk = "Abhängigkeit vom Kontakt")
        ), "observe"),
        Archetype("recon", "Aufklärung", true, listOf("intel", "combat"), listOf(
            Objective("site", "Gelände kartieren", "Das Gelände im Bereich {region} wird von {faction} genutzt. Erfasse Zugänge, Wachen und Rückzugswege.", "recon", "",
                "Bericht nüchtern halten" to "Eine verlässliche Grundlage für Folgeeinsätze.", "Schwachstelle markieren" to "Ein späterer Zugriff wird leichter – wenn die Markierung unentdeckt bleibt."),
            Objective("route", "Route prüfen", "Eine Fahrtroute durch den Bereich {region} soll auf Hinterhalte geprüft werden.", "recon", "",
                "Alternativroute vorschlagen" to "Sicherere Wege für künftige Eskorten.", "Verdächtige Stelle melden" to "{faction} merkt, dass die Route bekannt ist."),
            Objective("guards", "Wachplan ermitteln", "Ermittle, wann die Wachen von {faction} im Bereich {region} wechseln.", "recon", "",
                "Wachplan dokumentieren" to "Planbare Folgeeinsätze.", "Wachposten ablenken und tiefer vordringen" to "Mehr Einblick, mehr Risiko.")
        ), listOf(
            ap("distance", "Aus der Distanz", "Mit Fernglas und Geduld.", Attr.PERCEPTION, "observe", "stealth", risk = "Sichtbehinderung"),
            ap("close", "Nahaufklärung", "Selbst das Gelände ablaufen.", Attr.PRECISION, "shadowstep", "stealth", tactical = true, risk = "Kontakt mit Wachen"),
            ap("drone", "Übungsdrohne", "Abstrakte Luftbilder auswerten.", Attr.INTELLECT, "systems", "balanced", risk = "Funkstörung")
        ), "search"),
        Archetype("negotiation", "Verhandlung", true, listOf("social"), listOf(
            Objective("mediate", "Streit schlichten", "Zwei Informanten im Bereich {region} sind zerstritten. Ohne Einigung verliert die Division beide.", null, "",
                "Kompromiss vermitteln" to "Beide bleiben Quellen.", "Eine Seite bevorzugen" to "Eine starke Quelle, ein neuer Gegner."),
            Objective("buy", "Information kaufen", "{Target} bietet Wissen über {faction} an – zu einem Preis.", null, "",
                "Fairen Preis zahlen" to "Ein verlässlicher Kontakt.", "Hart verhandeln" to "Günstiger, aber der Kontakt fühlt sich ausgenutzt."),
            Objective("turn", "Informant gewinnen", "{Target} arbeitet für {faction} und zweifelt. Überzeuge die Person, die Seiten zu wechseln.", null, "",
                "Schutz zusichern" to "Eine wertvolle Quelle – und eine Verantwortung.", "Druck aufbauen" to "Schnelles Ergebnis, brüchiges Vertrauen.")
        ), listOf(
            ap("empathy", "Zuhören", "Bedürfnisse verstehen, Vertrauen aufbauen.", Attr.CHARISMA, "empathy", "social", risk = "Zeitaufwand"),
            ap("hard", "Klare Linie", "Grenzen setzen und Fakten nennen.", Attr.WILLPOWER, "interview", "social", risk = "Abbruch des Gesprächs"),
            ap("deal", "Tauschgeschäft", "Etwas anbieten, das die Gegenseite braucht.", Attr.CHARISMA, "negotiation", "social", risk = "Kosten")
        ), "choice", maxTeam = 1),
        Archetype("data", "Datenauswertung", true, listOf("tech", "intel"), listOf(
            Objective("files", "Akten entschlüsseln", "Ein Datensatz aus dem Bereich {region} enthält verschlüsselte Notizen von {faction}.", null, "",
                "Gründlich auswerten" to "Belastbare Ergebnisse, etwas später.", "Schnellbericht liefern" to "Sofort nutzbar, aber mit Lücken."),
            Objective("payments", "Zahlungen verfolgen", "Ungewöhnliche Zahlungen laufen über ein Konto im Bereich {region}. Wer steckt dahinter?", null, "",
                "Zahlungsfluss dokumentieren" to "Ein Beweisstück für die Akten.", "Konto einfrieren lassen" to "{faction} verliert Mittel – und weiß, dass jemand sucht."),
            Objective("photos", "Bildabgleich", "Hunderte Fotos aus dem Bereich {region} müssen mit bekannten Gesichtern von {faction} abgeglichen werden.", null, "",
                "Treffer bestätigen" to "Ein klares Bild der Beteiligten.", "Unbekannte Gesichter melden" to "Neue Spuren, mehr offene Fragen.")
        ), listOf(
            ap("analysis", "Musteranalyse", "Zusammenhänge im Datenmaterial suchen.", Attr.INTELLECT, "analysis", "stealth"),
            ap("systems", "Systemzugriff", "Metadaten und Protokolle auswerten.", Attr.INTELLECT, "systems", "balanced", risk = "Spuren im System"),
            ap("evidence", "Beweissicherung", "Jedes Detail wie für ein Verfahren dokumentieren.", Attr.PERCEPTION, "evidence", "balanced")
        ), "search", maxTeam = 1),
        Archetype("rescue", "Rettung", false, listOf("combat", "medic"), listOf(
            Objective("hostage", "Festgehaltene Person befreien", "{Target} wird im Bereich {region} von {faction} festgehalten.", "extract", "Festgehaltene Person",
                "Sofort zur Versorgung bringen" to "Die Person ist in Sicherheit und dankbar.", "Erst befragen, dann versorgen" to "Wertvolle Hinweise, aber Unmut."),
            Objective("agent", "Agentin zurückholen", "Eine Agentin der Division ist im Bereich {region} aufgeflogen und sitzt fest.", "extract", "Agentin",
                "Legende der Agentin retten" to "Sie kann später weiterarbeiten.", "Alles abbrechen, Hauptsache raus" to "Sicher, aber die Operation ist verloren."),
            Objective("witness", "Zeugin evakuieren", "Eine Zeugin gegen {faction} muss aus dem Bereich {region} gebracht werden, bevor sie gefunden wird.", "extract", "Zeugin",
                "In ein sicheres Haus bringen" to "Die Aussage bleibt verfügbar.", "Ins Programm der Regierungsaufsicht übergeben" to "Rechtlich sauber, aber außerhalb der Kontrolle der Division.")
        ), listOf(
            ap("quiet", "Leiser Zugang", "Über Nebeneingänge vordringen.", Attr.PERCEPTION, "shadowstep", "stealth", tactical = true, risk = "Alarm"),
            ap("tech", "Technik zuerst", "Kameras stören, dann zugreifen.", Attr.INTELLECT, "electronics", "balanced", tactical = true, risk = "Störung fällt auf"),
            ap("talk", "Freilassung verhandeln", "Ein Angebot, das die Gegenseite nicht ablehnt.", Attr.CHARISMA, "negotiation", "social", risk = "Gegenseite spielt auf Zeit")
        ), "choice", maxTeam = 3),
        Archetype("intel", "Informationsbeschaffung", true, listOf("intel", "tech"), listOf(
            Objective("ledger", "Unterlagen sichern", "Im Bereich {region} lagern Unterlagen von {faction}. Sichere Kopien.", "intel", "",
                "Kopien unbemerkt sichern" to "Die Gegenseite ahnt nichts.", "Originale mitnehmen" to "Stärkere Beweise, aber offener Bruch."),
            Objective("drive", "Laufwerk spiegeln", "Ein Rechner im Bereich {region} enthält Kontaktlisten von {faction}.", "intel", "",
                "Nur spiegeln" to "Unbemerkte Langzeitquelle.", "Spiegeln und Hintertür lassen" to "Mehr Daten später – Birkhoff wird es lieben, Percy weniger."),
            Objective("source", "Quelle befragen", "{Target} kennt Abläufe bei {faction}, will aber nur persönlich reden.", null, "",
                "Quelle schützen" to "Die Person bleibt verfügbar.", "Alles sofort aufschreiben" to "Mehr Details, die Quelle ist nervös.")
        ), listOf(
            ap("infiltrate", "Eindringen", "Zu den Infopunkten vordringen.", Attr.PERCEPTION, "blend_in", "stealth", tactical = true, risk = "Wachen"),
            ap("social", "Social Engineering", "Jemanden dazu bringen, die Tür zu öffnen.", Attr.CHARISMA, "persuasion", "social", risk = "Misstrauen"),
            ap("archive", "Archivrecherche", "Öffentliche und interne Quellen kombinieren.", Attr.INTELLECT, "evidence", "balanced")
        ), "search"),
        Archetype("escort", "Eskorte", false, listOf("combat", "social"), listOf(
            Objective("witness", "Zeugen begleiten", "Ein Zeuge muss sicher durch den Bereich {region} zum Treffpunkt der Division.", "escort", "Zeuge",
                "Pünktlich übergeben" to "Die Aussage kann verwertet werden.", "Umweg nehmen, um Verfolger abzuschütteln" to "Später, aber unauffälliger."),
            Objective("defector", "Überläuferin", "Eine Überläuferin von {faction} wechselt die Seiten. Bring sie durch den Bereich {region}.", "escort", "Überläuferin",
                "Versprochenen Schutz einhalten" to "Weitere Überläufer fassen Vertrauen.", "Direkt der Division übergeben" to "Effizient, aber ihr Misstrauen wächst."),
            Objective("courier", "Kurierfahrt", "Ein versiegelter Koffer der Division muss durch den Bereich {region}.", "escort", "Kurier",
                "Koffer ungeöffnet abliefern" to "Vertrauen der Einsatzleitung.", "Inhalt prüfen" to "Wissen ist Macht – aber nicht erlaubt.")
        ), listOf(
            ap("convoy", "Geschlossene Formation", "Eng beieinander bleiben, Gefahren abschirmen.", Attr.WILLPOWER, "planning", "direct", tactical = true, risk = "Offener Kontakt"),
            ap("back", "Nebenwege", "Abseits der Hauptwege, schnell und leise.", Attr.PERCEPTION, "orientation", "stealth", tactical = true, risk = "Unbekanntes Gelände"),
            ap("decoy", "Täuschung", "Eine Attrappe lenkt Verfolger ab.", Attr.CHARISMA, "disguise", "social", risk = "Täuschung fliegt auf")
        ), "choice", maxTeam = 3),
        Archetype("protection", "Schutz", false, listOf("combat", "medic"), listOf(
            Objective("meeting", "Treffen absichern", "Ein vertrauliches Treffen im Bereich {region} darf nicht gestört werden.", "protect", "Gesprächspartnerin",
                "Diskret im Hintergrund" to "Das Treffen bleibt geheim.", "Sichtbare Präsenz" to "Abschreckend, aber Aufmerksamkeit erregend."),
            Objective("witness", "Zeugin bewachen", "Eine Zeugin gegen {faction} wird im Bereich {region} vermutet. Halte die Stellung, bis Verstärkung kommt.", "protect", "Zeugin",
                "Zeugin beruhigen" to "Sie sagt aus.", "Zeugin sofort verlegen" to "Sicherer Ort, aber sie fühlt sich als Spielfigur."),
            Objective("conference", "Konferenz sichern", "{Target} spricht auf einer Konferenz im Bereich {region}. {faction} hat Interesse an einer Störung.", "protect", "Rednerin",
                "Unauffällig eingreifen" to "Keine Schlagzeilen.", "Störer festsetzen lassen" to "Ein klares Signal an {faction}.")
        ), listOf(
            ap("perimeter", "Sicherheitsring", "Zugänge planen und besetzen.", Attr.WILLPOWER, "planning", "balanced", tactical = true, risk = "Lücke im Plan"),
            ap("close", "Personenschutz", "Nah an der Schutzperson bleiben.", Attr.FITNESS, "defense", "direct", tactical = true, risk = "Direkte Konfrontation"),
            ap("calm", "Deeskalation", "Spannungen früh erkennen und auflösen.", Attr.CHARISMA, "empathy", "social", risk = "Nicht jeder lässt sich beruhigen")
        ), "choice", maxTeam = 3),
        Archetype("joint", "Koordinierter Einsatz", false, listOf("combat", "tech", "medic"), listOf(
            Objective("depot", "Depot ausheben", "Ein Lager von {faction} im Bereich {region} soll stillgelegt werden.", "eliminate", "",
                "Beweise sichern" to "Ein Verfahren gegen {faction} wird möglich.", "Lager unbrauchbar machen" to "{faction} verliert Ressourcen."),
            Objective("sync", "Zeitgleicher Zugriff", "Zwei Teams greifen gleichzeitig an zwei Orten zu. Dein Team übernimmt {region}.", "intel", "",
                "Funkdisziplin halten" to "Das andere Team bleibt geschützt.", "Eigenes Ziel priorisieren" to "Bessere Beute, schlechtere Abstimmung."),
            Objective("cell", "Zelle auflösen", "Eine Zelle von {faction} operiert im Bereich {region}.", "eliminate", "",
                "Festnahmen der Aufsicht überlassen" to "Rechtlich sauber.", "Alles der Division übergeben" to "Mehr Einfluss für das Direktorat.")
        ), listOf(
            ap("coordinated", "Koordinierter Zugriff", "Exakt abgestimmt mit dem zweiten Team.", Attr.WILLPOWER, "inspire", "direct", tactical = true, risk = "Abstimmungsfehler"),
            ap("split", "Geteilte Teams", "Mehrere Ziele zugleich.", Attr.INTELLECT, "planning", "balanced", tactical = true, risk = "Unterzahl"),
            ap("overwatch", "Überwachung zuerst", "Erst sichern, dann vorrücken.", Attr.PRECISION, "focus", "stealth", tactical = true, risk = "Zeitverlust")
        ), "choice", maxTeam = 3)
    )
    val archetypeById = archetypes.associateBy { it.id }

    data class Entry(val id: String, val text: String, val alarm: Int = 0, val intel: Int = 0, val cover: Int = 0, val creditFactor: Int = 100)
    val entries = listOf(
        Entry("early", "Der Kontakt hat das Treffen um zwei Stunden vorverlegt.", alarm = 0, intel = -1),
        Entry("rain", "Dauerregen: schlechte Sicht, aber kaum Passanten."),
        Entry("leak", "Es gibt Hinweise, dass die Gegenseite vorgewarnt wurde.", alarm = 1),
        Entry("insider", "Eine Quelle vor Ort wartet auf ein vereinbartes Zeichen.", intel = 1),
        Entry("crowd", "In der Nähe findet eine Großveranstaltung statt – viele Zivilisten.", alarm = 1, cover = 1),
        Entry("night", "Der Einsatz beginnt nach Einbruch der Dunkelheit.", intel = 1),
        Entry("cold", "Die Spur ist schon einige Tage alt.", intel = -1),
        Entry("tail", "Vermutlich wirst du selbst beobachtet.", cover = -1),
        Entry("handover", "Ein anderes Team übergibt dir seine Vorarbeit.", intel = 1),
        Entry("budget", "Das Direktorat hat das Budget für diesen Auftrag gekürzt.", creditFactor = 75)
    )
    val entryById = entries.associateBy { it.id }

    data class Complication(val id: String, val label: String, val text: String)
    val complications = listOf(
        Complication("none", "Keine", "Keine besonderen Auflagen."),
        Complication("no_witnesses", "Keine Zeugen", "Die Division darf nicht in Erscheinung treten: Alarmstufe wiegt doppelt."),
        Complication("time", "Zeitdruck", "Das Zeitfenster ist knapp: weniger Runden und Suchaktionen."),
        Complication("civilians", "Zivilisten vor Ort", "Unbeteiligte in der Nähe: gegnerische Präsenz geringer, aber jeder Fehler fällt auf."),
        Complication("warned", "Gegner gewarnt", "Die Gegenseite ist vorbereitet: zusätzliche Sicherung."),
        Complication("team_only", "Nur mit Team", "Die Einsatzleitung schickt niemanden allein: mindestens ein Teammitglied."),
        Complication("no_radio", "Funkstille", "Keine Abstimmung per Funk: Teambonus halbiert."),
        Complication("double_agent", "Doppelagent", "Mindestens eine Quelle spielt ein doppeltes Spiel – Widersprüche sind möglich.")
    )
    val complicationById = complications.associateBy { it.id }

    data class Client(val id: String, val name: String, val faction: String, val npc: String?)
    val clients = listOf(
        Client("ops", "Einsatzleitung", "division", "michael"),
        Client("tech", "Technikabteilung", "division", "birkhoff"),
        Client("psych", "Psychologische Abteilung", "division", "amanda"),
        Client("directorate", "Direktorat", "division", "percy"),
        Client("oversight", "Regierungsaufsicht", "government", null),
        Client("network", "Informantennetz", "civilian", null),
        Client("rogue", "Abtrünnige Kontakte", "independent", "nikita"),
        Client("alex", "Persönliche Bitte", "division", "alex")
    )
    val clientById = clients.associateBy { it.id }

    /** Zielpersonen im Nominativ (Satzanfang wird über {Target} großgeschrieben). */
    private val targets = listOf("ein Kurier", "eine Buchhalterin", "ein Zollbeamter", "eine Journalistin", "ein Ex-Agent", "ein IT-Dienstleister", "eine Übersetzerin", "ein Spediteur", "eine Laborleiterin")
    private val codewords = listOf("Nachtfalke", "Graufeld", "Eisvogel", "Kupferdraht", "Leuchtturm", "Scherbe", "Morgenrot", "Sandsturm", "Glasherz", "Kranich", "Nordlicht", "Basalt", "Fernglas", "Seidenfaden", "Ankerplatz", "Schiefer", "Mondsichel", "Rauchzeichen")
    val regions = listOf("harbor", "airport", "hospital", "embassy", "safehouse", "city")

    fun regionLabel(data: GameData, id: String) = data.placeById[id]?.name ?: id

    fun factionName(data: GameData, id: String) = data.factionById[id]?.name ?: id

    // --- Fraktionen ------------------------------------------------------------------

    private val baseFactions = mapOf(
        "syndicate" to FactionState(influence = 45, resources = 60),
        "phoenix" to FactionState(influence = 25, resources = 70),
        "independent" to FactionState(influence = 20, resources = 30),
        "government" to FactionState(influence = 55, resources = 65),
        "civilian" to FactionState(influence = 40, resources = 25),
        "division" to FactionState(influence = 50, resources = 55)
    )

    fun faction(state: GameState, id: String): FactionState = state.ops.factions[id] ?: baseFactions[id] ?: FactionState()

    fun attitude(state: GameState, id: String): Int = state.factionStanding[id] ?: when (id) {
        "syndicate" -> -30; "phoenix" -> -10; "government" -> 15; else -> 0
    }

    fun attitudeLabel(v: Int) = when {
        v >= 40 -> "Verbündet"; v >= 15 -> "Wohlwollend"; v > -15 -> "Neutral"; v > -40 -> "Misstrauisch"; else -> "Feindselig"
    }

    private fun putFaction(state: GameState, id: String, f: (FactionState) -> FactionState): GameState {
        val nf = f(faction(state, id)).let { it.copy(influence = it.influence.coerceIn(0, 100), resources = it.resources.coerceIn(0, 100)) }
        return state.copy(ops = state.ops.copy(factions = state.ops.factions + (id to nf)))
    }

    fun shiftInfluence(state: GameState, id: String, delta: Int, resources: Int = 0) = putFaction(state, id) { it.copy(influence = it.influence + delta, resources = it.resources + resources) }

    fun shiftAttitude(state: GameState, id: String, delta: Int): GameState =
        state.copy(factionStanding = state.factionStanding + (id to (attitude(state, id) + delta).coerceIn(-100, 100)))

    fun worldLog(state: GameState, line: String): GameState =
        state.copy(ops = state.ops.copy(worldLog = (state.ops.worldLog + "${Clock.dateLabel(state.clock.day)}: $line").takeLast(WORLD_LOG_CAP)))

    /** Wöchentliche Weltlage: Einfluss verschiebt sich, Konfliktbögen schreiten fort. */
    fun weeklyFactions(data: GameData, state: GameState): GameState {
        var s = state
        // Black Meridian wächst, wenn niemand gegenhält – bis zu einem Gleichgewicht
        s = shiftInfluence(s, "syndicate", if (faction(s, "syndicate").influence < 60) 1 else -1)
        if ("phoenix_truth" in s.flags) s = shiftInfluence(s, "phoenix", if (faction(s, "phoenix").influence < 45) 1 else -1)
        // Übrige Organisationen pendeln langsam zu ihrem Grundniveau zurück
        listOf("independent", "government", "civilian", "division").forEach { id ->
            val base = baseFactions.getValue(id).influence
            val cur = faction(s, id).influence
            if (cur != base && s.clock.day % 14 == 0) s = shiftInfluence(s, id, if (cur < base) 1 else -1)
        }
        val bm = faction(s, "syndicate")
        if (bm.influence >= 70 && bm.arc == 0) {
            s = putFaction(s, "syndicate") { it.copy(arc = 1, lastEvent = "Gegenoffensive") }
            s = worldLog(s, "Black Meridian startet eine Gegenoffensive – mehr dringende Aufträge.")
        } else if (bm.influence <= 25 && bm.arc >= 1) {
            s = putFaction(s, "syndicate") { it.copy(arc = 2, lastEvent = "Rückzug") }
            s = worldLog(s, "Black Meridian zieht sich aus mehreren Vierteln zurück.")
        }
        val gov = attitude(s, "government")
        val govState = faction(s, "government")
        if (gov <= -30 && govState.arc == 0) {
            s = putFaction(s, "government") { it.copy(arc = 1, lastEvent = "Budgetkürzung") }
            s = worldLog(s, "Die Regierungsaufsicht kürzt das Budget der Division – Belohnungen sinken.")
        } else if (gov > -10 && govState.arc == 1) {
            s = putFaction(s, "government") { it.copy(arc = 0, lastEvent = "Budget wiederhergestellt") }
            s = worldLog(s, "Die Regierungsaufsicht stellt das alte Budget wieder her.")
        }
        val ind = attitude(s, "independent")
        if (ind >= 30 && faction(s, "independent").arc == 0) {
            s = putFaction(s, "independent") { it.copy(arc = 1, lastEvent = "Vertrauen") }
            s = worldLog(s, "Abtrünnige Agenten bieten über Mittelsleute Zusammenarbeit an.")
        }
        return s
    }

    private fun creditFactor(state: GameState): Int = if (faction(state, "government").arc == 1) 80 else 100

    // --- Verfügbarkeit von Figuren ------------------------------------------------------

    /** Ist die Figur gerade plausibel im HQ verfügbar (nicht im Einsatz, verletzt, im Urlaub …)? */
    fun available(data: GameData, state: GameState, id: String): Boolean {
        val st = state.living.lives[id]?.status ?: LifeStatus.DUTY
        return st in setOf(LifeStatus.DUTY, LifeStatus.TRAINING, LifeStatus.FREE)
    }

    fun unavailableReason(data: GameData, state: GameState, id: String): String? {
        val l = state.living.lives[id] ?: return null
        return when (l.status) {
            LifeStatus.MISSION -> "im Einsatz"
            LifeStatus.INJURED -> "verletzt"
            LifeStatus.VACATION -> "im Urlaub"
            LifeStatus.ABSENT -> "abwesend"
            LifeStatus.TRANSFERRED -> "versetzt"
            else -> null
        }
    }

    private fun clientAvailable(data: GameData, state: GameState, c: Client): Boolean {
        val npc = c.npc ?: return when (c.id) {
            "oversight" -> attitude(state, "government") > -50
            else -> true
        }
        val met = "met_$npc" in state.flags || state.relations.containsKey(npc)
        return when (npc) {
            "nikita" -> "nikita_contact" in state.flags && state.living.lives[npc]?.status != LifeStatus.TRANSFERRED
            "alex" -> met && Relations.get(data, state, npc).friendship >= 40 && available(data, state, npc)
            else -> met && available(data, state, npc)
        }
    }

    // --- Generierung -----------------------------------------------------------------------

    fun isContract(id: String) = id.startsWith(PREFIX)
    fun contract(state: GameState, id: String) = state.ops.contracts.firstOrNull { it.id == id }

    /** Schreibt Pool, Abläufe und Ketten bis heute fort. Idempotent pro Tag. */
    fun refresh(data: GameData, state: GameState): GameState {
        if (state.phase != "game") return state
        val today = state.clock.day
        if (state.ops.lastGenDay >= today) return state
        var s = expire(data, state, today)
        val from = maxOf(s.ops.lastGenDay + 1, today - 6)
        for (day in from..today) s = generateDay(data, s, day)
        s = Chains.offer(data, s)
        return s.copy(ops = s.ops.copy(lastGenDay = today))
    }

    private fun generateDay(data: GameData, state: GameState, day: Int): GameState {
        var s = state
        val rng = Rng(Rng.mix(s.seed, day.toLong(), 7707))
        val open = s.ops.contracts.count { !ContractStatus.closed(it.status) && it.chainId == null }
        val first = s.ops.lastGenDay < 0 && s.ops.contracts.isEmpty()
        val surge = if (faction(s, "syndicate").arc == 1) 1 else 0
        val n = if (first) 4 else when (rng.percent()) { in 1..45 -> 0; in 46..85 -> 1; else -> 2 } + surge
        repeat(n) { i ->
            if (open + i >= POOL_MAX) return@repeat
            val c = generate(data, s, day, s.ops.counter, Rng.mix(s.seed, day.toLong(), i.toLong(), 9901))
            if (c != null) s = s.copy(ops = s.ops.copy(contracts = s.ops.contracts + c, counter = s.ops.counter + 1))
        }
        return s
    }

    /** Erzeugt einen Auftrag deterministisch aus dem Seed und dem Spielzustand. */
    fun generate(data: GameData, state: GameState, day: Int, number: Int, seed: Long, forced: Archetype? = null): Contract? {
        val rng = Rng(seed)
        val adult = Ages.player(state) >= 18
        val pool = archetypes.filter { adult || it.minorSafe }
        val arch = forced ?: rng.pick(pool)
        val minor = !adult
        val approaches = arch.approaches.filter { !minor || !it.tactical }
        if (approaches.size < 2) return null
        val objectives = arch.objectives.filter { !minor || it.tactical == null || approaches.any { a -> !a.tactical } }
        val obj = rng.pick(objectives)
        val region = rng.pick(regions)
        val clientPool = clients.filter { clientAvailable(data, state, it) }.ifEmpty { listOf(clientById.getValue("ops")) }
        val client = rng.pick(clientPool)
        val targetPool = buildList {
            add("syndicate"); add("syndicate")
            add("independent")
            if ("phoenix_truth" in state.flags) add("phoenix")
            if (client.faction == "division" && client.id == "directorate") add("government")
        }.filter { it != client.faction }
        val target = rng.pick(targetPool)
        val entry = rng.pick(entries)
        val comp = rng.pick(complications.filter { c -> !(minor && c.id == "warned") && !(c.id == "team_only" && arch.maxTeam < 2) })
        val bm = faction(state, "syndicate")
        val base = if (minor) rng.range(1, 2) else (state.player.rank / 2 + rng.range(0, 2) + (if (target == "syndicate" && bm.arc == 1) 1 else 0)).coerceIn(1, 5)
        val difficulty = (if (minor) base.coerceAtMost(2) else base) - (if (Sites.siteLevel(data, state, "analysis") >= 3 && base > 1) 1 else 0)
        val urgency = when (rng.percent()) { in 1..55 -> 1; in 56..88 -> 2; else -> 3 } + (if (target == "syndicate" && bm.arc == 1) 1 else 0)
        val phases = if (difficulty >= 3 && arch.id in setOf("observation", "undercover", "joint", "recon") && rng.percent() <= 45) 2 else 1
        val used = state.ops.contracts.map { it.title.substringAfter("„").substringBefore("“") }.toSet()
        val code = rng.pick(codewords.filter { it !in used }.ifEmpty { codewords })
        val who = rng.pick(targets)
        val fill = { t: String -> t.replace("{Target}", who.replaceFirstChar { it.uppercase() }).replace("{target}", who).replace("{region}", regionLabel(data, region)).replace("{faction}", factionName(data, target)) }
        val map = TacticalMaps.forRegion(region).let { rng.pick(it) }
        val usesTactical = obj.tactical != null && approaches.any { it.tactical }
        val factor = creditFactor(state) * entry.creditFactor / 100
        val credits = ((60 + difficulty * 90) * (100 + (urgency - 1) * 20) / 100) * factor / 100
        val clientName = client.npc?.let { Relations.displayName(data, state, it) } ?: client.name
        val briefing = buildString {
            append(fill(obj.text)); append(" ")
            append(entry.text); append(" ")
            if (comp.id != "none") append("Auflage: ${comp.text} ")
            append("Auftraggeber: $clientName.")
        }
        val risks = buildList {
            add("Gefahrenstufe $difficulty/5")
            approaches.forEach { if (it.risk.isNotBlank()) add("${it.name}: ${it.risk}") }
            if (usesTactical) add("Taktischer Kontakt möglich (${map.name})")
            if (comp.id != "none") add(comp.label)
        }
        val items = when (arch.id) {
            "data" -> listOf("usb", "microchip"); "intel" -> listOf("research_drive", "usb"); "rescue" -> listOf("medkit", "bandage")
            "observation" -> listOf("binoculars", "battery"); "joint" -> listOf("radio_secure", "vest_light"); else -> listOf("battery", "notebook")
        }
        val rewardItems = if (rng.percent() <= 35) mapOf(rng.pick(items) to 1) else emptyMap()
        return Contract(
            id = "$PREFIX${day}-${number}", archetype = arch.id, regionId = region, clientId = client.id, clientNpc = client.npc,
            targetFaction = target, objectiveId = obj.id, entryId = entry.id, complicationId = comp.id,
            title = "${arch.label}: „$code“", briefing = briefing, approaches = approaches, risks = risks,
            difficulty = difficulty, urgency = urgency.coerceIn(1, 3), postedDay = day,
            expiresDay = day + when (urgency.coerceIn(1, 3)) { 3 -> 2; 2 -> 4; else -> 7 } + phases,
            phases = phases, minAge = if (arch.minorSafe && !usesTactical) 16 else if (minor) 16 else 18,
            maxTeam = if (minor) 1 else arch.maxTeam, credits = credits, xp = 80 + difficulty * 60, reputation = 2 + difficulty * 2,
            rewardItems = rewardItems, mapId = map.id, seed = seed, skills = arch.skills,
            successConsequence = "${factionName(data, target)} verliert Einfluss. ${fill(obj.closeA.second)}",
            failConsequence = "${factionName(data, target)} gewinnt Einfluss, ${clientName} ist enttäuscht."
        )
    }

    // --- Ablauf, Vertagen, Ablehnen ------------------------------------------------------

    private fun close(data: GameData, state: GameState, c: Contract, status: String, notes: List<String>): GameState {
        val rec = ContractRecord(c.id, c.title, c.archetype, status, state.clock.day, c.targetFaction, notes)
        return state.copy(ops = state.ops.copy(
            contracts = state.ops.contracts.filter { it.id != c.id },
            history = (state.ops.history + rec).takeLast(HISTORY_CAP)
        ))
    }

    fun expire(data: GameData, state: GameState, today: Int): GameState {
        var s = state
        s.ops.contracts.filter { !ContractStatus.closed(it.status) && it.status != ContractStatus.DELEGATED && it.expiresDay < today && s.activeMission?.missionId != it.id }.forEach { c ->
            val running = c.status == ContractStatus.IN_PROGRESS
            s = shiftInfluence(s, c.targetFaction, if (running) 4 else 3)
            s = shiftAttitude(s, clientById[c.clientId]?.faction ?: "division", -2)
            c.clientNpc?.let { npc -> if (Relations.isKnownNpc(data, s, npc)) s = Relations.update(data, s, npc) { it.copy(trust = it.trust - 2) } }
            val line = if (running) "„${c.title}“ wurde nicht fortgesetzt und ist gescheitert – ${factionName(data, c.targetFaction)} nutzt die Lücke."
                else "„${c.title}“ ist verstrichen – ${factionName(data, c.targetFaction)} nutzt die Lücke."
            s = worldLog(s, line)
            s = close(data, s, c, if (running) ContractStatus.FAILED else ContractStatus.EXPIRED, listOf(line))
            if (c.chainId != null) s = Chains.cooldown(s, c.chainId)
        }
        return s
    }

    fun postpone(data: GameData, state: GameState, id: String): Result {
        val c = contract(state, id) ?: return state.fail("Auftrag unbekannt")
        if (c.status != ContractStatus.OPEN) return state.fail("Nur offene Aufträge können vertagt werden.")
        if (c.postponed >= 1) return state.fail("Bereits einmal vertagt.")
        if (c.urgency >= 3) return state.fail("Kritische Aufträge lassen sich nicht vertagen.")
        val nc = c.copy(status = ContractStatus.POSTPONED, postponed = 1, expiresDay = c.expiresDay + 3, credits = c.credits * 80 / 100)
        val s = state.copy(ops = state.ops.copy(contracts = state.ops.contracts.map { if (it.id == id) nc else it }))
        return Result(s, listOf("„${c.title}“ vertagt: 3 Tage mehr Zeit, Belohnung −20 %."))
    }

    fun decline(data: GameData, state: GameState, id: String): Result {
        val c = contract(state, id) ?: return state.fail("Auftrag unbekannt")
        if (c.status == ContractStatus.IN_PROGRESS) return state.fail("Ein laufender Auftrag kann nur im Einsatz abgebrochen werden.")
        var s = state
        val notes = mutableListOf("Abgelehnt.")
        if (c.urgency >= 3) {
            s = shiftAttitude(s, clientById[c.clientId]?.faction ?: "division", -1)
            notes += "Der Auftraggeber hatte auf dich gezählt."
        }
        if (c.chainId != null) s = Chains.cooldown(s, c.chainId)
        s = close(data, s, c, ContractStatus.DECLINED, notes)
        return Result(s, listOf("„${c.title}“ abgelehnt."))
    }

    // --- Umwandlung in einen spielbaren Einsatz --------------------------------------------

    fun definitionFor(data: GameData, state: GameState, id: String): MissionDefinition? = contract(state, id)?.let { toMission(data, state, it) }

    fun lockReason(data: GameData, state: GameState, c: Contract): String? {
        if (ContractStatus.closed(c.status)) return ContractStatus.label(c.status)
        if (c.status == ContractStatus.DELEGATED) return "An ein Team delegiert"
        if (c.status == ContractStatus.IN_PROGRESS && state.clock.day < c.nextPhaseDay) return "Nächste Phase ab ${Clock.dateLabel(c.nextPhaseDay)}"
        return null
    }

    fun toMission(data: GameData, state: GameState, c: Contract): MissionDefinition {
        val arch = archetypeById.getValue(c.archetype)
        val obj = arch.objectives.firstOrNull { it.id == c.objectiveId } ?: arch.objectives.first()
        val entry = entryById[c.entryId] ?: entries.first()
        val d = c.difficulty
        val region = regionLabel(data, c.regionId)
        val target = factionName(data, c.targetFaction)
        val chainStep = c.chainId?.let { Chains.step(data, it, c.chainStep) }
        val time = c.complicationId == "time"
        val stages = mutableListOf<StageDefinition>()
        val lastPhase = c.phase >= c.phases - 1
        if (c.phase == 0) {
            stages += StageDefinition("brief", "narrative", "Lage", c.briefing + if (c.phases > 1) " Dieser Auftrag läuft über ${c.phases} Einsatztage." else "")
            stages += StageDefinition("approach", "choice", "Vorgehen", "Wie gehst du vor? (${entry.text})", options = c.approaches.map { a ->
                StageOption(
                    a.id, a.name + (if (a.tactical) " ⚔" else ""), CheckDef(a.attribute, a.skill, 30 + d * 6),
                    success = Outcome("${a.description} Es läuft nach Plan.", next = if (a.tactical && obj.tactical != null) "tac" else "quiet", intel = 1 + entry.intel.coerceAtLeast(0), cover = entry.cover.coerceAtMost(0)),
                    failure = Outcome("${a.risk.ifBlank { "Ein Fehler" }} – die Lage wird schwieriger.", next = if (a.tactical && obj.tactical != null) "tac" else "quiet", alarm = 1 + entry.alarm, cover = entry.cover.coerceAtMost(0) - (if (a.style == "social") 1 else 0))
                )
            })
            stages += quietStage(data, state, c, arch, obj, region, target, time, next = if (lastPhase) "close" else Missions.END_SUCCESS)
            if (obj.tactical != null) stages += tacticalStage(data, c, obj, time, next = if (lastPhase) "close" else Missions.END_SUCCESS)
        } else {
            stages += StageDefinition("brief", "narrative", "Tag ${c.phase + 1}", "Fortsetzung von „${c.title}“. Die Vorarbeit des Vortags zahlt sich aus: ${c.carriedIntel} gesicherte Erkenntnisse. Heute wird die Sache in $region zu Ende gebracht.")
            stages += StageDefinition("approach", "choice", "Lage am zweiten Tag", "Über Nacht hat sich etwas verändert. Wie reagierst du?", options = listOf(
                StageOption("adapt", "Plan anpassen", CheckDef(Attr.INTELLECT, "planning", 30 + d * 6), success = Outcome("Der neue Plan sitzt.", next = if (obj.tactical != null && c.approaches.any { it.tactical } && Ages.player(state) >= 18) "tac" else "quiet", intel = 1),
                    failure = Outcome("Improvisation statt Plan.", next = if (obj.tactical != null && c.approaches.any { it.tactical } && Ages.player(state) >= 18) "tac" else "quiet", alarm = 1)),
                StageOption("wait", "Abwarten und beobachten", CheckDef(Attr.PERCEPTION, "observe", 28 + d * 6), success = Outcome("Geduld lohnt sich.", next = "quiet", intel = 1), failure = Outcome("Zeit verloren.", next = "quiet", stress = 5))
            ))
            stages += quietStage(data, state, c, arch, obj, region, target, time, next = "close")
            if (obj.tactical != null) stages += tacticalStage(data, c, obj, time, next = "close")
        }
        if (lastPhase) stages += closeStage(c, obj, chainStep, target)
        // v1.0: Missionsdirektor 3.0 – deterministische Wendung zwischen Vorgehen und Durchführung
        val withTwist = Twists.apply(stages.toList(), c)
        stages.clear(); stages += withTwist
        val title = if (c.phases > 1) "${c.title} · Tag ${c.phase + 1}/${c.phases}" else c.title
        return MissionDefinition(
            id = c.id, title = title, locationId = c.regionId, type = typeFor(arch.id), minRank = 1, minAge = c.minAge,
            threat = d, xp = if (lastPhase) c.xp else c.xp / 3, description = c.briefing, factionId = c.targetFaction,
            briefing = listOfNotNull(
                when (c.urgency) { 3 -> "Kritisch"; 2 -> "Dringend"; else -> "Routine" },
                "Frist ${Clock.dateLabel(c.expiresDay)}",
                complicationById[c.complicationId]?.takeIf { it.id != "none" }?.label
            ).joinToString(" · "),
            giverId = c.clientNpc?.takeIf { Relations.isKnownNpc(data, state, it) }, credits = if (lastPhase) c.credits else 0,
            reputation = if (lastPhase) c.reputation else 0, rewardItems = if (lastPhase) c.rewardItems else emptyMap(),
            stages = stages, witnesses = if (entry.id == "crowd" || c.complicationId == "civilians") 2 else 0, risk = d, repeatable = false,
            maxTeam = c.maxTeam, recommendedItems = when (arch.id) {
                "observation", "recon" -> listOf("binoculars", "camera"); "data" -> listOf("laptop", "usb"); "intel" -> listOf("smartphone", "usb")
                "undercover" -> listOf("passport", "gear_cover_papers"); "rescue", "protection" -> listOf("medkit", "vest_light"); "escort", "joint" -> listOf("radio_secure", "vest_light")
                "negotiation" -> listOf("gear_earpiece", "notebook")
                else -> listOf("notebook")
            },
            debriefSuccess = c.successConsequence, debriefFail = c.failConsequence
        )
    }

    fun typeFor(arch: String) = when (arch) {
        "observation", "recon" -> "observation"; "undercover" -> "undercover"; "negotiation" -> "dialogue"; "data", "intel" -> "investigation"
        "rescue", "escort", "protection" -> "rescue"; else -> "tactical"
    }

    private val spotsByRegion = mapOf(
        "harbor" to listOf("Container 14", "Kranführerhaus", "Frachtliste", "Hafenmeisterei", "Lagerhalle C", "Laderampe", "Zollbox", "Spinde", "Pausenraum"),
        "airport" to listOf("Gepäckband 3", "Mietwagenschalter", "Lounge", "Schließfächer", "Überwachungsraum", "Café-Quittungen", "Parkdeck", "Fundbüro", "Frachtbüro"),
        "hospital" to listOf("Patientenaufnahme", "Schwesternzimmer", "Besucherliste", "Kapelle", "Wäscherei", "Notaufnahme", "Aufzug B", "Kiosk", "Archivraum"),
        "embassy" to listOf("Empfang", "Garderobe", "Arbeitszimmer", "Bibliothek", "Wintergarten", "Fahrzeugpool", "Sicherheitsbüro", "Konferenzraum", "Poststelle"),
        "safehouse" to listOf("Küche", "Lüftungsschacht", "Briefkasten", "Kühlschrank", "Papierkorb", "Dielenbrett", "Badezimmer", "Fensterbank", "Kleiderschrank"),
        "city" to listOf("Kiosk", "Telefonzelle", "Café-Terrasse", "U-Bahn-Ausgang", "Parkbank", "Litfaßsäule", "Fahrradständer", "Bäckerei", "Hauseingang")
    )

    private val observeTargets = mapOf(
        "observation" to listOf("Lieferwagen", "Frau am Telefon", "Mann mit Hund", "Parkwächter"),
        "undercover" to listOf("Barkeeper", "Gast mit Aktentasche", "Sicherheitschef", "Fahrer"),
        "escort" to listOf("Taxi an der Ecke", "Radfahrer", "Paar am Fenster", "Straßenmusiker"),
        "protection" to listOf("Techniker", "Pressefotograf", "Kellner", "Besucher in Grau")
    )

    private fun quietStage(data: GameData, state: GameState, c: Contract, arch: Archetype, obj: Objective, region: String, target: String, time: Boolean, next: String): StageDefinition {
        val d = c.difficulty
        val rng = Rng(Rng.mix(c.seed, c.phase.toLong(), 31))
        return when (arch.quietStage) {
            "search" -> StageDefinition("quiet", "search", "Spuren sichern", "Im Bereich $region liegen Hinweise auf $target verstreut. Finde ${2 + d / 2} davon.",
                spots = rng.shuffled(spotsByRegion[c.regionId] ?: spotsByRegion.getValue("city")).take(9), cluesNeeded = 2 + d / 2,
                actions = (6 + (state.player.skills["evidence"] ?: 0) - (if (time) 2 else 0)).coerceAtLeast(4), next = next)
            "observe" -> StageDefinition("quiet", "observe", "Beobachten", "Wer ist Teil der Übergabe im Bereich $region?",
                targets = observeTargets[arch.id] ?: observeTargets.getValue("observation"), rounds = 3 + (if (d >= 3) 1 else 0), intelNeeded = 2 + d / 3, next = next)
            else -> StageDefinition("quiet", "choice", obj.label, "Der entscheidende Moment: Es kommt auf dein Geschick an.", options = listOf(
                StageOption("steady", "Ruhig bleiben und lesen, was passiert", CheckDef(Attr.PERCEPTION, "lie_detector", 30 + d * 6), success = Outcome("Du erkennst rechtzeitig, worauf es ankommt.", next = next, intel = 1), failure = Outcome("Ein Detail entgeht dir.", next = next, alarm = 1)),
                StageOption("push", "Initiative ergreifen", CheckDef(Attr.WILLPOWER, "inspire", 32 + d * 6), success = Outcome("Die anderen folgen deinem Tempo.", next = next, intel = 1), failure = Outcome("Zu schnell – jemand wird misstrauisch.", next = next, alarm = 1, stress = 5)),
                StageOption("talk", "Mit Worten lösen", CheckDef(Attr.CHARISMA, "persuasion", 30 + d * 6), success = Outcome("Ein paar Sätze entschärfen die Lage.", next = next, cover = 1), failure = Outcome("Die Worte verfangen nicht.", next = next, cover = -1))
            ))
        }
    }

    private fun tacticalStage(data: GameData, c: Contract, obj: Objective, time: Boolean, next: String): StageDefinition {
        val enemy = when (c.targetFaction) {
            "syndicate" -> "Meridian-Sicherung"; "phoenix" -> "PHOENIX-Wache"; "independent" -> "Abtrünniger"; "government" -> "Aufsichtsteam"; else -> "Wache"
        }
        val map = TacticalMaps.byId[c.mapId] ?: TacticalMaps.all.first()
        return StageDefinition("tac", "tactical", "Einsatzbrett: ${map.name}", Tactical.objectiveLabels[obj.tactical] ?: "",
            tactical = TacticalSetup(
                map = emptyList(), mapId = map.id, enemyName = enemy, enemyHp = 5 + c.difficulty / 2,
                enemyAccuracy = 55 - (if (c.complicationId == "civilians") 5 else 0),
                objective = obj.tactical ?: "eliminate", maxTurns = (13 - (if (time) 3 else 0)).coerceAtLeast(8),
                vipName = obj.vip, requiredSecured = 2, holdTurns = 3 + (if (c.difficulty >= 4) 1 else 0)
            ), next = next)
    }

    private fun closeStage(c: Contract, obj: Objective, step: ChainStep?, target: String): StageDefinition {
        if (step != null && step.decisions.isNotEmpty()) {
            return StageDefinition("close", "choice", step.decisionTitle.ifBlank { "Entscheidung" }, step.decisionText, options = step.decisions.map { dec ->
                StageOption("dec_${dec.id}", dec.text, success = Outcome(dec.result, next = Missions.END_SUCCESS,
                    effects = Effects(setFlags = listOf(Chains.decisionFlag(c.chainId!!, c.chainStep, dec.id)))))
            })
        }
        val (aText, aRes) = obj.closeA; val (bText, bRes) = obj.closeB
        return StageDefinition("close", "choice", "Abschluss", "Wie schließt du den Auftrag ab? Die Entscheidung hat Folgen für $target und künftige Aufträge.", options = listOf(
            StageOption("close_a", aText, success = Outcome(aRes.replace("{faction}", target), next = Missions.END_SUCCESS, cover = 1,
                effects = Effects(setFlags = listOf("op_close_a")))),
            StageOption("close_b", bText, success = Outcome(bRes.replace("{faction}", target), next = Missions.END_SUCCESS, intel = 1, alarm = 1,
                effects = Effects(setFlags = listOf("op_close_b"))))
        ))
    }

    // --- Teamauswahl ----------------------------------------------------------------------

    /** Passung einer Figur zum Auftrag (0..10) anhand der Kompetenzen aus der Lebenssimulation. */
    fun fit(data: GameData, state: GameState, c: Contract, npcId: String): Int =
        if (c.skills.isEmpty()) 0 else c.skills.maxOf { Life.competence(data, state, npcId, it) }

    // --- Abschluss -------------------------------------------------------------------------

    /**
     * Wird von [Missions.finish] aufgerufen. Verbucht Konsequenzen genau einmal
     * (über [OpsState.settled]); Story-Missionen erhalten nur die Teamfolgen.
     */
    fun afterMission(data: GameData, state: GameState, m: MissionDefinition, run: MissionRun, result: String): Pair<GameState, List<String>> {
        var s = state
        val notes = mutableListOf<String>()
        val settleKey = "${m.id}#${run.seed}"
        if (settleKey in s.ops.settled) return s to notes
        s = s.copy(ops = s.ops.copy(settled = (s.ops.settled + settleKey).let { if (it.size > 200) it.drop(it.size - 200).toSet() else it }))
        // Teamfolgen für alle Einsätze: Bilanz, Verletzungen, Erinnerungen
        val downed = run.tactical?.units?.filter { it.ally && it.down && !it.isPlayer }?.map { it.id }?.toSet() ?: emptySet()
        run.team.forEach { id ->
            val l = Life.life(data, s, id)
            var nl = if (result == "success") l.copy(missions = l.missions + 1, careerXp = l.careerXp + 15 + m.threat * 5, mood = (l.mood + 5).coerceIn(0, 100))
                else if (result == "fail") l.copy(missionsFailed = l.missionsFailed + 1, careerXp = l.careerXp + 5, mood = (l.mood - 8).coerceIn(0, 100)) else l
            nl = if (id in downed) {
                notes += "${Relations.displayName(data, s, id).substringBefore(" ")} ist verletzt und fällt einige Tage aus."
                nl.copy(status = LifeStatus.INJURED, statusUntil = s.clock.day + 3 + m.threat, statusNote = "Einsatzverletzung", injuries = nl.injuries + 1)
            } else if (nl.status == LifeStatus.MISSION && nl.statusNote.startsWith("Einsatz mit")) nl.copy(status = LifeStatus.DUTY, statusUntil = -1, statusNote = "") else nl
            s = s.copy(living = s.living.copy(lives = s.living.lives + (id to nl)))
        }
        val c = contract(s, m.id) ?: return s to notes
        s = Command.recordMission(s, run.team, result == "success" && c.phase >= c.phases - 1)
        val (ns, n2) = settleContract(data, s, c, result, run)
        return ns to (notes + n2)
    }

    /**
     * Verbucht die Konsequenzen eines Auftrags (eigener Einsatz oder delegiert).
     * Wird pro Abschluss genau einmal aufgerufen; der Auftrag verlässt danach den Pool.
     */
    fun settleContract(data: GameData, state: GameState, c: Contract, result: String, run0: MissionRun?): Pair<GameState, List<String>> {
        var s = state
        val notes = mutableListOf<String>()
        val run = run0 ?: MissionRun(c.id, c.seed, "settle")
        val clientFaction = clientById[c.clientId]?.faction ?: "division"
        val lastPhase = c.phase >= c.phases - 1
        when {
            result == "success" && !lastPhase -> {
                val nc = c.copy(status = ContractStatus.IN_PROGRESS, phase = c.phase + 1, nextPhaseDay = s.clock.day + 1,
                    carriedIntel = c.carriedIntel + run.intel, expiresDay = maxOf(c.expiresDay, s.clock.day + 3))
                s = s.copy(ops = s.ops.copy(contracts = s.ops.contracts.map { if (it.id == c.id) nc else it }))
                notes += "Phase ${c.phase + 1}/${c.phases} abgeschlossen – Fortsetzung ab ${Clock.dateLabel(nc.nextPhaseDay)}, spätestens ${Clock.dateLabel(nc.expiresDay)}."
            }
            result == "success" -> {
                val closeB = "op_close_b" in s.flags
                val swing = 4 + c.difficulty * 2
                s = shiftInfluence(s, c.targetFaction, -(swing + if (closeB) 2 else 0), resources = -3)
                s = shiftAttitude(s, clientFaction, if (closeB) 2 else 3)
                if (run.alarm >= 3 || closeB) s = shiftAttitude(s, c.targetFaction, -3)
                c.clientNpc?.let { npc -> if (Relations.isKnownNpc(data, s, npc)) s = Relations.update(data, s, npc) { it.copy(trust = it.trust + 3, respect = it.respect + 2, memories = it.memories + "Auftrag für mich erledigt: ${c.title}") } }
                notes += "Weltlage: ${factionName(data, c.targetFaction)} Einfluss −${swing + if (closeB) 2 else 0} · ${factionName(data, clientFaction)} Haltung +${if (closeB) 2 else 3}"
                s = worldLog(s, "„${c.title}“ erfüllt – ${factionName(data, c.targetFaction)} verliert Boden im Bereich ${regionLabel(data, c.regionId)}.")
                s = Cases.onContractSuccess(data, s, c, run).let { (st, n) -> notes += n; st }
                s = Chains.onStepDone(data, s, c).let { (st, n) -> notes += n; st }
                s = followUp(data, s, c, closeB)
                s = close(data, s, c, ContractStatus.DONE, notes.toList())
            }
            result == "fail" -> {
                s = shiftInfluence(s, c.targetFaction, 5)
                s = shiftAttitude(s, clientFaction, -3)
                c.clientNpc?.let { npc -> if (Relations.isKnownNpc(data, s, npc)) s = Relations.update(data, s, npc) { it.copy(trust = it.trust - 2) } }
                notes += "Weltlage: ${factionName(data, c.targetFaction)} Einfluss +5 · ${factionName(data, clientFaction)} Haltung −3"
                s = worldLog(s, "„${c.title}“ gescheitert – ${factionName(data, c.targetFaction)} gewinnt an Boden.")
                if (c.chainId != null) s = Chains.cooldown(s, c.chainId)
                s = close(data, s, c, ContractStatus.FAILED, notes.toList())
            }
            else -> {
                s = shiftAttitude(s, clientFaction, -1)
                notes += "Abbruch: ${factionName(data, clientFaction)} Haltung −1"
                if (c.chainId != null) s = Chains.cooldown(s, c.chainId)
                s = close(data, s, c, ContractStatus.ABORTED, notes.toList())
            }
        }
        s = s.copy(flags = s.flags - "op_close_a" - "op_close_b")
        return s to notes
    }


    /** Folgeauftrag: Erfolge öffnen neue Türen, harte Abschlüsse provozieren Gegenreaktionen. */
    private fun followUp(data: GameData, state: GameState, c: Contract, hard: Boolean): GameState {
        if (c.chainId != null) return state
        val rng = Rng(Rng.mix(c.seed, 555))
        if (rng.percent() > (if (hard) 55 else 35)) return state
        val arch = if (hard) archetypeById.getValue(if (Ages.player(state) >= 18) "protection" else "negotiation")
            else archetypeById.getValue(rng.pick(listOf("data", "observation", "intel")))
        val nc = generate(data, state, state.clock.day, state.ops.counter, Rng.mix(c.seed, 556), forced = arch) ?: return state
        val follow = nc.copy(
            title = "Folgeauftrag: " + nc.title.substringAfter(": "), regionId = c.regionId, targetFaction = c.targetFaction,
            urgency = if (hard) 2 else 1, difficulty = (c.difficulty + if (hard) 1 else 0).coerceAtMost(if (Ages.player(state) >= 18) 5 else 2),
            briefing = (if (hard) "Nach dem harten Abschluss von „${c.title}“ reagiert ${factionName(data, c.targetFaction)}. " else "Die Erkenntnisse aus „${c.title}“ eröffnen eine neue Spur. ") + nc.briefing
        )
        var s = state.copy(ops = state.ops.copy(contracts = state.ops.contracts + follow, counter = state.ops.counter + 1))
        s = worldLog(s, "Neuer Folgeauftrag: ${follow.title}")
        return s
    }

    /** Teammitglieder gehen für die Dauer des Auftrags in den Einsatz. */
    fun deployTeam(data: GameData, state: GameState, m: MissionDefinition, team: List<String>): GameState {
        var s = state
        team.forEach { id ->
            val l = Life.life(data, s, id)
            s = s.copy(living = s.living.copy(lives = s.living.lives + (id to l.copy(status = LifeStatus.MISSION, statusUntil = s.clock.day, statusNote = "Einsatz mit dir"))))
        }
        return s
    }
}

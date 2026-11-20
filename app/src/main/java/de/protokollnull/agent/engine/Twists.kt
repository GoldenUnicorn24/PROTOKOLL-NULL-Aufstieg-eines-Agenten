package de.protokollnull.agent.engine

import de.protokollnull.agent.model.*

/**
 * v1.0 Missionsdirektor 3.0: Überraschende Wendungen in dynamischen Aufträgen.
 *
 * Eine Wendung ist eine zusätzliche Entscheidungsphase zwischen Vorgehen und Durchführung –
 * deterministisch aus Auftrags-ID und Seed gewählt (gleicher Auftrag = gleiche Wendung, auch
 * nach dem Laden). Kurze Routineaufträge (Gefahr 1, eintägig) bleiben bewusst ohne Wendung.
 */
object Twists {
    data class Twist(val id: String, val title: String, val text: String, val archetypes: Set<String> = emptySet(), val options: (Int) -> List<StageOption>)

    private fun o(id: String, text: String, attr: String?, skill: String?, diff: Int, ok: Outcome, fail: Outcome? = null) =
        StageOption(id, text, attr?.let { CheckDef(it, skill, diff) }, success = ok, failure = fail)

    val all: List<Twist> = listOf(
        Twist("witness", "Ein Zeuge", "Ein Junge auf einem Fahrrad hält an und starrt euch an. Er hat gesehen, wie ihr die Tür geöffnet habt.") { d -> listOf(
            o("distract", "Freundlich ablenken – „Wir sind von der Hausverwaltung.“", Attr.CHARISMA, "disguise", 30 + d * 5, Outcome("Er zuckt mit den Schultern und fährt weiter.", next = "quiet"), Outcome("Er glaubt dir nicht und fotografiert euch.", next = "quiet", alarm = 1, cover = -1)),
            o("pause", "Kurz unterbrechen, bis er weg ist", Attr.WILLPOWER, null, 25 + d * 4, Outcome("Zwei Minuten Geduld. Niemand hat etwas bemerkt.", next = "quiet", stress = 3), Outcome("Die Pause kostet Zeit – das Ziel wird unruhig.", next = "quiet", alarm = 1)),
            o("ignore", "Weitermachen – er ist ein Kind", null, null, 0, Outcome("Er erzählt es später seiner Mutter. Die erzählt es der Polizei.", next = "quiet", alarm = 1))
        ) },
        Twist("price", "Ein neuer Preis", "Der Kontakt lächelt und schiebt den Umschlag wieder zurück. „Die Lage hat sich geändert. Der Preis auch.“", setOf("intel", "negotiation", "undercover", "data")) { d -> listOf(
            o("pay", "Zahlen (−150 Cr) – Zeit ist wichtiger", null, null, 0, Outcome("Er zählt nach und gibt dir mehr, als du gefragt hast.", next = "quiet", intel = 2, effects = Effects(credits = -150))),
            o("haggle", "Hart verhandeln", Attr.CHARISMA, "negotiation", 34 + d * 5, Outcome("Er gibt nach. Respekt in seinem Blick.", next = "quiet", intel = 1), Outcome("Er wird nervös und redet weniger.", next = "quiet", cover = -1)),
            o("bluff", "Bluffen: „Meridian zahlt dir das nicht.“", Attr.PERCEPTION, "lie_detector", 36 + d * 5, Outcome("Volltreffer – er hat Angst vor Meridian.", next = "quiet", intel = 2), Outcome("Der Bluff verpufft.", next = "quiet", alarm = 1))
        ) },
        Twist("police", "Streifenwagen", "Ein Streifenwagen hält auf der anderen Straßenseite. Zwei Beamte steigen aus und sehen sich um.") { d -> listOf(
            o("legend", "Legende halten und vorbeigehen", Attr.CHARISMA, "disguise", 32 + d * 5, Outcome("Ein Nicken, ein „Guten Abend“. Sie sehen durch dich hindurch.", next = "quiet"), Outcome("Sie verlangen deinen Ausweis. Es dauert.", next = "quiet", alarm = 1, stress = 4)),
            o("wait", "Im Schatten warten", Attr.PERCEPTION, "blend_in", 30 + d * 5, Outcome("Nach zehn Minuten fahren sie weiter.", next = "quiet"), Outcome("Einer leuchtet in deine Richtung.", next = "quiet", alarm = 1)),
            o("detour", "Großen Umweg laufen", Attr.FITNESS, "orientation", 28 + d * 5, Outcome("Über zwei Hinterhöfe bist du wieder am Ziel.", next = "quiet", stress = 3), Outcome("Du verirrst dich kurz.", next = "quiet", stress = 6))
        ) },
        Twist("spotter", "Ein Späher", "Am Kiosk steht ein Mann, der zu oft auf sein Telefon und zu oft auf dich sieht. Meridian-Stil.") { d -> listOf(
            o("shadow", "Ihn unbemerkt beschatten", Attr.PERCEPTION, "shadowstep", 36 + d * 5, Outcome("Er führt dich zu einem zweiten Kontakt. Wertvoll.", next = "quiet", intel = 2, effects = Effects(faction = mapOf("syndicate" to -2))), Outcome("Er merkt es und verschwindet.", next = "quiet", alarm = 1)),
            o("ignore", "Ignorieren und weitermachen", null, null, 0, Outcome("Er meldet deine Ankunft. Die Gegenseite ist vorbereitet.", next = "quiet", alarm = 1)),
            o("bump", "„Versehentlich“ anrempeln und sein Telefon prüfen", Attr.PRECISION, "shadowstep", 40 + d * 5, Outcome("Zwei Sekunden reichen für eine Nummer.", next = "quiet", intel = 1), Outcome("Er hält das Telefon fest. Und dich im Blick.", next = "quiet", alarm = 1, cover = -1))
        ) },
        Twist("comms", "Funkstille", "Ein Knacken, dann nichts mehr. Der Funk ist tot – Störsender oder Pech.") { d -> listOf(
            o("fix", "Kanal improvisieren", Attr.INTELLECT, "electronics", 34 + d * 5, Outcome("Ein Ausweichkanal steht nach einer Minute.", next = "quiet", intel = 1), Outcome("Nur Rauschen. Ihr arbeitet getrennt weiter.", next = "quiet", stress = 5)),
            o("signs", "Auf vereinbarte Handzeichen umstellen", Attr.WILLPOWER, "planning", 30 + d * 5, Outcome("Das Training zahlt sich aus.", next = "quiet"), Outcome("Ein Zeichen wird missverstanden.", next = "quiet", alarm = 1))
        ) },
        Twist("injured", "Ein Verletzter", "Ein Lieferfahrer ist von der Rampe gestürzt und liegt mitten auf deinem Weg. Er blutet am Kopf.") { d -> listOf(
            o("help", "Erste Hilfe leisten – auch wenn es Zeit kostet", Attr.INTELLECT, "first_aid", 28 + d * 4, Outcome("Er ist stabil, als der Rettungswagen kommt. Du bist längst weg.", next = "quiet", stress = 3, effects = Effects(reputation = 2, faction = mapOf("civilian" to 2))), Outcome("Du hilfst, so gut es geht. Es kostet Zeit und Nerven.", next = "quiet", stress = 6, alarm = 1)),
            o("call", "Anonym den Notruf wählen und weitergehen", null, null, 0, Outcome("Sirenen in der Ferne. Du hoffst, dass sie rechtzeitig kommen.", next = "quiet", stress = 4)),
        ) },
        Twist("recognized", "Ein bekanntes Gesicht", "„Bist du nicht …?“ Eine Frau aus deinem Tarnjob steht vor dir und strahlt.", setOf("undercover", "observation", "recon", "intel")) { d -> listOf(
            o("cover", "Im Tarnleben bleiben und plaudern", Attr.CHARISMA, "empathy", 30 + d * 5, Outcome("Zwei Minuten Smalltalk – und sie hat dir unbewusst etwas Nützliches erzählt.", next = "quiet", intel = 1), Outcome("Du wirkst gehetzt. Sie wird es weitererzählen.", next = "quiet", cover = -1)),
            o("excuse", "Höflich abwimmeln", Attr.WILLPOWER, null, 26 + d * 4, Outcome("„Später!“ Sie winkt.", next = "quiet"), Outcome("Sie ist beleidigt und laut.", next = "quiet", alarm = 1))
        ) },
        Twist("trap", "Präparierter Datenträger", "Der Datenträger ist zu leicht zu finden. Jemand wollte, dass du ihn findest.", setOf("data", "intel", "joint", "recon")) { d -> listOf(
            o("analyse", "Vor Ort analysieren", Attr.INTELLECT, "analysis", 36 + d * 5, Outcome("Ein Ortungsprogramm – entfernt. Der Inhalt ist echt.", next = "quiet", intel = 2), Outcome("Du findest das Programm zu spät. Jemand weiß jetzt, wo du bist.", next = "quiet", alarm = 2)),
            o("leave", "Liegen lassen und anders vorgehen", Attr.PERCEPTION, "evidence", 30 + d * 5, Outcome("Die Falle schnappt ins Leere.", next = "quiet"), Outcome("Ohne den Datenträger fehlt dir ein Puzzleteil.", next = "quiet", stress = 4))
        ) }
    )
    val byId = all.associateBy { it.id }

    /** Wendung für einen Auftrag oder null (Routineaufträge bleiben kurz). */
    fun forContract(c: Contract): Twist? {
        if (c.difficulty <= 1 && c.phases <= 1) return null
        val rng = Rng(Rng.mix(c.seed, c.id.hashCode().toLong(), 9091L + c.phase))
        if (rng.percent() > 55 + c.difficulty * 5) return null
        val pool = all.filter { it.archetypes.isEmpty() || c.archetype in it.archetypes }
        return rng.pick(pool)
    }

    /** Fügt die Wendung zwischen Vorgehen und ruhiger Durchführung ein (Taktikpfad bleibt unverändert). */
    fun apply(stages: List<StageDefinition>, c: Contract): List<StageDefinition> {
        val t = forContract(c) ?: return stages
        if (stages.none { it.id == "quiet" }) return stages
        val rerouted = stages.map { st ->
            if (st.id != "approach") st else st.copy(options = st.options.map { o ->
                o.copy(success = if (o.success.next == "quiet") o.success.copy(next = "twist") else o.success,
                    failure = o.failure?.let { f -> if (f.next == "quiet") f.copy(next = "twist") else f })
            })
        }
        val idx = rerouted.indexOfFirst { it.id == "approach" } + 1
        return rerouted.toMutableList().apply { add(idx, StageDefinition("twist", "choice", "Wendung: ${t.title}", t.text, options = t.options(c.difficulty))) }
    }
}

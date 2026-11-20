package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v0.6 „Living Division“: deterministische Lebenssimulation aller Figuren.
 *
 * Jede Figur entscheidet täglich anhand ihres Profils, ihrer Stimmung,
 * Charakterzüge und Ziele, was sie tut (Dienst, Training, Freizeit, Einsatz,
 * Urlaub …). Daraus entstehen eigene Karrieren (Training → Prüfung →
 * Beförderung oder Durchfallen), Verletzungen, Abwesenheiten, Versetzungen
 * und NPC-NPC-Beziehungen. Alle Würfe hängen nur von Seed, Tag und Figur ab.
 *
 * Wichtig: Die Simulation verändert niemals die Romanzenstufe zwischen
 * Spieler und Figur – Beziehungen entstehen ausschließlich durch bewusste
 * Entscheidungen des Spielers.
 */
object Life {
    const val FEED_CAP = 160
    private const val MAX_CATCH_UP = 3700

    val focusAreas = listOf("combat", "tech", "intel", "social", "medic")
    fun focusLabel(id: String) = when (id) {
        "combat" -> "Nahkampf & Fitness"
        "tech" -> "Technik"
        "intel" -> "Analyse & Aufklärung"
        "social" -> "Gesprächsführung"
        "medic" -> "Sanitätsdienst"
        else -> id
    }

    private val defaultFocus = mapOf(
        "michael" to "combat", "nikita" to "combat", "alex" to "combat", "birkhoff" to "tech",
        "amanda" to "social", "percy" to "intel", "thom" to "combat", "jaden" to "combat",
        "owen" to "combat", "sonya" to "tech", "ryan" to "intel", "voss" to "combat",
        "maya" to "social", "ethan" to "intel", "kira" to "intel", "helena" to "medic"
    )

    /** Figuren, die vorübergehend versetzt werden können (kehren später zurück). */
    private val transferable = setOf("thom", "jaden", "ryan", "helena", "sonya")

    /** Volljährige Figuren ohne Spielerromanze, die untereinander Paare bilden können. */
    private val npcCoupleCandidates = setOf("birkhoff", "sonya", "owen", "ryan", "helena", "thom", "jaden")

    private val seedBonds = mapOf(
        LivingState.key("michael", "nikita") to 45, LivingState.key("alex", "nikita") to 55,
        LivingState.key("birkhoff", "sonya") to 30, LivingState.key("alex", "thom") to 20,
        LivingState.key("alex", "jaden") to -25, LivingState.key("amanda", "percy") to 15,
        LivingState.key("michael", "percy") to -20, LivingState.key("nikita", "owen") to 25,
        LivingState.key("birkhoff", "michael") to 25, LivingState.key("jaden", "thom") to 15,
        LivingState.key("maya", "ethan") to 30, LivingState.key("amanda", "michael") to -5,
        LivingState.key("nikita", "percy") to -80, LivingState.key("amanda", "nikita") to -50
    )

    // --- Abfragen ------------------------------------------------------------------

    fun ids(data: GameData, state: GameState): List<String> =
        data.agents.map { it.id } + state.customNpcs.map { it.id }

    fun profile(data: GameData, state: GameState, id: String): String =
        data.agentById[id]?.lifeProfile ?: "recruit"

    fun life(data: GameData, state: GameState, id: String): NpcLife =
        state.living.lives[id] ?: initial(data, state, id)

    fun initial(data: GameData, state: GameState, id: String): NpcLife {
        val def = data.agentById[id]
        val custom = state.customNpcs.firstOrNull { it.id == id }
        val focus = custom?.let { focusForWish(it.careerWish, it.profession) } ?: defaultFocus[id] ?: "combat"
        return NpcLife(
            rank = def?.startRank ?: custom?.rank ?: 1,
            focus = focus,
            goal = def?.goal ?: custom?.lifeGoal.orEmpty()
        )
    }

    fun focusForWish(wish: String, profession: String) = when (wish.ifBlank { profession }) {
        "field", "lead" -> "combat"
        "tech" -> "tech"
        "medic" -> "medic"
        "intel" -> "intel"
        "social" -> "social"
        else -> "intel"
    }

    /**
     * Gemeinsame 6er-Attributskala für sämtliche Charaktere. Für ältere Saves
     * ohne individuelle NPC-Ratings werden stabile Ausgangswerte aus dem
     * Figurenprofil berechnet; Training speichert danach echte Veränderungen.
     */
    fun attributeRatings(data: GameData, state: GameState, id: String): Map<String, Int> {
        val l = life(data, state, id)
        val def = data.agentById[id]
        val own = state.customNpcs.firstOrNull { it.id == id }
        val combat = def?.combat ?: (own?.skills?.get("stamina") ?: 2) * 6 + 28
        val focus = l.focus.ifBlank { "combat" }
        val base = linkedMapOf(
            Attr.FITNESS to combat.coerceAtLeast(15),
            Attr.PRECISION to (combat - 5).coerceAtLeast(15),
            Attr.INTELLECT to (if (focus in setOf("tech", "intel")) 55 else 35),
            Attr.CHARISMA to (if (focus == "social") 55 else 35),
            Attr.PERCEPTION to (if (focus == "intel") 55 else 40),
            Attr.WILLPOWER to (if (focus == "medic") 45 else 40)
        )
        return base.mapValues { (attribute, initial) ->
            (l.attributes[attribute] ?: initial).coerceAtLeast(0)
        }
    }

    fun rank(data: GameData, state: GameState, id: String): Int = life(data, state, id).rank

    fun traits(data: GameData, state: GameState, id: String): List<String> =
        data.agentById[id]?.traits ?: state.customNpcs.firstOrNull { it.id == id }?.traits ?: emptyList()

    fun interests(data: GameData, state: GameState, id: String): List<String> =
        data.agentById[id]?.interests ?: state.customNpcs.firstOrNull { it.id == id }?.interests ?: emptyList()

    fun values(data: GameData, state: GameState, id: String): List<String> =
        data.agentById[id]?.values ?: state.customNpcs.firstOrNull { it.id == id }?.values ?: emptyList()

    /** Kompetenz in einem Bereich (0..10): Grundwert + eigenes Training. */
    fun competence(data: GameData, state: GameState, id: String, area: String): Int {
        val l = life(data, state, id)
        val def = data.agentById[id]
        val custom = state.customNpcs.firstOrNull { it.id == id }
        val base = when {
            def != null -> when (area) {
                "combat" -> def.combat / 15
                defaultFocus[id] -> 4
                else -> 2
            }
            custom != null -> {
                val sk = custom.skills
                when (area) {
                    "combat" -> (sk["stamina"] ?: 0) + 1
                    "tech" -> (sk["analysis"] ?: 0) + 1
                    "intel" -> (sk["observe"] ?: 0) + (sk["analysis"] ?: 0) / 2 + 1
                    "social" -> (sk["empathy"] ?: 0) + 1
                    "medic" -> (sk["first_aid"] ?: 0) + 1
                    else -> 1
                }
            }
            else -> 2
        }
        return (base + (l.skillGain[area] ?: 0)).coerceIn(0, 10)
    }

    fun bond(state: GameState, a: String, b: String): Int =
        state.living.bonds[LivingState.key(a, b)] ?: seedBonds[LivingState.key(a, b)] ?: 0

    fun partnerOf(state: GameState, id: String): String? =
        state.living.couples.firstOrNull { it.split("|").contains(id) }?.split("|")?.firstOrNull { it != id }

    /** Kurzbeschreibung für Kontakte und Personalakte. */
    fun statusLine(data: GameData, state: GameState, id: String): String {
        val l = state.living.lives[id] ?: return "Im Dienst"
        val until = if (l.statusUntil >= state.clock.day && l.status != LifeStatus.TRANSFERRED) {
            val left = l.statusUntil - state.clock.day + 1
            " · noch $left Tag" + if (left == 1) "" else "e"
        } else ""
        val note = if (l.statusNote.isNotBlank()) " – ${l.statusNote}" else ""
        return LifeStatus.label(l.status) + note + until
    }

    fun inHq(data: GameData, state: GameState, id: String): Boolean =
        LifeStatus.inHq(state.living.lives[id]?.status ?: LifeStatus.DUTY)

    /** Beförderungsschwelle in Karrierepunkten. */
    fun examThreshold(rank: Int) = 250 + rank * 200

    fun rankCap(data: GameData, state: GameState, id: String): Int {
        val adult = Ages.npc(data, state, id) >= 18
        val cap = when (profile(data, state, id)) {
            "recruit" -> 6
            "staff" -> 7
            "agent" -> 8
            else -> 0 // leader/rogue: kein Prüfungsweg
        }
        if (state.customNpcs.any { it.id == id }) return if (adult) 9 else 3
        return if (adult) cap else cap.coerceAtMost(3)
    }

    // --- Fortschreibung ------------------------------------------------------------

    /**
     * Simuliert alle noch nicht verarbeiteten Tage bis einschließlich heute.
     * Idempotent: mehrfacher Aufruf am selben Tag ändert nichts.
     */
    fun advance(data: GameData, state: GameState, log: MutableList<String>? = null, hqEvents: Boolean = true): GameState {
        if (state.phase != "game") return state
        var s = state
        val today = s.clock.day
        var from = s.living.lastDay + 1
        if (s.living.lastDay < 0) {
            s = s.copy(living = s.living.copy(lastDay = today - 1, bonds = seedBonds + s.living.bonds))
            from = today
        }
        if (from > today) return s
        if (today - from > MAX_CATCH_UP) from = today - MAX_CATCH_UP
        for (d in from..today) s = simulateDay(data, s, d, log, hqEvents && d == today)
        return s.copy(living = s.living.copy(lastDay = today))
    }

    private fun simulateDay(data: GameData, state: GameState, day: Int, log: MutableList<String>?, hqEvents: Boolean): GameState {
        var s = state
        for (id in ids(data, s)) s = stepNpc(data, s, id, day, log)
        if (day % 7 == 3) s = socialWeek(data, s, day, log)
        if (day % 7 == 5) s = Bonds.weekly(data, s, day)
        if (day % 30 == 0) s = monthlyGrades(data, s, day)
        s = arcsDaily(data, s, day)
        if (hqEvents) s = HqEvents.daily(data, s, day)
        return s
    }

    private fun rngFor(state: GameState, id: String, day: Int, salt: Long) =
        Rng(Rng.mix(state.seed, day.toLong(), id.hashCode().toLong(), salt))

    private fun put(state: GameState, id: String, l: NpcLife) =
        state.copy(living = state.living.copy(lives = state.living.lives + (id to l)))

    fun feed(data: GameData, state: GameState, e: LifeEvent, log: MutableList<String>? = null): GameState {
        log?.add("${Clock.dateLabel(e.day)}: ${e.text}")
        return state.copy(living = state.living.copy(feed = (state.living.feed + e).takeLast(FEED_CAP)))
    }

    private fun name(data: GameData, state: GameState, id: String) = Relations.displayName(data, state, id).substringBefore(" ")

    private fun stepNpc(data: GameData, state: GameState, id: String, day: Int, log: MutableList<String>?): GameState {
        // Zugewiesene Gefährtin bzw. Gefährte: erst nach Ankunft simulieren
        val custom = state.customNpcs.firstOrNull { it.id == id }
        if (custom != null && day < custom.createdDay) return state
        var s = state
        var l = life(data, s, id)
        val rng = rngFor(s, id, day, 11)
        // Stimmung und Stress pendeln sich ein
        l = l.copy(mood = (l.mood + (60 - l.mood).coerceIn(-1, 1)).pct(), stress = (l.stress - 1).pct())
        // Laufender Sonderstatus
        if (l.statusUntil >= day) {
            if (l.status == LifeStatus.MISSION && l.statusUntil == day) return resolveMission(data, put(s, id, l), id, day, log)
            return put(s, id, l)
        }
        // Sonderstatus abgelaufen
        if (l.statusUntil in 0 until day) {
            val back = when (l.status) {
                LifeStatus.INJURED -> "${name(data, s, id)} ist wieder dienstfähig."
                LifeStatus.VACATION -> "${name(data, s, id)} ist aus dem Urlaub zurück."
                LifeStatus.TRANSFERRED -> "${Relations.displayName(data, s, id)} kehrt von der Versetzung in die Division zurück."
                LifeStatus.ABSENT -> null
                else -> null
            }
            l = l.copy(status = LifeStatus.DUTY, statusUntil = -1, statusNote = "")
            s = put(s, id, l)
            if (back != null) s = feed(data, s, LifeEvent(day, id, "returned", back), log)
        }
        if (l.status == LifeStatus.TRANSFERRED) return put(s, id, l)
        // Prüfung, sobald die Karrierepunkte reichen
        if (l.careerXp >= examThreshold(l.rank) && day >= l.examCooldownUntil && l.rank < rankCap(data, s, id)) {
            return exam(data, s, id, l, day, log)
        }
        return decide(data, s, id, l, day, rng, log)
    }

    private fun decide(data: GameData, state: GameState, id: String, life: NpcLife, day: Int, rng: Rng, log: MutableList<String>?): GameState {
        var s = state
        var l = life
        val profile = profile(data, s, id)
        val adult = Ages.npc(data, s, id) >= 18
        val weekend = Clock.isWeekend(day)
        val traits = traits(data, s, id)
        val ambitious = traits.any { it in setOf("ehrgeizig", "diszipliniert", "kompetitiv") }
        val calm = traits.any { it in setOf("ruhig", "verschlossen", "vorsichtig") }
        val sociable = traits.any { it in setOf("humorvoll", "loyal", "warmherzig", "neugierig") }
        val inTeam = id in s.team
        // Gewichte: duty, training, free, mission
        var duty: Int; var training: Int; var free: Int; var mission: Int
        when (profile) {
            "leader" -> { duty = 84; training = 4; free = 12; mission = if (id == "michael") 2 else 0 }
            "rogue" -> { duty = 0; training = 5; free = 10; mission = 0 }
            "staff" -> { duty = 70; training = 8; free = 20; mission = if (adult && id == "owen") 5 else 1 }
            "agent" -> { duty = 40; training = 22; free = 20; mission = if (adult) 14 else 0 }
            else -> { duty = 15; training = 55; free = 25; mission = if (adult && l.rank >= 3) 6 else 0 }
        }
        if (weekend) { free += 35; training -= training / 3; duty -= duty / 3 }
        if (ambitious) training += 12
        if (calm) mission = mission * 2 / 3
        if (sociable) free += 6
        if (l.mood < 35) { free += 20; training -= training / 2 }
        if (l.stress > 70) { free += 15; mission /= 2 }
        if (inTeam) mission /= 2 // Stammteam-Mitglieder warten auf deine Einsätze
        // Seltene, längere Abwesenheiten
        val roll = rng.nextInt(1000)
        if (profile == "rogue") {
            val away = rng.percent() <= 80
            l = l.copy(status = if (away) LifeStatus.ABSENT else LifeStatus.DUTY, statusNote = if (away) "Untergetaucht" else "", statusUntil = -1, lastDecision = if (away) "Untergetaucht" else "In der Stadt", lastDecisionDay = day)
            return put(s, id, l)
        }
        if (adult && profile != "recruit" && id != "michael" && roll < 4 && day > 30) {
            val days = rng.range(5, 12)
            l = l.copy(status = LifeStatus.VACATION, statusUntil = day + days - 1, statusNote = vacationNote(rng), lastDecision = "Urlaub genommen", lastDecisionDay = day)
            s = put(s, id, l)
            return feed(data, s, LifeEvent(day, id, "vacation", "${name(data, s, id)} nimmt $days Tage Urlaub."), log)
        }
        if (id in transferable && !inTeam && l.mood < 30 && roll in 4..6 && adult) {
            val days = rng.range(120, 300)
            l = l.copy(status = LifeStatus.TRANSFERRED, statusUntil = day + days - 1, statusNote = "Außenstelle", transferredDay = day, lastDecision = "Versetzung beantragt", lastDecisionDay = day)
            s = put(s, id, l)
            return feed(data, s, LifeEvent(day, id, "transferred", "${Relations.displayName(data, s, id)} lässt sich für einige Monate an eine Außenstelle versetzen."), log)
        }
        val total = (duty + training + free + mission).coerceAtLeast(1)
        var pick = rng.nextInt(total)
        val choice = when {
            pick < mission -> LifeStatus.MISSION
            run { pick -= mission; pick < training } -> LifeStatus.TRAINING
            run { pick -= training; pick < free } -> LifeStatus.FREE
            else -> LifeStatus.DUTY
        }
        when (choice) {
            LifeStatus.MISSION -> {
                val days = rng.range(1, 5)
                val diff = rng.range(30, 75) + l.rank * 2
                l = l.copy(status = LifeStatus.MISSION, statusUntil = day + days - 1, statusNote = missionNote(rng), missionDifficulty = diff, lastDecision = "Einsatz übernommen", lastDecisionDay = day)
                s = put(s, id, l)
                if (days >= 3) s = feed(data, s, LifeEvent(day, id, "mission_start", "${name(data, s, id)} bricht zu einem ${days}-tägigen Einsatz auf."), null)
                if (days == 1) return resolveMission(data, s, id, day, log)
                return s
            }
            LifeStatus.TRAINING -> {
                val gain = rng.range(2, 5) + if (ambitious) 1 else 0
                val prog = (l.skillProgress[l.focus] ?: 0) + gain
                val level = l.skillGain[l.focus] ?: 0
                val need = 40 * (level + 1)
                l = if (prog >= need && level < 5) {
                    s = feed(data, s, LifeEvent(day, id, "skill", "${name(data, s, id)} macht Fortschritte: ${focusLabel(l.focus)} Stufe ${level + 1}."), null)
                    l.copy(skillProgress = l.skillProgress + (l.focus to 0), skillGain = l.skillGain + (l.focus to level + 1))
                } else l.copy(skillProgress = l.skillProgress + (l.focus to prog))
                l = l.copy(status = LifeStatus.TRAINING, statusUntil = -1, statusNote = focusLabel(l.focus), careerXp = l.careerXp + 3 + if (ambitious) 1 else 0,
                    trainingDays = l.trainingDays + 1, stress = (l.stress + 2).pct(), lastDecision = "Training: " + focusLabel(l.focus), lastDecisionDay = day)
            }
            LifeStatus.FREE -> l = l.copy(status = LifeStatus.FREE, statusUntil = -1, statusNote = freeNote(data, s, id, rng), mood = (l.mood + 3).pct(), stress = (l.stress - 4).pct(), lastDecision = "Freizeit", lastDecisionDay = day)
            else -> l = l.copy(status = LifeStatus.DUTY, statusUntil = -1, statusNote = "", careerXp = l.careerXp + if (profile in setOf("leader", "rogue")) 0 else 2, lastDecision = "Dienst", lastDecisionDay = day)
        }
        return put(s, id, l)
    }

    private fun vacationNote(rng: Rng) = rng.pick(listOf("an der Küste", "bei der Familie", "in den Bergen", "ohne Telefon", "in einer anderen Stadt"))
    private fun missionNote(rng: Rng) = rng.pick(listOf("Observation", "Personenschutz", "Datenbeschaffung", "Kurierauftrag", "Aufklärung", "Zugriff"))
    private fun freeNote(data: GameData, s: GameState, id: String, rng: Rng): String {
        val i = interests(data, s, id)
        return if (i.isEmpty()) "Pause" else rng.pick(i)
    }

    private fun resolveMission(data: GameData, state: GameState, id: String, day: Int, log: MutableList<String>?): GameState {
        var s = state
        var l = life(data, s, id)
        val rng = rngFor(s, id, day, 23)
        val score = l.rank * 6 + competence(data, s, id, "combat") * 3 + competence(data, s, id, l.focus) * 3 + l.mood / 5 + rng.range(0, 45)
        val success = score >= l.missionDifficulty
        val injuryRoll = rng.percent()
        val injured = if (success) injuryRoll <= 7 else injuryRoll <= 38
        val nm = name(data, s, id)
        l = if (success) l.copy(missions = l.missions + 1, careerXp = l.careerXp + 12 + l.missionDifficulty / 6, mood = (l.mood + 6).pct(), stress = (l.stress + 8).pct())
        else l.copy(missionsFailed = l.missionsFailed + 1, careerXp = l.careerXp + 4, mood = (l.mood - 12).pct(), stress = (l.stress + 15).pct())
        l = l.copy(status = LifeStatus.DUTY, statusUntil = -1, statusNote = "", missionDifficulty = 0)
        s = s.copy(flags = s.flags + if (success) "lf_${id}_mission_ok" else "lf_${id}_mission_failed")
        s = feed(data, s, LifeEvent(day, id, if (success) "mission_ok" else "mission_fail",
            if (success) "$nm kehrt erfolgreich von einem Einsatz zurück." else "$nm kehrt von einem gescheiterten Einsatz zurück."), log)
        if (injured) {
            val days = rng.range(3, 12)
            l = l.copy(status = LifeStatus.INJURED, statusUntil = day + days, statusNote = rng.pick(listOf("Prellungen", "Schnittwunde", "Gehirnerschütterung", "verstauchter Knöchel")), injuries = l.injuries + 1)
            s = s.copy(flags = s.flags + "lf_${id}_injured")
            s = feed(data, s, LifeEvent(day, id, "injured", "$nm liegt verletzt auf der Krankenstation (${l.statusNote})."), log)
        }
        return put(s, id, l)
    }

    private fun exam(data: GameData, state: GameState, id: String, life: NpcLife, day: Int, log: MutableList<String>?): GameState {
        var s = state
        var l = life
        val rng = rngFor(s, id, day, 37)
        val gradeBonus = when (l.grades.lastOrNull()) { "A" -> 12; "B" -> 6; "C" -> 0; "D" -> -6; "E" -> -12; else -> 0 }
        val chance = (50 + gradeBonus + competence(data, s, id, l.focus) * 3 + (l.mood - 50) / 4 - l.rank * 3 - l.stress / 10).coerceIn(15, 90)
        val pass = rng.percent() <= chance
        val nm = Relations.displayName(data, s, id)
        if (pass) {
            val newRank = l.rank + 1
            l = l.copy(rank = newRank, careerXp = 0, examCooldownUntil = day + 60, examsPassed = l.examsPassed + 1, mood = (l.mood + 12).pct(), status = LifeStatus.DUTY, statusUntil = -1, statusNote = "Beförderung", lastDecision = "Prüfung bestanden", lastDecisionDay = day)
            s = s.copy(flags = s.flags + "lf_${id}_promoted" + "lf_${id}_rank_$newRank")
            s = put(s, id, l)
            return feed(data, s, LifeEvent(day, id, "promoted", "$nm besteht die Prüfung und wird zum ${data.rank(newRank).name} befördert."), log)
        }
        l = l.copy(examsFailed = l.examsFailed + 1, examCooldownUntil = day + 30, careerXp = l.careerXp * 2 / 3, mood = (l.mood - 15).pct(),
            stress = (l.stress + 10).pct(), status = LifeStatus.DUTY, statusUntil = -1, statusNote = "Prüfung nicht bestanden", lastDecision = "Prüfung nicht bestanden", lastDecisionDay = day)
        s = s.copy(flags = s.flags + "lf_${id}_exam_failed")
        s = put(s, id, l)
        return feed(data, s, LifeEvent(day, id, "exam_failed", "$nm fällt durch die Prüfung zum ${data.rank(l.rank + 1).name} und muss 30 Tage warten."), log)
    }

    private fun monthlyGrades(data: GameData, state: GameState, day: Int): GameState {
        var s = state
        for (id in ids(data, s)) {
            val l = s.living.lives[id] ?: continue
            if (profile(data, s, id) in setOf("leader", "rogue") && s.customNpcs.none { it.id == id }) continue
            val delta = (l.careerXp - l.monthStartXp).coerceAtLeast(0) + if (l.careerXp < l.monthStartXp) 60 else 0
            val score = (delta * 100 / 110 + (l.mood - 50) / 3).coerceIn(0, 100)
            val g = when { score >= 80 -> "A"; score >= 60 -> "B"; score >= 40 -> "C"; score >= 25 -> "D"; else -> "E" }
            s = put(s, id, l.copy(grades = (l.grades + g).takeLast(6), monthStartXp = l.careerXp))
        }
        return s
    }

    /** Wöchentliches Sozialleben: Wer Zeit im HQ verbringt, lernt sich kennen. */
    private fun socialWeek(data: GameData, state: GameState, day: Int, log: MutableList<String>?): GameState {
        var s = state
        val present = ids(data, s).filter { id ->
            val l = s.living.lives[id] ?: return@filter false
            LifeStatus.inHq(l.status) && World.revealed(data, s, id) && s.customNpcs.none { it.id == id && day < it.createdDay }
        }
        val bonds = s.living.bonds.toMutableMap()
        val couples = s.living.couples.toMutableSet()
        val events = mutableListOf<LifeEvent>()
        for (i in present.indices) for (j in i + 1 until present.size) {
            val a = present[i]; val b = present[j]
            val key = LivingState.key(a, b)
            val rng = Rng(Rng.mix(s.seed, day.toLong(), key.hashCode().toLong(), 51))
            // Nicht jedes Paar begegnet sich jede Woche
            if (rng.percent() > 45) continue
            val affinity = affinity(data, s, a, b)
            val old = bonds[key] ?: seedBonds[key] ?: 0
            val delta = ((affinity - 3) / 2 + rng.range(-2, 2)).coerceIn(-4, 5)
            val nv = (old + delta).coerceIn(-100, 100)
            bonds[key] = nv
            val na = name(data, s, a); val nb = name(data, s, b)
            if (old < 50 && nv >= 50) events += LifeEvent(day, a, "friends", "$na und $nb sind gute Freunde geworden.", listOf(b))
            if (old > -40 && nv <= -40) events += LifeEvent(day, a, "rivals", "Zwischen $na und $nb gibt es ernste Spannungen.", listOf(b))
            val adults = Ages.npc(data, s, a) >= 18 && Ages.npc(data, s, b) >= 18
            if (key !in couples && adults && a in npcCoupleCandidates && b in npcCoupleCandidates && nv >= 75 &&
                partnerOf(s.copy(living = s.living.copy(couples = couples)), a) == null &&
                partnerOf(s.copy(living = s.living.copy(couples = couples)), b) == null && rng.percent() <= 20
            ) {
                couples += key
                events += LifeEvent(day, a, "couple", "$na und $nb sind jetzt offiziell ein Paar.", listOf(b))
            }
            if (key in couples && nv < 40) {
                couples -= key
                events += LifeEvent(day, a, "breakup", "$na und $nb haben sich getrennt.", listOf(b))
            }
        }
        s = s.copy(living = s.living.copy(bonds = bonds, couples = couples))
        events.forEach { s = feed(data, s, it, log) }
        return s
    }

    /** Wie gut passen zwei Figuren zueinander (−6..10)? */
    fun affinity(data: GameData, state: GameState, a: String, b: String): Int {
        val ia = interests(data, state, a).toSet(); val ib = interests(data, state, b).toSet()
        val va = values(data, state, a).toSet(); val vb = values(data, state, b).toSet()
        val ta = traits(data, state, a).toSet(); val tb = traits(data, state, b).toSet()
        var x = (ia intersect ib).size * 2 + (va intersect vb).size * 2 + (ta intersect tb).size
        if ("loyal" in ta && "loyal" in tb) x += 1
        if (("kontrolle" in va.map { it.lowercase() }) && ("freiheit" in vb.map { it.lowercase() })) x -= 2
        if (("freiheit" in va.map { it.lowercase() }) && ("kontrolle" in vb.map { it.lowercase() })) x -= 2
        if ("ehrgeizig" in ta && "ehrgeizig" in tb) x -= 1
        val ra = rank(data, state, a); val rb = rank(data, state, b)
        if (kotlin.math.abs(ra - rb) <= 1) x += 1
        return x.coerceIn(-6, 10)
    }

    /** Erzählerische Bögen: Percys Druck, Amandas Aufmerksamkeit. */
    private fun arcsDaily(data: GameData, state: GameState, day: Int): GameState {
        if (day % 7 != 0) return state
        val arcs = state.living.arcs.toMutableMap()
        val p = state.player
        if (p.reputation >= 40 || p.rank >= 3) arcs["percy_pressure"] = (arcs["percy_pressure"] ?: 0) + 1
        if (p.stress >= 65) arcs["amanda_watch"] = (arcs["amanda_watch"] ?: 0) + 1
        if ("nikita_contact" in state.flags) arcs["nikita_trust"] = (arcs["nikita_trust"] ?: 0) + 0
        return state.copy(living = state.living.copy(arcs = arcs))
    }

    // --- Eingriffe von außen (HQ-Ereignisse, Gespräche) -------------------------------

    fun apply(data: GameData, state: GameState, c: LifeChange): GameState {
        if (!Relations.isKnownNpc(data, state, c.npcId)) return state
        var s = state
        var l = life(data, s, c.npcId)
        val day = s.clock.day
        if (c.status != null) {
            l = l.copy(status = c.status, statusUntil = if (c.days > 0) day + c.days - 1 else -1, statusNote = c.note,
                transferredDay = if (c.status == LifeStatus.TRANSFERRED) day else l.transferredDay)
        }
        l = l.copy(mood = (l.mood + c.mood).pct(), careerXp = (l.careerXp + c.careerXp).coerceAtLeast(0))
        s = put(s, c.npcId, l)
        if (c.bondWith != null && c.bond != 0) {
            val key = LivingState.key(c.npcId, c.bondWith)
            s = s.copy(living = s.living.copy(bonds = s.living.bonds + (key to (bond(s, c.npcId, c.bondWith) + c.bond).coerceIn(-100, 100))))
        }
        return s
    }

    /** Spieler berät die Gefährtin/den Gefährten zur Laufbahn. */
    fun setFocus(data: GameData, state: GameState, id: String, focus: String): Result {
        if (focus !in focusAreas) return state.fail("Unbekannter Schwerpunkt")
        val key = "focus_talk_$id"
        val last = state.living.arcs[key] ?: -999
        if (state.clock.day - last < 14) return state.fail("Ihr habt erst vor Kurzem über die Laufbahn gesprochen.")
        val l = life(data, state, id)
        val custom = state.customNpcs.firstOrNull { it.id == id }
        val wish = custom?.careerWish?.let { focusForWish(it, custom.profession) }
        val agrees = wish == null || wish == focus || Relations.get(data, state, id).trust >= 55
        val nm = Relations.displayName(data, state, id).substringBefore(" ")
        var s = state.copy(living = state.living.copy(arcs = state.living.arcs + (key to state.clock.day)))
        if (!agrees) {
            s = Relations.update(data, s, id) { it.copy(respect = it.respect + 1, memories = it.memories + "Ein Gespräch über meine Laufbahn – ich bleibe bei meinem Weg") }
            return Result(World.passTime(data, s, 1).state, listOf("$nm hört zu, bleibt aber bei ${focusLabel(l.focus)}: „Ich weiß, was ich will.“ (Vertrauen ≥ 55 nötig, um umzuschwenken)"), ok = false)
        }
        s = put(s, id, l.copy(focus = focus, lastDecision = "Schwerpunkt gewechselt: ${focusLabel(focus)}", lastDecisionDay = s.clock.day))
        s = Relations.update(data, s, id) { it.copy(trust = it.trust + 2, respect = it.respect + 2, memories = it.memories + "Dein Rat zur Laufbahn: ${focusLabel(focus)}") }
        s = feed(data, s, LifeEvent(s.clock.day, id, "decision", "$nm entscheidet sich für den Schwerpunkt ${focusLabel(focus)}."))
        val t = World.passTime(data, s, 1)
        return Result(t.state, listOf("$nm trainiert ab jetzt mit Schwerpunkt ${focusLabel(focus)}.", "Vertrauen +2, Respekt +2") + t.messages)
    }

    /** Anwesenheit im HQ zusammengefasst (für HQ-Übersicht). */
    fun presenceSummary(data: GameData, state: GameState): Map<String, List<String>> =
        ids(data, state).filter { World.revealed(data, state, it) && state.customNpcs.none { c -> c.id == it && state.clock.day < c.createdDay } }
            .groupBy { state.living.lives[it]?.status ?: LifeStatus.DUTY }
}

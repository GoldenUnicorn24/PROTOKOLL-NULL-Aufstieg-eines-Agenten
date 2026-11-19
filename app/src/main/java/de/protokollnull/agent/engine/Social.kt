package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

object Romance {
    val stageNames = listOf("—", "Gegenseitiges Interesse", "Ein Paar", "Zusammengezogen", "Verheiratet", "Familie")
    val teenStageNames = listOf("—", "Schwärmerei", "Fest zusammen (altersgerecht)")

    /** Alex ist in dieser alternativen Nikita-Handlung eine optionale Romanze.
     *  Ohne ausdrückliche Annäherung im Dialog bleibt sie ausschließlich Freundin. */
    fun romanceable(data: GameData, state: GameState, npcId: String): Boolean {
        if (npcId == "alex") return data.agentById[npcId]?.romanceable == true &&
            "alex_romance_unlocked" in state.flags
        return state.customNpcs.any { it.id == npcId } ||
            (data.agentById[npcId]?.romanceable == true)
    }

    /**
     * Freiwillige, nicht-sexualisierte Romanzen sind ab 16 möglich, wenn beide
     * Figuren mindestens 16 und höchstens zwei Jahre auseinander sind (16/17,
     * 18/16). Zwei Erwachsene unterliegen nicht dieser Jugend-Altersgrenze.
     * Zusammenziehen, Heirat und Familienplanung ausschließlich als Erwachsene.
     */
    fun allowed(data: GameData, state: GameState, npcId: String): Boolean {
        if (!romanceable(data, state, npcId)) return false
        val playerAge = Ages.player(state)
        val npcAge = Ages.npc(data, state, npcId)
        if (playerAge >= 18 && npcAge >= 18) return true
        return playerAge >= 16 && npcAge >= 16 && kotlin.math.abs(playerAge - npcAge) <= 2
    }

    fun teen(data: GameData, state: GameState, npcId: String): Boolean =
        Ages.player(state) < 18 || Ages.npc(data, state, npcId) < 18

    fun maxStage(data: GameData, state: GameState, npcId: String): Int =
        if (!allowed(data, state, npcId)) 0 else if (teen(data, state, npcId)) 2 else 5

    fun stageName(data: GameData, state: GameState, npcId: String, stage: Int): String =
        if (teen(data, state, npcId)) teenStageNames.getOrElse(stage) { stageNames[stage] } else stageNames.getOrElse(stage) { "—" }

    fun partnerId(data: GameData, state: GameState): String? =
        state.relations.entries.firstOrNull { it.value.romanceStage >= 2 }?.key

    data class Step(val id: String, val label: String, val requirement: String, val available: Boolean)

    fun nextStep(data: GameData, state: GameState, npcId: String): Step? {
        val r = Relations.get(data, state, npcId)
        if (!romanceable(data, state, npcId)) return null
        if (!allowed(data, state, npcId)) {
            return if (r.romanceStage > 0) Step("pause", "Beziehung pausiert", "Die Altersvoraussetzungen sind momentan nicht erfüllt.", false)
            else null
        }
        // Jugendbeziehungen bleiben bei Interesse oder fester Partnerschaft.
        // Auch manipulierte oder alte Spielstände können hier keine
        // Erwachsenenaktionen (Zusammenziehen/Heirat/Familie) auslösen.
        if (teen(data, state, npcId) && r.romanceStage >= 2) return null
        val other = partnerId(data, state)
        if (other != null && other != npcId) return Step("busy", "Bereits in einer Beziehung", "Du bist mit ${Relations.displayName(data, state, other)} zusammen.", false)
        val day = state.clock.day
        val closed = state.living.arcs["closed_$npcId"]
        if (r.romanceStage == 0 && closed != null && day - closed < 90) return Step("closed", "Ihr seid bewusst Freunde", "Frühestens ${90 - (day - closed)} Tage nach eurer Entscheidung wieder möglich.", false)
        state.living.conflicts[npcId]?.let { c ->
            if (r.romanceStage in 0..2) return Step("conflict", "Erst den Konflikt klären", "Ungelöst: ${c.reason}. Nutze „Aussprache“.", false)
        }
        val compat = Bonds.compatibility(data, state, npcId).score
        return when (r.romanceStage) {
            0 -> Step("interest", "Interesse zeigen", "Zuneigung ≥ 25, Freundschaft ≥ 40", r.affection >= 25 && r.friendship >= 40)
            1 -> Step("confess", "Feste Beziehung eingehen (bewusste Entscheidung)", "Zuneigung ≥ 55, Vertrauen ≥ 55, Passung ≥ 45 · beendet andere Annäherungen",
                r.affection >= 55 && r.trust >= 55 && compat >= 45)
            2 -> Step(
                "move_in", "Zusammenziehen", "Eigene Wohnung, Zuneigung ≥ 75, 60 Tage Beziehung",
                state.player.apartment && r.affection >= 75 && day - r.romanceSinceDay >= 60
            )
            3 -> Step("propose", "Heiratsantrag", "Zuneigung ≥ 85, Vertrauen ≥ 80, 180 Tage zusammen", r.affection >= 85 && r.trust >= 80 && day - r.romanceSinceDay >= 180)
            4 -> Step("family", "Familie planen", "Beide ≥ 21, ein Jahr verheiratet", Ages.player(state) >= 21 && Ages.npc(data, state, npcId) >= 21 && day - r.romanceSinceDay >= 365)
            else -> null
        }
    }

    /** Andere Figuren, zu denen gerade Interesse (Stufe 1) besteht. */
    fun otherInterests(data: GameData, state: GameState, npcId: String): List<String> =
        state.relations.filter { (id, r) -> id != npcId && r.romanceStage == 1 }.keys.toList()

    /**
     * „Nur Freunde bleiben“ bzw. Beziehung beenden – immer eine bewusste
     * Entscheidung des Spielers. Eine Trennung hinterlässt einen Konflikt.
     */
    fun stayFriends(data: GameData, state: GameState, npcId: String): Result {
        val r = Relations.get(data, state, npcId)
        val name = Relations.displayName(data, state, npcId)
        val breakup = r.romanceStage >= 2
        var s = Relations.update(data, state, npcId) {
            it.copy(romanceStage = 0, romanceSinceDay = -1, affection = it.affection.coerceAtMost(20))
        }
        s = s.copy(living = s.living.copy(arcs = s.living.arcs + ("closed_$npcId" to s.clock.day), closedRomances = s.living.closedRomances + npcId))
        s = Bonds.addMilestone(data, s, npcId, if (breakup) "breakup" else "stay_friends")
        if (breakup) {
            s = s.copy(flags = s.flags - "married")
            s = Bonds.addConflict(data, s, npcId, "Die Trennung", 2, "breakup")
            s = Relations.update(data, s, npcId) { it.copy(friendship = it.friendship - 10, trust = it.trust - 5) }
        } else {
            s = Memories.add(data, s, npcId, "Wir haben entschieden, Freunde zu bleiben", "trust", 4)
            s = Relations.update(data, s, npcId) { it.copy(trust = it.trust + 3) }
        }
        val t = World.passTime(data, s, 1)
        return Result(t.state, listOf(
            if (breakup) "Du beendest die Beziehung mit $name. Das wird Zeit brauchen." else "Du sagst $name ehrlich, dass du eure Freundschaft nicht gefährden willst. Es bleibt eine Freundschaft.",
            if (breakup) "Konflikt: Die Trennung · Freundschaft −10" else "Vertrauen +3"
        ) + t.messages)
    }

    fun advance(data: GameData, state: GameState, npcId: String): Result {
        val step = nextStep(data, state, npcId) ?: return state.fail("Kein nächster Schritt möglich.")
        if (!step.available) return state.fail(step.requirement)
        val name = Relations.displayName(data, state, npcId)
        val isTeen = teen(data, state, npcId)
        val (s0, roll) = Checks.roll(data, state, CheckDef(Attr.CHARISMA, "empathy", if (step.id == "interest") 30 else 40), salt = 5)
        if (step.id == "confess" && !roll.success) {
            val s = Relations.update(data, s0, npcId) { it.copy(affection = it.affection - 5, memories = it.memories + "Ein unbeholfenes Geständnis") }
            return Result(World.passTime(data, s, 1).state, listOf("$name braucht noch Zeit. (Probe ${roll.roll}/${roll.chance})"), ok = false)
        }
        val day = state.clock.day
        var s = Relations.update(data, s0, npcId) {
            val stage = it.romanceStage + 1
            it.copy(
                romanceStage = stage,
                romanceSinceDay = if (stage == 2 || stage == 4) day else it.romanceSinceDay,
                trust = it.trust + 5,
                memories = it.memories + when (step.id) {
                    "interest" -> if (isTeen) "Lange Gespräche auf dem Dach des Quartiers" else "Ein ehrlicher Abend in der Stadt"
                    "confess" -> "Der Moment, in dem ihr es ausgesprochen habt"
                    "move_in" -> "Die erste gemeinsame Wohnung"
                    "propose" -> "Der Antrag"
                    else -> "Die Entscheidung für eine Familie"
                }
            )
        }
        val text = when (step.id) {
            "interest" -> if (isTeen) "Du und $name verbringt immer mehr Zeit miteinander – beim Lernen, im Aufenthaltsraum, auf dem Dach." else "Zwischen dir und $name ist etwas entstanden."
            "confess" -> if (isTeen) "$name lächelt: „Okay. Wir zwei – aber in Ruhe, Schritt für Schritt.“" else "$name nimmt deine Hand. Ihr seid jetzt ein Paar."
            "move_in" -> "Ihr zieht in deine Wohnung in der Stadt. Ein Stück normales Leben."
            "propose" -> "$name sagt Ja. Die Hochzeit findet im kleinen Kreis statt."
            else -> "Ihr beschließt, eine Familie zu gründen. Die Division gewährt Elternzeit – und ein neues Sicherheitsprotokoll."
        }
        if (step.id == "interest") s = Bonds.addMilestone(data, s, npcId, "interest")
        if (step.id == "confess") {
            s = Bonds.addMilestone(data, s, npcId, "partner")
            // Bewusste Entscheidung: andere Annäherungen enden respektvoll
            otherInterests(data, s, npcId).forEach { other ->
                s = Relations.update(data, s, other) { it.copy(romanceStage = 0, affection = it.affection.coerceAtMost(20), friendship = it.friendship - 3) }
                s = Memories.add(data, s, other, "Du hast dich für ${name.substringBefore(" ")} entschieden", "romance", 3)
                s = s.copy(living = s.living.copy(arcs = s.living.arcs + ("closed_$other" to day), closedRomances = s.living.closedRomances + other))
            }
        }
        if (step.id == "family") s = s.copy(flags = s.flags + "family_started")
        if (step.id == "propose") s = s.copy(flags = s.flags + "married")
        val t = World.passTime(data, s, 1)
        return Result(t.state, listOf(text) + t.messages)
    }
}

object Social {
    data class Interaction(val id: String, val label: String, val description: String, val enabled: Boolean, val reason: String = "")

    fun interactions(data: GameData, state: GameState, npcId: String): List<Interaction> {
        val r = Relations.get(data, state, npcId)
        val today = r.lastTalkDay == state.clock.day
        val energy = state.player.energy
        val list = mutableListOf<Interaction>()
        list += Interaction("smalltalk", "Kurzes Gespräch", "Freundschaft +, 1 Zeitslot", !today, if (today) "Heute schon gesprochen" else "")
        list += Interaction("time", "Zeit verbringen", "Freundschaft & Stressabbau, 1 Zeitslot, 10 Energie", energy >= 10, if (energy < 10) "Zu erschöpft" else "")
        list += Interaction("advice", "Um Rat fragen", "Respekt +, Tipp für deine Laufbahn", r.trust >= 40, if (r.trust < 40) "Vertrauen ≥ 40" else "")
        val conflict = state.living.conflicts[npcId]
        if (conflict != null) list += Interaction("talk_out", "Aussprache", "Konflikt „${conflict.reason}“ klären · Empathie-Probe, 1 Slot", !today && energy >= 10, if (today) "Heute schon gesprochen" else "Zu erschöpft")
        val gifts = Bonds.giftOptions(data, state, npcId)
        if (gifts.isEmpty()) list += Interaction("gift", "Etwas schenken", "Kamera, Headset, Notizbuch, Uhr, Taschenlampe …", false, "Kein passendes Geschenk im Inventar")
        gifts.forEach { g -> list += Interaction("gift:${g.itemId}", "Schenken: ${g.name}", g.reaction + " · Freundschaft +${g.bonus}", true) }
        val r0 = Relations.get(data, state, npcId)
        if (r0.romanceStage >= 1) list += Interaction("stay_friends", if (r0.romanceStage >= 2) "Beziehung beenden" else "Nur Freunde bleiben",
            if (r0.romanceStage >= 2) "Bewusste Trennung – hinterlässt einen Konflikt" else "Ehrlich bleiben: die Freundschaft behalten", true)
        if (npcId == "michael") {
            val lock = Career.mentorAvailable(data, state)
            list += Interaction("m_eval", "Leistungsbeurteilung anfordern", "Note A–E aus Training, Einsätzen und Entwicklung der letzten 30 Tage",
                Career.canRequestEvaluation(state), "Nächste Beurteilung frühestens 30 Tage nach der letzten")
            list += Interaction("m_train", "Einzeltraining mit Michael", "Gezielt an deiner Schwäche, Prüfungsvorbereitung +8, 1 Slot, 25 Energie", lock == null && energy >= 25, lock ?: "Zu erschöpft")
            list += Interaction("m_prep", "Prüfungsvorbereitung", "Szenarien & Theorie, Prüfungsvorbereitung +15, 2 Slots", lock == null && energy >= 20, lock ?: "Zu erschöpft")
        }
        val custom = state.customNpcs.firstOrNull { it.id == npcId }
        if (custom != null) {
            list += Interaction("mentor", "Mentoring", "Gemeinsam lernen: Vertrauen +, Fähigkeiten der Figur wachsen", energy >= 15, "Zu erschöpft")
            val last = state.living.arcs["focus_talk_$npcId"] ?: -999
            val ok = state.clock.day - last >= 14
            Life.focusAreas.forEach { f ->
                if (f != Life.life(data, state, npcId).focus) list += Interaction("focus:$f", "Laufbahn-Rat: ${Life.focusLabel(f)}",
                    "Schwerpunkt des eigenen Trainings ändern (bei abweichendem Berufswunsch Vertrauen ≥ 55)", ok, "Erst in ${14 - (state.clock.day - last)} Tagen wieder")
            }
        }
        if (r.rivalry >= 20) list += Interaction("challenge", "Zum Wettkampf herausfordern", "Fitness-Probe, Respekt oder Rivalität ändern sich", energy >= 20, "Zu erschöpft")
        return list
    }

    fun giftable(data: GameData, state: GameState) = state.player.inventory.filter { it.itemId in Bonds.giftItems && it.uid !in state.player.equipment.values }

    fun interact(data: GameData, state: GameState, npcId: String, id: String): Result {
        val name = Relations.displayName(data, state, npcId)
        val def = data.agentById[npcId]
        val traits = def?.traits ?: state.customNpcs.firstOrNull { it.id == npcId }?.traits ?: emptyList()
        val day = state.clock.day
        if (id.startsWith("gift:")) return Bonds.give(data, state, npcId, id.removePrefix("gift:"))
        if (id.startsWith("focus:")) {
            val r = Life.setFocus(data, state, npcId, id.removePrefix("focus:"))
            return if (r.ok) r.copy(state = Bonds.addMilestone(data, r.state, npcId, "career_talk")) else r
        }
        when (id) {
            "talk_out" -> return Bonds.talkOut(data, state, npcId)
            "stay_friends" -> return Romance.stayFriends(data, state, npcId)
            "m_eval" -> return Career.evaluate(data, state)
            "m_train" -> return Career.mentorTraining(data, state)
            "m_prep" -> return Career.mentorExamPrep(data, state)
            "smalltalk" -> {
                val r = Relations.get(data, state, npcId)
                if (r.lastTalkDay == day) return state.fail("Ihr habt heute schon gesprochen.")
                val line = smalltalkLine(data, state, npcId)
                var s = Relations.update(data, state, npcId) { it.copy(friendship = it.friendship + 2, trust = it.trust + 1, lastTalkDay = day) }
                s = Bonds.addMilestone(data, s, npcId, "first_talk")
                val t = World.passTime(data, s, 1)
                return Result(t.state, listOf("$name: „$line“", "Freundschaft +2, Vertrauen +1") + t.messages)
            }
            "time" -> {
                if (state.player.energy < 10) return state.fail("Zu erschöpft.")
                val romance = Romance.allowed(data, state, npcId)
                val strained = npcId in state.living.conflicts
                var s = Relations.update(data, state, npcId) {
                    it.copy(friendship = it.friendship + if (strained) 2 else 4, trust = it.trust + if (strained) 0 else 2,
                        affection = it.affection + if (romance && !strained) 4 else 0, lastTalkDay = day)
                }
                if (!strained && Relations.get(data, s, npcId).memories.size < 3) s = Memories.add(data, s, npcId, sharedMoment(data, s, npcId), "moment", 2)
                s = s.copy(player = s.player.copy(stress = (s.player.stress - 8).pct(), energy = (s.player.energy - 10).pct()))
                val what = if (Ages.player(state) < 18) "Ihr spielt Karten im Aufenthaltsraum und redet über alles außer Division."
                else "Ihr geht nach dem Dienst etwas essen und redet über Dinge jenseits der Arbeit."
                val t = World.passTime(data, s, 1)
                return Result(t.state, listOf(what, "$name: Freundschaft +4" + if (romance) ", Zuneigung +4" else "", "Stress −8") + t.messages)
            }
            "advice" -> {
                val tip = adviceLine(data, state, npcId)
                val s = Relations.update(data, state, npcId) { it.copy(respect = it.respect + 2, trust = it.trust + 1) }
                val t = World.passTime(data, s, 1)
                return Result(t.state, listOf("$name: „$tip“") + t.messages)
            }
            "gift" -> {
                val st = giftable(data, state).firstOrNull() ?: return state.fail("Kein Geschenk verfügbar.")
                return Bonds.give(data, state, npcId, st.itemId)
            }
            "mentor" -> {
                val npc = state.customNpcs.firstOrNull { it.id == npcId } ?: return state.fail("Nur für eigene Rekruten")
                if (state.player.energy < 15) return state.fail("Zu erschöpft.")
                val key = npc.skills.minByOrNull { it.value }?.key ?: "observe"
                val upgraded = npc.copy(skills = npc.skills + (key to ((npc.skills[key] ?: 0) + 1).coerceAtMost(5)))
                var s = state.copy(customNpcs = state.customNpcs.map { if (it.id == npcId) upgraded else it })
                s = Relations.update(data, s, npcId) { it.copy(trust = it.trust + 3, respect = it.respect + 2, friendship = it.friendship + 2, lastTalkDay = day) }
                s = Bonds.addMilestone(data, s, npcId, "mentor")
                val lf = Life.life(data, s, npcId)
                s = s.copy(living = s.living.copy(lives = s.living.lives + (npcId to lf.copy(careerXp = lf.careerXp + 6))))
                s = s.copy(player = s.player.copy(energy = (s.player.energy - 15).pct()))
                val t = World.passTime(data, s, 1)
                return Result(t.state, listOf("Ihr lernt gemeinsam. ${npc.firstName} verbessert ${data.skillById[key]?.name ?: key}.", "Vertrauen +3, Respekt +2") + t.messages)
            }
            "challenge" -> {
                if (state.player.energy < 20) return state.fail("Zu erschöpft.")
                val (s0, roll) = Checks.roll(data, state, CheckDef(Attr.FITNESS, "defense", 45), salt = 9)
                var s = s0.copy(player = s0.player.copy(energy = (s0.player.energy - 20).pct()))
                s = if (roll.success) Relations.update(data, s, npcId) { it.copy(respect = it.respect + 6, rivalry = it.rivalry - 4) }
                else Relations.update(data, s, npcId) { it.copy(rivalry = it.rivalry + 5, respect = it.respect + 1) }
                val xp = Progression.grantXp(data, s, 30)
                val t = World.passTime(data, xp.state, 1)
                val text = if (roll.success) "Du gewinnst den Wettkampf gegen $name (${roll.roll}/${roll.chance}). Respekt +6" else "$name gewinnt knapp (${roll.roll}/${roll.chance}). Rivalität +5"
                return Result(t.state, listOf(text) + xp.messages + t.messages)
            }
        }
        return state.fail("Unbekannte Interaktion")
    }

    private fun sharedMoment(data: GameData, state: GameState, id: String): String {
        val i = Life.interests(data, state, id)
        return if (i.isEmpty()) "Ein ruhiger Abend im Aufenthaltsraum" else "Ein Nachmittag über ${i[state.clock.day % i.size]}"
    }

    private fun smalltalkLine(data: GameData, state: GameState, id: String): String {
        val lines = when (id) {
            "michael" -> listOf("Schlaf ist Teil des Trainings. Ignorier das nicht.", "Gute Arbeit heute. Morgen besser.", "Wer hier überlebt, lernt zuzuhören.")
            "nikita" -> listOf("Vertrau deinen Instinkten, nicht den Akten.", "Division nimmt dir mehr, als sie dir gibt.")
            "alex" -> listOf("Hast du die neue Rangliste gesehen? Jaden ist unausstehlich.", "Manchmal vermisse ich ganz normale Dinge. Musik. Ein Fenster.")
            "birkhoff" -> listOf("Wenn du was brauchst: Kaffee, dann Fragen. In der Reihenfolge.", "Dein Login hat übrigens ein Passwort wie ein Rentner.")
            "amanda" -> listOf("Wie schläfst du? Ehrlich.", "Lügen sind Werkzeuge. Man muss sie warten wie alles andere.")
            "percy" -> listOf("Effizienz, Rekrut. Alles andere ist Sentimentalität.", "Ich beobachte deine Fortschritte. Enttäusche mich nicht.")
            "thom" -> listOf("Morgen Sparring? Ich zeig dir den Trick mit der Fußarbeit.", "Wenn man oben auf der Liste steht, wird die Luft dünn.")
            "jaden" -> listOf("Glaub nicht, dass du hier Freunde findest.", "Du warst heute nicht schlecht. Für einen Neuen.")
            "owen" -> listOf("Jeder hier hat etwas, das er vergessen will.", "Halt die Augen offen. Immer.")
            "sonya" -> listOf("Die Server laufen heiß. Irgendwer kopiert Archive.", "Birkhoff tut so, als wäre er faul. Ist er nicht.")
            "ryan" -> listOf("In den Mustern steckt immer die Wahrheit.", "Ich habe mir deine Akte angesehen. Oder das, was davon übrig ist.")
            "maya" -> listOf("Ich hab heimlich ein Buch aus der Stadt reingeschmuggelt. Willst du's leihen?", "Ich zeichne die Leute hier. Du bist schwer zu zeichnen.")
            "ethan" -> listOf("Ich hab gehört, du warst vorher woanders. Wo?", "Ich will in die Einsatzgruppe. Egal wie.")
            else -> listOf("Schön, dich zu sehen.", "Wie läuft die Ausbildung?", "Ich lerne hier jeden Tag etwas Neues.")
        }
        return lines[(state.clock.day + id.length) % lines.size]
    }

    private fun adviceLine(data: GameData, state: GameState, id: String): String {
        val check = Progression.nextRankCheck(data, state)
        val missing = check?.requirements?.firstOrNull { !it.second }?.first
        return when (id) {
            "michael" -> if (missing != null) "Für die nächste Stufe fehlt dir noch: $missing." else "Du bist bereit. Melde dich im Direktorat zur Prüfung."
            "birkhoff" -> "Rüste ein Gerät aus. Mit Analyseterminal oder Mobilgerät findest du in Untersuchungen mehr."
            "amanda" -> "Dein Stress liegt bei ${state.player.stress}. Über 80 machst du Fehler. Ruhe ist keine Schwäche."
            "percy" -> "Ruf ist Währung. Du hast ${state.player.reputation}. Verdiene mehr."
            else -> "Trainiere deine Schwäche (${Attr.label(state.player.weakness)}), nicht nur deine Stärke."
        }
    }

    // --- Neuer Agent (Partnereditor ab 18) ----------------------------------

    fun canCreateRecruit(state: GameState): Boolean = Ages.player(state) >= 18 && state.customNpcs.size < 3

    fun createRecruit(
        data: GameData, state: GameState, firstName: String, lastName: String, gender: String,
        avatar: AvatarSpec, traits: List<String>, background: String, profession: String,
        interests: List<String>, skills: Map<String, Int>
    ): Result {
        if (Ages.player(state) < 18) return state.fail("Der Rekrutierungseditor wird mit 18 freigeschaltet.")
        if (state.customNpcs.size >= 3) return state.fail("Maximal drei eigene Rekruten.")
        if (firstName.isBlank()) return state.fail("Bitte einen Vornamen eingeben.")
        val id = "custom_" + (state.customNpcs.size + 1)
        val npc = CustomNpc(
            id = id, firstName = firstName.trim(), lastName = lastName.trim().ifBlank { "Neu" }, gender = gender,
            avatar = avatar, traits = traits.take(3), background = background, profession = profession,
            interests = interests.take(3), skills = skills, createdDay = state.clock.day, ageAtCreation = 16
        )
        var s = state.copy(customNpcs = state.customNpcs + npc)
        s = s.copy(relations = s.relations + (id to Relation(friendship = 10, trust = 15, loyalty = 10, respect = 20)))
        return Result(s, listOf("${npc.name} (16) trifft als neue/r Rekrut/in in der Division ein. Ihr kennt euch noch nicht – Vertrauen muss wachsen."))
    }

    /** v0.6: Rang eigener Figuren kommt aus ihrer eigenen Laufbahn (siehe [Life]). */
    fun customRank(data: GameData, state: GameState, npc: CustomNpc): Int = Life.rank(data, state, npc.id)

    fun professionLabel(id: String) = when (id) {
        "field" -> "Außendienst"
        "intel" -> "Analyse"
        "tech" -> "Technik"
        "medic" -> "Sanitätsdienst"
        else -> id
    }
}

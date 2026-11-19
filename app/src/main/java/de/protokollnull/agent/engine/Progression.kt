package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

object Progression {
    const val MAX_LEVEL = 100

    fun requiredXp(level: Int): Int = 100 + 35 * level + 2 * level * level

    fun grantXp(data: GameData, state: GameState, amount: Int): Result {
        if (amount <= 0) return Result(state)
        var p = state.player
        val mult = when (p.difficulty) { "easy" -> 1.2; "hard" -> 0.85; else -> 1.0 }
        var xp = p.xp + (amount * mult).toInt()
        var level = p.level
        var points = p.skillPoints
        val msgs = mutableListOf("+${(amount * mult).toInt()} XP")
        while (level < MAX_LEVEL && xp >= requiredXp(level)) {
            xp -= requiredXp(level)
            level++
            points += 2
            msgs += "Level $level erreicht! +2 Fähigkeitspunkte"
        }
        if (level >= MAX_LEVEL) { level = MAX_LEVEL; xp = 0 }
        p = p.copy(xp = xp, level = level, skillPoints = points)
        return Result(state.copy(player = p), msgs)
    }

    // --- Fähigkeiten --------------------------------------------------------

    fun skillPrereqsMet(skill: SkillDefinition, p: PlayerCharacter): Boolean {
        val pre = listOfNotNull(skill.prerequisite) + skill.requires
        return pre.all { (p.skills[it] ?: 0) >= 1 } && p.level >= skill.minLevel
    }

    fun learnSkill(data: GameData, state: GameState, skillId: String): Result {
        val skill = data.skillById[skillId] ?: return state.fail("Unbekannte Fähigkeit")
        val p = state.player
        val current = p.skills[skillId] ?: 0
        if (current >= skill.maxRank) return state.fail("${skill.name} ist bereits gemeistert.")
        if (p.skillPoints <= 0) return state.fail("Keine Fähigkeitspunkte verfügbar.")
        if (!skillPrereqsMet(skill, p)) return state.fail("Voraussetzungen für ${skill.name} fehlen.")
        val next = p.copy(skills = p.skills + (skillId to current + 1), skillPoints = p.skillPoints - 1)
        return Result(state.copy(player = next), listOf("${skill.name} → Stufe ${current + 1}"))
    }

    // --- Training -------------------------------------------------------------

    data class Training(val id: String, val name: String, val attribute: String, val energy: Int, val stress: Int, val xp: Int, val skill: String?)

    val trainings = listOf(
        Training("train_fitness", "Ausdauer & Parcours", Attr.FITNESS, 20, 6, 25, "stamina"),
        Training("train_combat", "Selbstverteidigung", Attr.FITNESS, 20, 8, 30, "defense"),
        Training("train_precision", "Präzisionsstand (Markierungsmunition)", Attr.PRECISION, 15, 6, 25, "weapon_handling"),
        Training("train_theory", "Taktiktheorie & Sprachen", Attr.INTELLECT, 12, 4, 25, "language"),
        Training("train_tech", "Systeme & Elektronik", Attr.INTELLECT, 12, 5, 25, "electronics"),
        Training("train_social", "Rollenspiel: Legenden & Gespräche", Attr.CHARISMA, 12, 5, 25, "disguise"),
        Training("train_observe", "Beobachtungsparcours", Attr.PERCEPTION, 12, 4, 25, "observe"),
        Training("train_mind", "Stressimpfung mit Amanda", Attr.WILLPOWER, 15, -6, 25, "resilience")
    )

    data class Intensity(val id: String, val name: String, val energyMult: Double, val stressAdd: Int, val progressMult: Double, val injuryRisk: Int)

    val intensities = listOf(
        Intensity("light", "Leicht", 0.7, -2, 0.7, 0),
        Intensity("normal", "Normal", 1.0, 0, 1.0, 1),
        Intensity("hard", "Intensiv", 1.4, 5, 1.35, 6)
    )

    /** Ausbilder: wer welche Trainingsarten verstärkt (Anwesenheit + Vertrauen nötig). */
    val trainers = mapOf(
        "michael" to setOf(Attr.FITNESS, Attr.PRECISION, Attr.WILLPOWER),
        "owen" to setOf(Attr.PRECISION, Attr.FITNESS),
        "birkhoff" to setOf(Attr.INTELLECT),
        "sonya" to setOf(Attr.INTELLECT, Attr.PERCEPTION),
        "amanda" to setOf(Attr.CHARISMA, Attr.WILLPOWER),
        "ryan" to setOf(Attr.PERCEPTION, Attr.INTELLECT)
    )

    /** Bonus (0..20) eines Ausbilders auf ein Attribut; 0 wenn nicht passend oder kein Vertrauen. */
    fun trainerBonus(data: GameData, state: GameState, trainerId: String?, attribute: String): Int {
        if (trainerId == null) return 0
        val skills = trainers[trainerId] ?: return 0
        if (attribute !in skills) return 0
        val r = Relations.get(data, state, trainerId)
        if (r.trust < 40) return 0
        val competence = (data.agentById[trainerId]?.combat ?: 50) / 10
        return (4 + competence + (r.trust - 40) / 6 + r.respect / 25).coerceIn(0, 20)
    }

    /**
     * Gemeinsame Fortschrittsformel für manuelles Training und Zeitplan-Simulation:
     * abnehmender Ertrag mit steigendem Wert, Obergrenze nach Level.
     * Liefert (neuer Wert, neuer Teilfortschritt).
     */
    fun attributeProgress(current: Int, partial: Int, cap: Int, roll: Int, bonus: Int, mult: Double): Pair<Int, Int> {
        if (current >= cap) return current to 0
        var progress = partial + ((22 + roll + bonus) * mult).toInt() - (current - 30).coerceAtLeast(0) / 2
        progress = progress.coerceAtLeast(partial + 6)
        var gain = 0
        while (progress >= 50) { progress -= 50; gain++ }
        return (current + gain).coerceAtMost(cap) to if (current + gain >= cap) 0 else progress
    }

    /** XP für eine Trainingseinheit sinkt mit dem Level (kein unendlicher Grind). */
    fun trainingXp(base: Int, level: Int): Int = (base * 30 / (30 + level)).coerceAtLeast(2)

    const val DAILY_SESSIONS = 3

    fun train(data: GameData, state: GameState, trainingId: String, partnerId: String? = null, trainerId: String? = null, intensityId: String = "normal"): Result {
        val t = trainings.firstOrNull { it.id == trainingId } ?: return state.fail("Unbekanntes Training")
        val intensity = intensities.firstOrNull { it.id == intensityId } ?: intensities[1]
        val p = state.player
        val energyCost = (t.energy * intensity.energyMult).toInt()
        if (p.energy < energyCost) return state.fail("Zu erschöpft für ${t.name}. Erst ausruhen.")
        if (p.health < 35) return state.fail("Die Krankenstation untersagt Training (Gesundheit zu niedrig).")
        if (p.wounds > 0 && t.attribute == Attr.FITNESS && intensity.id == "hard") return state.fail("Mit einer offenen Verletzung ist intensives Körpertraining untersagt.")
        val day = state.clock.day
        val c = state.career
        val sessions = if (c.sessionsDay == day) c.sessionsToday else 0
        if (sessions >= DAILY_SESSIONS + 1) return state.fail("Für heute ist Schluss: Mehr als ${DAILY_SESSIONS + 1} Einheiten am Tag bringen nichts mehr.")
        val fatigue = if (sessions >= DAILY_SESSIONS) 0.4 else 1.0
        val rng = Rng(Rng.mix(state.seed, day.toLong(), state.clock.slot.toLong(), trainingId.hashCode().toLong(), state.rolls.toLong()))
        val cap = Stats.attributeCap(p.level)
        val current = p.attributes[t.attribute] ?: 30
        val deptBonus = (Directorate.power(data, state, "training") - 1) * 6
        val presentTrainer = trainerId?.takeIf { World.npcLocation(data, state, it) == state.location }
        val tBonus = trainerBonus(data, state, presentTrainer, t.attribute)
        val partnerBonus = if (partnerId != null) 6 else 0
        val (newValue, progress) = attributeProgress(current, p.trainingProgress[t.attribute] ?: 0, cap, rng.nextInt(25), deptBonus + tBonus + partnerBonus, intensity.progressMult * fatigue)
        val msgs = mutableListOf<String>()
        msgs += t.name + " (" + intensity.name + ") abgeschlossen." + if (fatigue < 1) " Müdigkeit: kaum Fortschritt." else ""
        if (newValue > current) msgs += Attr.label(t.attribute) + " " + current + " → " + newValue
        else if (current >= cap) msgs += Attr.label(t.attribute) + " am Limit für Level ${p.level} ($cap)."
        else msgs += "Fortschritt " + Attr.label(t.attribute) + ": ${progress * 2}%"
        if (presentTrainer != null && tBonus > 0) msgs += Relations.displayName(data, state, presentTrainer) + " korrigiert deine Technik (Bonus +$tBonus)"
        var wounds = p.wounds
        if (intensity.injuryRisk > 0 && rng.percent() <= intensity.injuryRisk + (if (p.energy - energyCost < 15) 6 else 0)) {
            wounds += 1
            msgs += "Verletzung beim Training! (Wunden +1)"
        }
        val next = p.copy(
            attributes = p.attributes + (t.attribute to newValue),
            trainingProgress = p.trainingProgress + (t.attribute to progress),
            energy = (p.energy - energyCost).pct(),
            stress = (p.stress + t.stress + intensity.stressAdd).pct(),
            wounds = wounds
        )
        val career = c.copy(
            sessionsDay = day, sessionsToday = sessions + 1,
            trainingDays = (c.trainingDays.filter { it > day - 90 } + day).distinct()
        )
        var s = state.copy(player = next, rolls = state.rolls + 1, career = career)
        val xpRes = grantXp(data, s, (trainingXp(t.xp, p.level) * fatigue).toInt())
        s = xpRes.state
        msgs += xpRes.messages
        if (partnerId != null) {
            s = Relations.update(data, s, partnerId) { it.copy(friendship = it.friendship + 2, respect = it.respect + 1, lastTalkDay = day) }
            msgs += Relations.displayName(data, s, partnerId) + " trainiert mit dir (Freundschaft +2)"
        }
        if (presentTrainer != null && tBonus > 0) s = Relations.update(data, s, presentTrainer) { it.copy(respect = it.respect + 1) }
        s = Career.checkGoals(s, msgs)
        val time = World.passTime(data, s, 1)
        return Result(time.state, msgs + time.messages)
    }

    // --- Ränge --------------------------------------------------------------

    data class RankCheck(val rank: RankDefinition, val requirements: List<Pair<String, Boolean>>) {
        val ready: Boolean get() = requirements.all { it.second }
    }

    fun nextRankCheck(data: GameData, state: GameState): RankCheck? {
        val p = state.player
        val r = data.ranks.firstOrNull { it.rank == p.rank + 1 } ?: return null
        val age = Ages.player(state)
        val req = mutableListOf<Pair<String, Boolean>>()
        req += "Level ${r.minLevel} (aktuell ${p.level})" to (p.level >= r.minLevel)
        req += "Alter ${r.minAge} (aktuell $age)" to (age >= r.minAge)
        req += "Ruf ${r.minReputation} (aktuell ${p.reputation})" to (p.reputation >= r.minReputation)
        r.supervisorId?.let { sup ->
            val t = Relations.get(data, state, sup).trust
            req += "Vertrauen von ${Relations.displayName(data, state, sup)} ≥ ${r.minSupervisorTrust} (aktuell $t)" to (t >= r.minSupervisorTrust)
        }
        r.requiredMissions.forEach { m ->
            req += "Mission „${data.missionById[m]?.title ?: m}“" to (m in state.completedMissions)
        }
        r.requiredFlags.forEach { f -> req += (flagLabels[f] ?: f) to (f in state.flags) }
        if (r.rank in 2..7) {
            val ev = Career.latestEvaluation(state, withinDays = 120)
            req += "Leistungsbeurteilung (≤ 120 Tage) mindestens C (aktuell ${ev?.grade ?: "keine"})" to (ev != null && ev.grade <= "C")
        }
        return RankCheck(r, req)
    }

    val flagLabels = mapOf(
        "ch1_done" to "Kapitel 1 „Neues Blut“ abgeschlossen",
        "prologue_done" to "Ausbildung bei PHOENIX abgeschlossen",
        "team_lead_trial" to "Bewährung als Teamführer",
        "phoenix_truth" to "Wahrheit über PHOENIX aufgedeckt"
    )

    /** Beförderung anwenden (nach bestandener mehrphasiger Prüfung, siehe Career). */
    fun applyPromotion(data: GameData, state: GameState, rank: Int): GameState {
        val p = state.player
        var s = state.copy(
            player = p.copy(rank = rank, skillPoints = p.skillPoints + 1, reputation = p.reputation + 10),
            flags = state.flags + "rank_$rank"
        )
        if (rank >= 10) s = Directorate.ensure(data, s)
        return s
    }

    fun teamSize(data: GameData, state: GameState): Int = data.rank(state.player.rank).teamSize
}

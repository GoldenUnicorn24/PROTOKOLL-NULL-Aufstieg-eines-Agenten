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

    fun train(data: GameData, state: GameState, trainingId: String, partnerId: String? = null): Result {
        val t = trainings.firstOrNull { it.id == trainingId } ?: return state.fail("Unbekanntes Training")
        val p = state.player
        if (p.energy < t.energy) return state.fail("Zu erschöpft für ${t.name}. Erst ausruhen.")
        if (p.health < 35) return state.fail("Die Krankenstation untersagt Training (Gesundheit zu niedrig).")
        val rng = Rng(Rng.mix(state.seed, state.clock.day.toLong(), state.clock.slot.toLong(), trainingId.hashCode().toLong(), state.rolls.toLong()))
        val cap = Stats.attributeCap(p.level)
        val current = p.attributes[t.attribute] ?: 30
        val deptBonus = (state.director?.departments?.get("training") ?: 1) - 1
        val partnerBonus = if (partnerId != null) 4 else 0
        var progress = (p.trainingProgress[t.attribute] ?: 0) + 30 + rng.nextInt(25) + deptBonus * 8 + partnerBonus * 3 -
            (current - 30).coerceAtLeast(0) / 2
        progress = progress.coerceAtLeast(12)
        var gain = 0
        while (progress >= 50) { progress -= 50; gain++ }
        val newValue = if (current >= cap) current else (current + gain).coerceAtMost(cap)
        val msgs = mutableListOf<String>()
        msgs += t.name + " abgeschlossen."
        if (newValue > current) msgs += Attr.label(t.attribute) + " " + current + " → " + newValue
        else if (current >= cap) msgs += Attr.label(t.attribute) + " am Limit für Level ${p.level} ($cap)."
        else msgs += "Fortschritt " + Attr.label(t.attribute) + ": ${progress * 2}%"
        var next = p.copy(
            attributes = p.attributes + (t.attribute to newValue),
            trainingProgress = p.trainingProgress + (t.attribute to progress),
            energy = (p.energy - t.energy).pct(),
            stress = (p.stress + t.stress).pct()
        )
        var s = state.copy(player = next, rolls = state.rolls + 1)
        val xpRes = grantXp(data, s, t.xp + partnerBonus * 2)
        s = xpRes.state
        msgs += xpRes.messages
        if (partnerId != null) {
            s = Relations.update(data, s, partnerId) { it.copy(friendship = it.friendship + 2, respect = it.respect + 1) }
            msgs += Relations.displayName(data, s, partnerId) + " trainiert mit dir (Freundschaft +2)"
        }
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
        return RankCheck(r, req)
    }

    val flagLabels = mapOf(
        "ch1_done" to "Kapitel 1 „Neues Blut“ abgeschlossen",
        "prologue_done" to "Ausbildung bei PHOENIX abgeschlossen",
        "team_lead_trial" to "Bewährung als Teamführer",
        "phoenix_truth" to "Wahrheit über PHOENIX aufgedeckt"
    )

    /** Beförderungsprüfung: Willenskraft/Intellekt-Probe vor dem Gremium. */
    fun attemptPromotion(data: GameData, state: GameState): Result {
        val check = nextRankCheck(data, state) ?: return state.fail("Du hast den höchsten Rang erreicht.")
        if (!check.ready) return state.fail("Noch nicht alle Voraussetzungen für „${check.rank.name}“ erfüllt.")
        if (state.player.energy < 20) return state.fail("Für die Prüfung brauchst du mindestens 20 Energie.")
        val attr = if (check.rank.rank >= 8) Attr.CHARISMA else Attr.WILLPOWER
        val (s1, roll) = Checks.roll(data, state, CheckDef(attr, "planning", check.rank.examDifficulty), salt = 77)
        var s = s1.copy(player = s1.player.copy(energy = (s1.player.energy - 20).pct()))
        return if (roll.success) {
            val p = s.player
            s = s.copy(
                player = p.copy(rank = check.rank.rank, skillPoints = p.skillPoints + 1, reputation = p.reputation + 10),
                flags = s.flags + "rank_${check.rank.rank}"
            )
            if (check.rank.rank == 12 && s.director == null) s = s.copy(director = DirectorState())
            val t = World.passTime(data, s, 2)
            Result(t.state, listOf("Prüfung bestanden (${roll.roll}/${roll.chance}). Neuer Rang: ${check.rank.name}! +1 Fähigkeitspunkt") + t.messages)
        } else {
            s = s.copy(player = s.player.copy(stress = (s.player.stress + 15).pct()))
            val t = World.passTime(data, s, 2)
            Result(t.state, listOf("Prüfung nicht bestanden (${roll.roll}/${roll.chance}). Nächster Versuch nach weiterem Training.") + t.messages, ok = false)
        }
    }

    fun teamSize(data: GameData, state: GameState): Int = data.rank(state.player.rank).teamSize
}

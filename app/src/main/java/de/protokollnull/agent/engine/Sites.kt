package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/**
 * v0.8 HQ-Ausbau und Außenstellen. Die 13 bestehenden HQ-Räume bleiben
 * unverändert; Ausbauten sind Module mit Stufen, Unterhalt und Wirkung.
 * Außenstellen brauchen Personal: ohne Besetzung wirken sie nicht.
 */
object Sites {
    data class Module(val id: String, val name: String, val roomId: String, val effect: String, val minRank: Int, val baseCost: Int, val upkeep: Int)
    val modules = listOf(
        Module("training_hall", "Erweiterte Trainingshalle", "training", "Trainierendes Personal +1 Karrierepunkt je Stufe und Tag.", 8, 3000, 300),
        Module("analysis_room", "Analyseraum", "ops", "Ab Stufe 2: eigene Einsätze +1 Intel; Stufe 3: zusätzliche Hinweise.", 8, 3500, 300),
        Module("tech_workshop", "Technikwerkstatt", "tech", "+2 je Stufe auf Intellekt-Proben im Einsatz.", 9, 4000, 350),
        Module("med_ward", "Erweiterte Krankenstation", "medical", "Verletztes Personal erholt sich schneller; besserer Schlaf (+2 Gesundheit je Stufe).", 8, 3500, 300),
        Module("security_center", "Sicherheitszentrale", "director", "Ab Stufe 2: eigene Einsätze starten mit Alarm −1; Lecks seltener.", 9, 4500, 350),
        Module("lounge", "Aufenthaltsbereich", "canteen", "Weniger Stress beim Schlafen, bessere Stimmung des Personals.", 8, 2500, 200)
    )
    val moduleById = modules.associateBy { it.id }

    data class Outpost(
        val id: String, val name: String, val region: String, val description: String, val minRank: Int,
        val buildCost: Int, val upkeep: Int, val staffNeed: List<Int>, val area: String, val effects: List<String>
    )
    val outposts = listOf(
        Outpost("safehouse", "Stadtsafehouse", "city", "Rückzugsort in der Innenstadt für Erholung und verdeckte Treffen.", 8, 4000, 400, listOf(1, 1, 2), "social",
            listOf("Stufe 1: Einsätze in Innenstadt/Wohnung +3 je Stufe", "Stufe 2: Team −5 Stress pro Woche", "Stufe 3: Team −10 Stress pro Woche, sicherer Treffpunkt für Nikita")),
        Outpost("analysis", "Analysezentrum", "embassy", "Auswertung abgefangener Daten und Musterabgleich.", 9, 7000, 600, listOf(1, 2, 2), "intel",
            listOf("Stufe 1: eigene Einsätze +1 Intel, Delegationen +3 % je Stufe", "Stufe 2: zusätzliche Hinweise bei erfüllten Aufträgen", "Stufe 3: neue Aufträge eine Gefahrenstufe leichter")),
        Outpost("base", "Sanitäts- und Ausbildungsstützpunkt", "hospital", "Rehabilitation und Ausbildung außerhalb des HQ.", 10, 9000, 700, listOf(1, 2, 3), "medic",
            listOf("Stufe 1: Training +1 Karrierepunkt je Stufe und Tag", "Stufe 2: Verletzte genesen schneller, Verletzungsrisiko bei Delegationen sinkt", "Stufe 3: +5 Gesundheit pro Woche für dich")),
        Outpost("harbor", "Beobachtungsposten Hafen", "harbor", "Dauerbeobachtung des Industriehafens.", 11, 6000, 500, listOf(1, 1, 2), "intel",
            listOf("Stufe 1: Black Meridian −1 Einfluss pro Woche je Stufe", "Stufe 2: Einsätze im Hafen +4", "Stufe 3: frühe Warnung vor Gegenoffensiven"))
    )
    val outpostById = outposts.associateBy { it.id }

    fun siteName(id: String) = outpostById[id]?.name ?: id

    // --- HQ-Module ----------------------------------------------------------------------

    fun moduleLevel(state: GameState, id: String) = state.lead.hqModules[id] ?: 0
    fun moduleCost(m: Module, level: Int) = m.baseCost * (level + 1)

    fun upgradeModule(data: GameData, state: GameState, id: String): Result {
        val m = moduleById[id] ?: return state.fail("Unbekannter Ausbau")
        if (state.player.rank < m.minRank) return state.fail("Ab Rang ${m.minRank} (${data.rank(m.minRank).name}).")
        val lvl = moduleLevel(state, id)
        if (lvl >= 3) return state.fail("${m.name} ist voll ausgebaut.")
        if (state.player.rank < 10 && lvl >= 1) return state.fail("Weitere Stufen ab Rang 10.")
        val cost = moduleCost(m, lvl)
        if (Directorate.budget(state) < cost) return state.fail("Etat reicht nicht ($cost Cr).")
        var s = Directorate.spend(state, cost)
        s = s.copy(lead = s.lead.copy(hqModules = s.lead.hqModules + (id to lvl + 1)))
        return Result(Command.log(s, "${m.name} auf Stufe ${lvl + 1}"), listOf("${m.name} auf Stufe ${lvl + 1} ausgebaut (−$cost Cr Etat, Unterhalt ${m.upkeep * (lvl + 1)} Cr/Woche)."))
    }

    // --- Außenstellen -----------------------------------------------------------------------

    /** Wirksame Stufe: geöffnet und so weit besetzt, wie die Stufe Personal verlangt. */
    fun siteLevel(data: GameData, state: GameState, id: String): Int {
        val o = outpostById[id] ?: return 0
        val st = state.lead.sites[id] ?: return 0
        if (!st.open) return 0
        val staffed = st.staff.count { npc -> state.living.lives[npc]?.status.let { it == null || LifeStatus.inHq(it) && it != LifeStatus.INJURED } }
        var lvl = 0
        for (l in 1..st.level) if (staffed >= o.staffNeed[l - 1]) lvl = l
        return lvl
    }

    fun build(data: GameData, state: GameState, id: String): Result {
        val o = outpostById[id] ?: return state.fail("Unbekannte Außenstelle")
        if (state.player.rank < o.minRank) return state.fail("Ab Rang ${o.minRank} (${data.rank(o.minRank).name}).")
        val st = state.lead.sites[id]
        if (st != null && st.open) return state.fail("Bereits in Betrieb.")
        val cost = if (st == null) o.buildCost else o.buildCost / 4
        if (Directorate.budget(state) < cost) return state.fail("Etat reicht nicht ($cost Cr).")
        var s = Directorate.spend(state, cost)
        val ns = st?.copy(open = true, openedDay = s.clock.day, staff = emptyList()) ?: SiteState(level = 1, open = true, openedDay = s.clock.day)
        s = s.copy(lead = s.lead.copy(sites = s.lead.sites + (id to ns)))
        s = Ops.worldLog(s, "${o.name} ${if (st == null) "eröffnet" else "wiedereröffnet"}.")
        return Result(Command.log(s, "${o.name} ${if (st == null) "eröffnet" else "wiedereröffnet"}"),
            listOf("${o.name} ${if (st == null) "eröffnet" else "wiedereröffnet (Stufe ${ns.level})"} (−$cost Cr). Ohne Personal bleibt sie wirkungslos."))
    }

    fun upgrade(data: GameData, state: GameState, id: String): Result {
        val o = outpostById[id] ?: return state.fail("Unbekannte Außenstelle")
        val st = state.lead.sites[id] ?: return state.fail("Noch nicht eröffnet.")
        if (!st.open) return state.fail("Geschlossen.")
        if (st.level >= 3) return state.fail("Höchste Stufe erreicht.")
        if (st.level >= 2 && state.player.rank < 11) return state.fail("Stufe 3 ab Rang 11.")
        val cost = o.buildCost * st.level
        if (Directorate.budget(state) < cost) return state.fail("Etat reicht nicht ($cost Cr).")
        var s = Directorate.spend(state, cost)
        s = s.copy(lead = s.lead.copy(sites = s.lead.sites + (id to st.copy(level = st.level + 1))))
        return Result(Command.log(s, "${o.name} auf Stufe ${st.level + 1}"), listOf("${o.name} auf Stufe ${st.level + 1} (−$cost Cr). Personalbedarf: ${o.staffNeed[st.level]}."))
    }

    fun close(data: GameData, state: GameState, id: String): Result {
        val o = outpostById[id] ?: return state.fail("Unbekannte Außenstelle")
        val st = state.lead.sites[id] ?: return state.fail("Noch nicht eröffnet.")
        if (!st.open) return state.fail("Bereits geschlossen.")
        var s = state.copy(lead = state.lead.copy(sites = state.lead.sites + (id to st.copy(open = false, staff = emptyList()))))
        st.staff.forEach { npc -> s = Relations.update(data, s, npc) { it.copy(memories = it.memories + "Die Schließung von ${o.name}") } }
        s = Ops.worldLog(s, "${o.name} geschlossen.")
        return Result(Command.log(s, "${o.name} geschlossen"), listOf("${o.name} geschlossen. Das Personal kehrt ins HQ zurück; der Unterhalt entfällt. Wiedereröffnung für ${o.buildCost / 4} Cr."))
    }

    fun assignStaff(data: GameData, state: GameState, id: String, npcId: String): Result {
        val o = outpostById[id] ?: return state.fail("Unbekannte Außenstelle")
        val st = state.lead.sites[id] ?: return state.fail("Noch nicht eröffnet.")
        if (!st.open) return state.fail("Geschlossen.")
        if (st.staff.size >= 3) return state.fail("Voll besetzt.")
        if (npcId in st.staff) return state.fail("Bereits dort eingesetzt.")
        if (npcId !in Command.staff(data, state)) return state.fail("Nicht in deinem Personal.")
        if (Ages.npc(data, state, npcId) < 18) return state.fail("Außenstellen nur mit volljährigem Personal.")
        val status = Command.status(data, state, npcId)
        if (!status.available || status.trainingOnly) return state.fail("${Relations.displayName(data, state, npcId).substringBefore(" ")}: ${status.label}")
        val (ok, why) = Personnel.willAccept(data, state, npcId, o.area, id.hashCode().toLong() + state.clock.day / 14)
        if (!ok) return Result(Personnel.refuse(state, npcId), listOf("${Relations.displayName(data, state, npcId)} $why."), ok = false)
        var s = state.copy(team = state.team - npcId, lead = state.lead.copy(roles = state.lead.roles - npcId,
            sites = state.lead.sites + (id to st.copy(staff = st.staff + npcId))))
        s = Relations.update(data, s, npcId) { it.copy(respect = it.respect + 3, memories = it.memories + "Einsatz in ${o.name}") }
        return Result(Command.log(s, "${Relations.displayName(data, s, npcId)} → ${o.name}"), listOf("${Relations.displayName(data, s, npcId)} arbeitet jetzt in ${o.name}."))
    }

    fun removeStaff(data: GameData, state: GameState, id: String, npcId: String): Result {
        val st = state.lead.sites[id] ?: return state.fail("Noch nicht eröffnet.")
        if (npcId !in st.staff) return state.fail("Nicht dort eingesetzt.")
        val s = state.copy(lead = state.lead.copy(sites = state.lead.sites + (id to st.copy(staff = st.staff - npcId))))
        return Result(Command.log(s, "${Relations.displayName(data, s, npcId)} verlässt ${siteName(id)}"), listOf("${Relations.displayName(data, s, npcId)} kehrt ins HQ zurück."))
    }

    // --- Wirkungen -----------------------------------------------------------------------

    /** Zusatzbonus für Proben in eigenen Einsätzen (Region und Attribut). */
    fun checkBonus(data: GameData, state: GameState, region: String?, attribute: String): Int {
        var b = 0
        val safe = siteLevel(data, state, "safehouse")
        if (safe > 0 && region in setOf("city", "safehouse")) b += 3 * safe
        if (siteLevel(data, state, "harbor") >= 2 && region == "harbor") b += 4
        if (attribute == Attr.INTELLECT) b += 2 * moduleLevel(state, "tech_workshop") + if ("proj_field_kit" in state.flags) 4 else 0
        return b
    }

    /** Zusätzliche Start-Intel eigener Einsätze. */
    fun startIntel(data: GameData, state: GameState): Int =
        (if (siteLevel(data, state, "analysis") >= 1) 1 else 0) + (if (moduleLevel(state, "analysis_room") >= 2) 1 else 0) + (if ("proj_source_network" in state.flags) 1 else 0)

    /** Alarmsenkung zu Einsatzbeginn. */
    fun startAlarmReduction(data: GameData, state: GameState): Int =
        (if (moduleLevel(state, "security_center") >= 2) 1 else 0) + (if ("proj_secure_net" in state.flags) 1 else 0) + (if (state.director?.policy == "secrecy") 1 else 0)

    /** Täglich: schnellere Genesung durch Krankenstation und Stützpunkt. */
    fun daily(data: GameData, state: GameState): GameState {
        val med = moduleLevel(state, "med_ward") + (if (siteLevel(data, state, "base") >= 2) 1 else 0)
        if (med <= 0) return state
        val day = state.clock.day
        if (day % (5 - med.coerceAtMost(3)) != 0) return state
        return state.copy(living = state.living.copy(lives = state.living.lives.mapValues { (_, l) ->
            if (l.status == LifeStatus.INJURED && l.statusUntil > day + 1) l.copy(statusUntil = l.statusUntil - 1) else l
        }))
    }

    /** Wöchentlich (in der Abrechnung): Erholung, Weltlage, Gesundheit. */
    fun weekly(data: GameData, state: GameState, lines: MutableList<String>): GameState {
        var s = state
        val safe = siteLevel(data, s, "safehouse")
        if (safe >= 2) {
            val amount = if (safe >= 3) 10 else 5
            s.team.forEach { id -> val l = Life.life(data, s, id); s = s.copy(living = s.living.copy(lives = s.living.lives + (id to l.copy(stress = (l.stress - amount).pct())))) }
            lines += "Stadtsafehouse: Team −$amount Stress"
        }
        val harbor = siteLevel(data, s, "harbor")
        if (harbor > 0) { s = Ops.shiftInfluence(s, "syndicate", -harbor); lines += "Beobachtungsposten Hafen: Black Meridian −$harbor Einfluss" }
        if (siteLevel(data, s, "base") >= 3) { s = s.copy(player = s.player.copy(health = (s.player.health + 5).coerceAtMost(100))); lines += "Stützpunkt: +5 Gesundheit" }
        val lounge = moduleLevel(s, "lounge")
        if (lounge > 0) s = s.copy(living = s.living.copy(lives = s.living.lives.mapValues { (_, l) -> l.copy(mood = (l.mood + lounge).pct()) }))
        // Unbesetzte Außenstellen sind ein Thema im Bericht
        s.lead.sites.filter { it.value.open && siteLevel(data, s, it.key) == 0 }.keys.forEach { lines += "${siteName(it)}: ohne ausreichendes Personal wirkungslos" }
        return s
    }
}

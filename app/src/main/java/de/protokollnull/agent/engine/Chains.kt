package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

/** v0.7 Optionale Einsatzketten (assets/game/ops_chains.json) mit Wahlfolgen. */
object Chains {
    const val MAX_ACTIVE = 3

    fun step(data: GameData, chainId: String, step: Int): ChainStep? = data.chainById[chainId]?.steps?.getOrNull(step)

    fun progress(state: GameState, chainId: String) = state.ops.chains[chainId] ?: 0

    fun decisionFlag(chainId: String, step: Int, decision: String) = "chain_${chainId}_${step}_$decision"

    fun cooldown(state: GameState, chainId: String): GameState =
        state.copy(ops = state.ops.copy(chains = state.ops.chains + ("$chainId#cool" to state.clock.day + 3)))

    fun available(data: GameData, state: GameState, def: ChainDefinition): Boolean {
        val p = progress(state, def.id)
        if (p >= def.steps.size) return false
        if (Ages.player(state) < def.minAge) return false
        if (!Conds.met(def.conditions, data, state)) return false
        if ((state.ops.chains["${def.id}#cool"] ?: -1) > state.clock.day) return false
        val st = def.steps[p]
        if (st.clientNpc != null) {
            val npc = st.clientNpc
            val ok = if (npc == "nikita") "nikita_contact" in state.flags else Ops.available(data, state, npc) && ("met_$npc" in state.flags || state.relations.containsKey(npc))
            if (!ok) return false
        }
        return true
    }

    /** Bietet je verfügbarer Kette den nächsten Schritt an (höchstens [MAX_ACTIVE] gleichzeitig). */
    fun offer(data: GameData, state: GameState): GameState {
        var s = state
        var active = s.ops.contracts.count { it.chainId != null && !ContractStatus.closed(it.status) }
        data.chains.forEach { def ->
            if (active >= MAX_ACTIVE) return@forEach
            if (s.ops.contracts.any { it.chainId == def.id }) return@forEach
            if (!available(data, s, def)) return@forEach
            val p = progress(s, def.id)
            val st = def.steps[p]
            val arch = Ops.archetypeById[st.archetype] ?: return@forEach
            val base = Ops.generate(data, s, s.clock.day, s.ops.counter, Rng.mix(s.seed, def.id.hashCode().toLong(), p.toLong(), 717), forced = arch) ?: return@forEach
            val obj = arch.objectives.firstOrNull { it.id == st.objective } ?: arch.objectives.first()
            val maxD = if (Ages.player(s) >= 18) 5 else 2
            val c = base.copy(
                title = "${def.title} · ${p + 1}/${def.steps.size}: ${st.title}", regionId = st.regionId, objectiveId = obj.id,
                targetFaction = def.faction, clientNpc = st.clientNpc ?: base.clientNpc, briefing = st.briefing,
                difficulty = st.difficulty.coerceAtMost(maxD), urgency = 1, expiresDay = s.clock.day + 10, phases = 1,
                chainId = def.id, chainStep = p, complicationId = "none", entryId = "handover",
                mapId = TacticalMaps.forRegion(st.regionId).first().id,
                successConsequence = "Die Geschichte „${def.title}“ geht weiter.", failConsequence = "Die Kette stockt – in einigen Tagen gibt es einen neuen Anlauf."
            )
            s = s.copy(ops = s.ops.copy(contracts = s.ops.contracts + c, counter = s.ops.counter + 1))
            active++
        }
        return s
    }

    fun onStepDone(data: GameData, state: GameState, c: Contract): Pair<GameState, List<String>> {
        val id = c.chainId ?: return state to emptyList()
        val def = data.chainById[id] ?: return state to emptyList()
        val st = def.steps.getOrNull(c.chainStep) ?: return state to emptyList()
        var s = state
        val notes = mutableListOf<String>()
        val decision = st.decisions.firstOrNull { decisionFlag(id, c.chainStep, it.id) in s.flags }
        if (decision != null) {
            Effs.apply(decision.effects, data, s, st.clientNpc).let { s = it.state; notes += it.messages }
            decision.npcEffects.forEach { (npc, e) -> Effs.apply(e, data, s, npc).let { s = it.state; notes += it.messages } }
            decision.factionInfluence.forEach { (f, d) ->
                s = Ops.shiftInfluence(s, f, d)
                notes += "${Ops.factionName(data, f)} Einfluss ${Effs.signed(d)}"
            }
            decision.clue?.let { Cases.addClue(s, it, def.title).let { (ns, n) -> s = ns; n?.let { notes += it } } }
            s = Ops.worldLog(s, "${def.title}: ${decision.text}")
        }
        val np = c.chainStep + 1
        s = s.copy(ops = s.ops.copy(chains = s.ops.chains + (id to np) + ("$id#cool" to s.clock.day + 1)))
        if (np >= def.steps.size) {
            s = s.copy(flags = s.flags + "chain_${id}_done")
            s = Ops.worldLog(s, "Einsatzkette abgeschlossen: ${def.title}")
            notes += "Einsatzkette „${def.title}“ abgeschlossen."
        } else notes += "Einsatzkette „${def.title}“: Schritt $np/${def.steps.size} – der nächste folgt in Kürze."
        return s to notes
    }
}

package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

object CompanionAssignment {
    private const val id = "assigned_companion"
    private const val introduced = "companion_introduced"

    fun dossier() = CustomNpc(
        id = id, firstName = "Mira", lastName = "Kessler", gender = "female",
        avatar = AvatarSpec(skin = 2, hairStyle = 3, hairColor = 2, outfit = 4),
        traits = listOf("neugierig", "loyal"), interests = listOf("Fotografie"),
        background = "Jahrelang im Fundbüro ausgeholfen. Erkennt Menschen oft an den Dingen, die sie verlieren.",
        profession = "intel", skills = mapOf("observe" to 2, "empathy" to 1),
        createdDay = Clock.daysUntilAge(0, 18)
    )

    fun arrive(data: GameData, state: GameState): GameState {
        val dossier = state.pendingCompanion ?: return state
        if (Ages.player(state) < 18 || state.phase != "game") return state
        val npc = dossier.copy(id = id, createdDay = Clock.daysUntilAge(0, 18))
        val relation = Relation(trust = 30, friendship = 10, respect = 20)
        val hasSpace = state.team.size < Progression.teamSize(data, state)
        return state.copy(
            pendingCompanion = null, customNpcs = state.customNpcs + npc,
            relations = state.relations + (id to relation),
            team = if (hasSpace) state.team + id else state.team
        )
    }

    fun awaitingIntroduction(state: GameState): CustomNpc? =
        if (introduced in state.flags) null else state.customNpcs.find { it.id == id }

    fun introduce(data: GameData, state: GameState, offerTour: Boolean): Result {
        val npc = awaitingIntroduction(state) ?: return state.fail("Die Zuteilung ist bereits abgeschlossen.")
        val memory = if (offerTour) "Der erste Rundgang: Du hast mir den Platz am Fenster gezeigt."
            else "Unser erster Dienst: Wir haben vereinbart, keine Lücken in einem Bericht zu verschweigen."
        val s = Relations.update(data, state, id) {
            it.copy(friendship = it.friendship + if (offerTour) 4 else 0,
                respect = it.respect + if (offerTour) 0 else 4, memories = it.memories + memory)
        }
        return Result(s.copy(flags = s.flags + introduced), listOf("Michaels Zuteilung: ${npc.name} ist jetzt dein Kontakt in der Division."))
    }
}

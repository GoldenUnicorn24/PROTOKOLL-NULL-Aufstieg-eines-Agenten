package de.protokollnull.agent.engine

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.model.*

object Prologue {
    fun node(data: GameData, state: GameState): StoryNode? = data.storyById[state.prologueNode]

    fun choose(data: GameData, state: GameState, choiceId: String): Result {
        val node = node(data, state) ?: return finish(data, state)
        val choice = node.choices.firstOrNull { it.id == choiceId } ?: return state.fail("Auswahl fehlt")
        var s = state
        val msgs = mutableListOf<String>()
        val legacy = Effects(trust = choice.trustDelta, xp = choice.xp, stress = choice.stressDelta)
        val r1 = Effs.apply(legacy, data, s, node.speakerId); s = r1.state; msgs += r1.messages
        val r2 = Effs.apply(choice.effects, data, s, node.speakerId); s = r2.state; msgs += r2.messages
        val next = choice.nextNode
        return if (next == null || data.storyById[next] == null) {
            val f = finish(data, s); Result(f.state, msgs + f.messages)
        } else Result(s.copy(prologueNode = next), msgs)
    }

    fun skip(data: GameData, state: GameState): Result = finish(data, state)

    /** Übergang ins freie Spiel mit 16: Startausrüstung, erster Quest, Ankunftsdialog. */
    fun finish(data: GameData, state: GameState): Result {
        if (state.phase == "game") return Result(state)
        var s = state.copy(phase = "game", flags = state.flags + "prologue_done", location = "dorm", clock = GameClock(0, 0))
        listOf("id_card" to 1, "radio_basic" to 1, "bandage" to 2, "notebook" to 1, "backpack_small" to 1, "watch" to 1).forEach { (id, q) ->
            s = Inventory.add(data, s, id, q).state
        }
        s.player.inventory.firstOrNull { it.itemId == "backpack_small" }?.let { s = Inventory.equip(data, s, it.uid).state }
        s.player.inventory.firstOrNull { it.itemId == "radio_basic" }?.let { s = Inventory.equip(data, s, it.uid).state }
        s = MissionGenerator.refreshBoard(data, s)
        s = Quests.update(data, s).state
        val d = Dialogues.start(data, s, "dlg_arrival")
        return Result(d.state, listOf("Drei Jahre PHOENIX liegen hinter dir. Deine alte Identität ist verschlüsselt.", "Willkommen in der Division."))
    }
}

object Dialogues {
    fun available(data: GameData, state: GameState, npcId: String): List<DialogueDefinition> =
        data.dialogues.filter { it.npcId == npcId && (!it.once || it.id !in state.completedDialogues) && Conds.met(it.conditions, data, state, npcId) }
            .sortedByDescending { it.priority }

    fun pendingStory(data: GameData, state: GameState): DialogueDefinition? =
        data.dialogues.filter { it.npcId == null && it.id !in state.completedDialogues && Conds.met(it.conditions, data, state) }
            .maxByOrNull { it.priority }

    fun start(data: GameData, state: GameState, id: String): Result {
        val d = data.dialogueById[id] ?: return state.fail("Dialog fehlt: $id")
        if (d.once && id in state.completedDialogues) return state.fail("Bereits geführt.")
        return Result(state.copy(activeDialogue = DialogueRun(id, d.startNode)))
    }

    fun current(data: GameData, state: GameState): Pair<DialogueDefinition, DialogueNode>? {
        val run = state.activeDialogue ?: return null
        val d = data.dialogueById[run.dialogueId] ?: return null
        val n = d.nodes.firstOrNull { it.id == run.nodeId } ?: return null
        return d to n
    }

    fun visibleChoices(data: GameData, state: GameState): List<DialogueChoice> {
        val (d, n) = current(data, state) ?: return emptyList()
        return n.choices.filter { Conds.met(it.conditions, data, state, n.speakerId ?: d.npcId) }
    }

    fun choiceChance(data: GameData, state: GameState, c: DialogueChoice): Int? = c.check?.let { Stats.checkChance(data, state, it) }

    fun choose(data: GameData, state: GameState, choiceId: String): Result {
        val (d, n) = current(data, state) ?: return state.fail("Kein Dialog aktiv")
        val c = n.choices.firstOrNull { it.id == choiceId } ?: return state.fail("Auswahl fehlt")
        val target = n.speakerId?.takeIf { it != "narrator" && it != "player" } ?: d.npcId
        var s = state
        val msgs = mutableListOf<String>()
        var next = c.nextNode
        var effects = c.effects
        var resultLine = ""
        if (c.check != null) {
            val (s1, roll) = Checks.roll(data, s, c.check, salt = d.id.hashCode().toLong())
            s = s1
            if (!roll.success) { next = c.failNode ?: next; effects = c.failEffects }
            resultLine = (if (roll.success) "Probe gelungen" else "Probe misslungen") + " (${roll.roll}/${roll.chance})"
            msgs += resultLine
        }
        val legacy = Effects(trust = c.trustDelta, xp = c.xp, stress = c.stressDelta)
        val r1 = Effs.apply(legacy, data, s, target); s = r1.state; msgs += r1.messages
        val r2 = Effs.apply(effects, data, s, target); s = r2.state; msgs += r2.messages
        val run = s.activeDialogue!!
        val hist = (run.history + ("» " + c.text)).takeLast(20)
        val nextNode = next?.let { id -> d.nodes.firstOrNull { it.id == id } }
        return if (nextNode == null) {
            s = s.copy(
                activeDialogue = null,
                completedDialogues = s.completedDialogues + d.id,
                flags = if (d.completeFlag != null) s.flags + d.completeFlag else s.flags
            )
            if (target != null && Relations.isKnownNpc(data, s, target)) {
                s = Relations.update(data, s, target) { it.copy(lastTalkDay = s.clock.day) }
            }
            val q = Quests.update(data, s)
            Result(q.state, msgs + q.messages)
        } else {
            Result(s.copy(activeDialogue = run.copy(nodeId = nextNode.id, lastResult = resultLine, history = hist)), msgs)
        }
    }

    fun format(text: String, state: GameState): String = text
        .replace("{codename}", state.player.codename)
        .replace("{name}", state.player.firstName)
        .replace("{fullname}", state.player.displayName)
}

object Quests {
    fun active(data: GameData, state: GameState): List<QuestDefinition> =
        data.quests.filter { it.completeFlag !in state.flags && Conds.met(it.conditions, data, state) }

    fun completed(data: GameData, state: GameState): List<QuestDefinition> =
        data.quests.filter { it.completeFlag in state.flags }

    fun objectiveDone(state: GameState, o: QuestObjective): Boolean =
        (o.flag == null || o.flag in state.flags) &&
            (o.missionId == null || o.missionId in state.completedMissions) &&
            (o.dialogueId == null || o.dialogueId in state.completedDialogues)

    /** Prüft abgeschlossene Quests (mehrfach, da Quests verkettet sind). */
    fun update(data: GameData, state: GameState): Result {
        var s = state
        val msgs = mutableListOf<String>()
        var changed = true
        var guard = 0
        while (changed && guard++ < 20) {
            changed = false
            for (q in active(data, s)) {
                if (q.objectives.all { objectiveDone(s, it) }) {
                    s = s.copy(flags = s.flags + q.completeFlag)
                    val r = Effs.apply(q.reward, data, s, q.giverId)
                    s = r.state
                    msgs += "Auftrag abgeschlossen: ${q.title}"
                    msgs += r.messages
                    changed = true
                }
            }
        }
        return Result(s, msgs)
    }
}

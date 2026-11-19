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
        val c = visibleChoices(data, state).firstOrNull { it.id == choiceId } ?: return state.fail("Diese Antwort ist nicht verfügbar.")
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
        val hist = (run.history + ("» " + format(c.text, state))).takeLast(20)
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

/** Enden und Epilog: aus Story-Flags und Beziehungen zusammengesetzt. */
object Endings {
    data class Section(val title: String, val text: String)

    fun endingId(state: GameState): String? =
        listOf("ending_reform", "ending_order", "ending_shadow", "ending_exit").firstOrNull { it in state.flags }

    fun pending(state: GameState): Boolean = endingId(state) != null && "epilogue_seen" !in state.flags

    fun title(id: String?): String = when (id) {
        "ending_reform" -> "Ende: Im Licht"
        "ending_order" -> "Ende: Die neue Ordnung"
        "ending_shadow" -> "Ende: Phönix"
        "ending_exit" -> "Ende: Spurlos"
        else -> "Epilog"
    }

    fun epilogue(data: GameData, state: GameState): List<Section> {
        val id = endingId(state) ?: return emptyList()
        val f = state.flags
        val out = mutableListOf<Section>()
        out += Section(title(id), when (id) {
            "ending_reform" -> "Die Anhörungen dauern ein Jahr. Am Ende steht eine Division mit Gesetz, Aufsicht und Grenzen – und du an ihrer Spitze, öffentlich vereidigt. Niemand wird mehr abgeschaltet. Manche nennen dich naiv. Die Rekruten nennen dich die erste Direktion, die ihnen ihre Namen zurückgegeben hat."
            "ending_order" -> "Die Division bleibt im Schatten, aber die Schatten haben neue Regeln. Keine Abschaltungen, keine Kinder in Programmen, keine Lügen innerhalb der Mauern. Die Aufsicht ahnt, dass du etwas verbirgst. Sie ahnt auch, dass die Welt sicherer geworden ist."
            "ending_shadow" -> "PHOENIX steigt aus der Asche – effizienter, verborgener, deins. Sieben neue Kandidaten, sieben neue Nummern. Manchmal, spät in der Nacht, ertappst du dich dabei, wie du leise sprichst und selten lobst."
            else -> "Die Enthüllung beherrscht wochenlang die Schlagzeilen. Die Division wird aufgelöst, Percy angeklagt, Untersuchungsausschüsse tagen. Du bist zu diesem Zeitpunkt längst jemand anderes, irgendwo am Meer, mit einem Namen, den nur zwei Menschen kennen."
        })
        val voss = when {
            "voss_arrested" in f -> "Adrian Voss sitzt in einem Hochsicherheitstrakt und schreibt Briefe, die nie beantwortet werden."
            "voss_ally" in f -> "Voss steht an deiner Seite, ein Schritt hinter dir – genau dort, wo er immer stehen wollte."
            "voss_free" in f -> "Voss ist verschwunden. Die sieben Namen, die er dir gab, haben sieben Leben gerettet."
            "voss_escaped" in f -> "Voss ist entkommen. Gelegentlich erhältst du Postkarten ohne Absender."
            else -> null
        }
        if (voss != null) out += Section("Adrian Voss", voss)
        val michael = Relations.get(data, state, "michael")
        out += Section("Michael", when {
            id == "ending_shadow" -> "Michael hat die Division am Tag deiner Entscheidung verlassen. Er hat sich nicht verabschiedet."
            michael.trust >= 70 -> "Michael leitet weiter die Ausbildung. Wenn neue Rekruten fragen, wer der Beste war, den er je trainiert hat, zeigt er wortlos auf ein Foto von dir."
            michael.trust >= 40 -> "Michael bleibt Ausbilder. Ihr redet selten, aber wenn, dann ehrlich."
            else -> "Michael hat um seine Versetzung gebeten. Er glaubt nicht mehr, dass du die bist, die er ausgebildet hat."
        })
        val partner = Romance.partnerId(data, state)
        if (partner != null) {
            val r = Relations.get(data, state, partner)
            val name = Relations.displayName(data, state, partner)
            out += Section(name, when {
                "family_started" in f -> "Mit $name hast du eine Familie gegründet. Die Division hat ein neues Sicherheitsprotokoll für Kinder von Agenten – du hast es selbst geschrieben."
                r.romanceStage >= 4 -> "$name und du seid verheiratet. Euer Küchentisch ist der einzige Ort, an dem nie über Einsätze gesprochen wird."
                r.romanceStage >= 3 -> "$name und du wohnt zusammen, mit einem Fenster zur Stadt."
                else -> "$name ist an deiner Seite – Tag für Tag, Einsatz für Einsatz."
            })
        }
        if ("kira_rescued" in f) out += Section("Kira", if ("kira_joined" in f) "Kira ist deine Stellvertreterin geworden. Kandidatin 4 und Kandidat 7, am Ende doch frei." else "Kira lebt unter neuem Namen in Skandinavien. Sie schreibt einmal im Jahr.")
        if ("kira_rescued" in f && ("kira_key" in f || "kira_plums" in f)) {
            out += Section("Der Brief hinter der Leiste", when {
                "kira_plums" in f -> "Kira schickt dir einen Kassenzettel. Eine Flasche Milch, ein Glas Pflaumen, Spülmittel. Unten hat sie die Summe zweimal unterstrichen. Auf der Rückseite steht in ihrer kleinen, schiefen Schrift: „Viel zu teuer.“"
                else -> "Auf Kiras erster Postkarte ist ein Schlüssel abgepaust. Ein paar Kratzer, drei Zähne. „Wohnung im dritten Stock“, steht darunter, „mit einer Tür, die ich von innen zumachen kann.“"
            })
        }
        if ("commission_admitted" in f) {
            out += Section("Der Abzug", when (id) {
                "ending_reform", "ending_exit" -> "Unter den ersten Menschen, die vor dem Ausschuss aussagen, ist eine Frau aus der Aufnahmekommission. Sie identifiziert die Bahnhofsfotos und gibt zu Protokoll, wofür die Abzüge bestimmt waren. Die Familien dürfen ihre Exemplare abholen."
                "ending_order" -> "Du lässt die Abzüge aus den Bewerbungsakten nehmen. Wer sein Foto abholen will, muss am Empfang nur seinen alten Namen nennen. Auf der Liste der Abholberechtigten stehst auch du."
                else -> "Die Mappe mit den Bahnhofsfotos liegt jetzt in deinem Schreibtisch. Du weißt, wer die Bilder bestellt hat. Auf dem Formular für den nächsten Auftrag ist die Unterschriftszeile noch frei."
            })
        }
        if ("nikita_ally" in f) out += Section("Nikita", "Nikita taucht manchmal auf, ungefragt, mit einem Kaffee und einer Warnung. Ihr seid keine Freundinnen oder Freunde. Aber Verbündete.")
        val birkhoff = Relations.get(data, state, "birkhoff")
        if (birkhoff.trust >= 60) out += Section("Birkhoff", "Birkhoff hat dein Büro verwanzt, um zu beweisen, dass er es kann. Dann hat er dir die Wanzen geschenkt. Freundschaft auf seine Art.")
        if ("percy_exposed" in f) out += Section("Percy", "Percy verbringt seine Tage vor Ausschüssen und Anwälten. Er lächelt immer noch, wenn er deinen Namen hört.")
        else if ("percy_deal" in f) out += Section("Percy", "Percy hat seine Pension angetreten und schreibt angeblich Memoiren, die nie erscheinen werden.")
        val friends = state.relations.count { it.value.friendship >= 70 }
        out += Section("Bilanz", "Level ${state.player.level}, ${state.completedMissions.size} abgeschlossene Einsätze, $friends enge Freundschaften. Tag ${state.clock.day + 1} deiner Laufbahn.")
        return out
    }
}

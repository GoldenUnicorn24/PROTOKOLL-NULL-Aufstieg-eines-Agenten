package de.protokollnull.agent.engine

import de.protokollnull.agent.model.MissionRun

/** Das sichtbare Signalmuster entsteht aus demselben Einsatz wie die gespeicherte Lösung. */
data class ObservationSignal(val reference: List<String>, val fragment: List<String>, val channels: List<List<String>>) {
    companion object {
        private val symbols = listOf("Kreis", "Raute", "Stern", "Kreuz", "Welle", "Blitz")

        fun forRound(run: MissionRun): ObservationSignal? {
            val ob = run.observe ?: return null
            val correct = ob.correct.getOrNull(ob.round) ?: return null
            if (correct !in ob.targets.indices) return null
            val rng = Rng(Rng.mix(run.seed, run.stageId.hashCode().toLong(), ob.round.toLong()))
            val reference = rng.shuffled(symbols).take(rng.range(3, 5))
            val offset = rng.range(1, reference.lastIndex)
            val answer = listOf(reference[offset], reference[(offset + 1) % reference.size])
            val otherPairs = reference.flatMap { a -> reference.map { b -> listOf(a, b) } }.filter { it != answer }
            val distractors = rng.shuffled(otherPairs)
            val channels = MutableList(ob.targets.size - 1) { distractors[it % distractors.size] }
            channels.add(correct, answer)
            return ObservationSignal(reference, reference.take(offset), channels)
        }
    }
}

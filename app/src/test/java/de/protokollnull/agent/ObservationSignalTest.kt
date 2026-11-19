package de.protokollnull.agent

import de.protokollnull.agent.engine.Missions
import de.protokollnull.agent.engine.ObservationSignal
import de.protokollnull.agent.model.*
import org.junit.Assert.*
import org.junit.Test

class ObservationSignalTest {
    @Test fun visibleSequenceDeterminesOneChannelAndMissionCreditsIt() {
        val data = TestData.data
        val mission = data.missions.first { it.id == "M008" }
        val stage = mission.stages.first { it.type == "observe" }
        val seen = mutableSetOf<List<String>>()
        for (seed in 1L..80L) {
            val targets = stage.targets
            val ob = ObserveState(rounds = stage.rounds, targets = targets,
                correct = targets.indices.map { (it + seed.toInt()) % targets.size }.take(stage.rounds))
            var state = GameState(activeMission = MissionRun(mission.id, seed, stage.id, observe = ob))
            repeat(stage.rounds) { round ->
                val run = state.activeMission!!
                val puzzle = ObservationSignal.forRound(run)!!
                assertEquals(puzzle, ObservationSignal.forRound(run.copy(log = listOf("Spiel geladen"))))
                seen += puzzle.reference
                val stream = puzzle.reference + puzzle.reference
                assertEquals(stream.take(puzzle.fragment.size), puzzle.fragment)
                val expected = stream.drop(puzzle.fragment.size).take(2)
                val index = puzzle.channels.indices.single { puzzle.channels[it] == expected }
                state = Missions.observe(data, state, index).state
                if (round < stage.rounds - 1) assertEquals(round + 1, state.activeMission!!.observe!!.hits)
            }
            assertEquals(stage.rounds, state.activeMission!!.intel)
        }
        assertTrue(seen.size > 10)
    }
}

package de.protokollnull.agent

import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Waffenbalancing und wiederholte gemeinsame Einsätze. */
class WeaponRanksTeamBondTest {
    private val data = TestData.data

    @Test fun weaponRankRequirementsAreExactlyTwoRanksLower() {
        val expected = mapOf(
            "pistol_service" to 2,
            "ars_p99" to 3,
            "ars_mp7" to 5,
            "ars_hk416" to 5,
            "ars_scarl" to 6,
            "ars_sr25" to 6,
            "ars_mk14" to 7,
            "ars_stunbaton" to 1
        )
        expected.forEach { (id, rank) ->
            val item = data.itemById[id]
            assertNotNull(id, item)
            assertEquals(id, rank, item!!.minRank)
        }
        val weapons = data.items.filter { it.category == "weapons" }
        assertEquals(27, weapons.size)
        assertTrue("Waffenränge bleiben mindestens 1", weapons.all { it.minRank >= 1 })
        assertEquals(1, data.itemById.getValue("pistol_training").minRank)
        // Nicht-Waffen behalten die früheren Ranganforderungen.
        assertEquals(2, data.itemById.getValue("vest_light").minRank)
    }

    @Test fun recurringMissionsBuildRealFriendshipBetweenTwoAgents() {
        val mission = MissionDefinition(id = "team_bond_test", title = "Zusammenarbeit",
            locationId = "hq", type = "test", threat = 2)
        var s = GameState(seed = 199L)
        val key = LivingState.key("alex", "michael")
        val before = Life.bond(s, "alex", "michael")
        val firstFriendship = Relations.get(data, s, "alex").friendship
        repeat(10) { i ->
            val run = MissionRun(mission.id, (i + 100L), "done",
                team = listOf("alex", "michael"))
            val (next, _) = Ops.afterMission(data, s, mission, run, "success")
            s = next
            assertEquals(i + 1, s.living.sharedMissions[key])
        }
        assertTrue("Häufige gemeinsame Einsätze verstärken die gegenseitige Freundschaft",
            Life.bond(s, "alex", "michael") > before + 50)
        assertTrue("Freundschaft zum Spieler wächst", Relations.get(data, s, "alex").friendship > firstFriendship)
        assertEquals(10, Life.life(data, s, "alex").missions)
        assertEquals(10, Life.life(data, s, "michael").missions)

        // Neue Felder müssen in alten Spielständen fehlen dürfen und in neuen erhalten bleiben.
        val save = Json.encodeToString(GameState.serializer(), s)
        val restored = Json.decodeFromString(GameState.serializer(), save)
        assertEquals(s.living.sharedMissions, restored.living.sharedMissions)
        assertEquals(Life.bond(s, "alex", "michael"), Life.bond(restored, "alex", "michael"))
    }

    @Test fun failuresCreateSmallSharedExperienceButAbortedMissionsNone() {
        val mission = MissionDefinition(id = "team_bond_failure", title = "Probe",
            locationId = "hq", type = "test", threat = 1)
        val s = GameState(seed = 151L)
        val key = LivingState.key("alex", "michael")
        val run = MissionRun(mission.id, 11L, "done", team = listOf("michael", "alex"))
        val (failed, _) = Ops.afterMission(data, s, mission, run, "fail")
        assertEquals(1, failed.living.sharedMissions[key])
        assertEquals(Life.bond(s, "alex", "michael") + 2, Life.bond(failed, "alex", "michael"))
        val (duplicate, _) = Ops.afterMission(data, failed, mission, run, "fail")
        assertEquals(failed, duplicate)

        val (aborted, _) = Ops.afterMission(data, s, mission,
            run.copy(seed = 12L), "abort")
        assertTrue(aborted.living.sharedMissions.isEmpty())
        assertEquals(Life.bond(s, "alex", "michael"), Life.bond(aborted, "alex", "michael"))
    }

    @Test fun soloMissionsDoNotInventNpcFriendships() {
        val mission = MissionDefinition(id = "team_bond_solo", title = "Einzel",
            locationId = "hq", type = "test", threat = 1)
        val s = GameState(seed = 151L)
        val (after, _) = Ops.afterMission(data, s, mission,
            MissionRun(mission.id, 12L, "done", team = listOf("alex")), "success")
        assertTrue(after.living.sharedMissions.isEmpty())
    }
}

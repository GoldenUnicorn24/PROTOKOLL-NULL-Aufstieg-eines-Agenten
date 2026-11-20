package de.protokollnull.agent

import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Freundschaftsabhängiges gemeinsames Training: beide Figuren verbessern sich nachweisbar. */
class SharedTrainingTest {
    private val d = TestData.data

    private fun state(friendship: Int): GameState {
        var s = GameState(seed = 867L, player = PlayerCharacter(firstName = "Probe", codename = "Null"))
        s = Prologue.finish(d, s).state.copy(
            activeDialogue = null, location = "training", clock = GameClock(day = 10, slot = 1)
        )
        s = s.copy(player = s.player.copy(energy = 100), flags = s.flags + "met_alex" + "met_michael")
        return Relations.update(d, s, "alex") { it.copy(friendship = friendship, trust = 70) }
    }

    private fun score(p: PlayerCharacter, attr: String = Attr.FITNESS) =
        (p.attributes[attr] ?: 30) * 50 + (p.trainingProgress[attr] ?: 0)

    @Test fun alexIsAvailableInTrainingHallInTheMorning() {
        val s = state(85)
        assertEquals("training", World.npcLocation(d, s, "alex"))
        assertNull(Progression.partnerLock(d, s, "alex"))
        assertEquals("Enge Freunde", Progression.partnerBoost(d, s, "alex")?.label)
    }

    @Test fun closeFriendsBothImproveMassivelyComparedToTrainingAlone() {
        val s = state(85)
        val solo = Progression.train(d, s, "train_combat")
        val together = Progression.train(d, s, "train_combat", partnerId = "alex")
        assertTrue(together.messages.toString(), together.ok)
        assertTrue("deutlich mehr Attributfortschritt", score(together.state.player) > score(solo.state.player) + 20)
        assertTrue("mehr Spieler-XP", together.state.player.xp > solo.state.player.xp)
        val trained = Life.life(d, together.state, "alex")
        assertTrue("NPC verdient Karrierepunkte", trained.careerXp >= 50)
        assertTrue("NPC lernt Kompetenz", (trained.skillGain["combat"] ?: 0) > 0 || (trained.skillProgress["combat"] ?: 0) > 0)
        assertEquals("NPC-Trainingstag verbucht", 1, trained.trainingDays)
        assertTrue("Freundschaft verbessert", Relations.get(d, together.state, "alex").friendship > 85)
        assertEquals("Keine automatische Romanze", 0, Relations.get(d, together.state, "alex").romanceStage)
        assertTrue("Training als gemeinsame Erinnerung", Memories.book(together.state, "alex").entries.any { it.type == "training" })
        assertEquals("Eine Einheit", 1, together.state.career.sessionsToday)
    }

    @Test fun friendshipTierChangesBothPlayerAndNpcGrowth() {
        val acquaintance = Progression.train(d, state(30), "train_fitness", partnerId = "alex")
        val friends = Progression.train(d, state(60), "train_fitness", partnerId = "alex")
        val close = Progression.train(d, state(85), "train_fitness", partnerId = "alex")
        assertTrue(acquaintance.ok && friends.ok && close.ok)
        assertTrue(score(close.state.player) > score(friends.state.player))
        assertTrue(score(friends.state.player) > score(acquaintance.state.player))
        assertTrue(Life.life(d, close.state, "alex").careerXp > Life.life(d, friends.state, "alex").careerXp)
        assertTrue(Life.life(d, friends.state, "alex").careerXp > Life.life(d, acquaintance.state, "alex").careerXp)
    }

    @Test fun impossibleOrBusyPartnerCannotGenerateFreeBonus() {
        val s = state(90)
        val absent = s.copy(clock = GameClock(day = 10, slot = 2))
        val before = Progression.train(d, absent, "train_fitness", partnerId = "alex")
        assertFalse(before.ok)
        assertEquals(absent, before.state)
        assertFalse(Progression.train(d, s, "train_fitness", partnerId = "unknown_agent").ok)
        assertFalse(Progression.train(d, s, "train_fitness", partnerId = "michael", trainerId = "michael").ok)
        val life = Life.life(d, s, "alex")
        val deployed = s.copy(living = s.living.copy(lives = s.living.lives +
            ("alex" to life.copy(status = LifeStatus.MISSION, statusUntil = 12))))
        assertFalse(Progression.train(d, deployed, "train_fitness", partnerId = "alex").ok)
    }

    @Test fun trainingIsDeterministicAndSharedProgressSurvivesSave() {
        val initial = state(85)
        val a = Progression.train(d, initial, "train_theory", partnerId = "alex").state
        val b = Progression.train(d, initial, "train_theory", partnerId = "alex").state
        assertEquals(a, b)
        val json = Json.encodeToString(GameState.serializer(), a)
        val restored = Json.decodeFromString(GameState.serializer(), json)
        assertEquals(Life.life(d, a, "alex"), Life.life(d, restored, "alex"))
        assertEquals(a.player.attributes, restored.player.attributes)
        assertEquals(a.player.trainingProgress, restored.player.trainingProgress)
    }

    @Test fun friendshipCannotBypassDailySessionLimit() {
        var s = state(85)
        repeat(4) {
            val r = Progression.train(d, s.copy(player = s.player.copy(energy = 100)), "train_theory", partnerId = "alex", intensityId = "light")
            assertTrue("Einheit $it: ${r.messages}", r.ok)
            s = r.state.copy(clock = GameClock(day = 10, slot = 1))
        }
        val before = s
        val extra = Progression.train(d, before, "train_theory", partnerId = "alex", intensityId = "light")
        assertFalse(extra.ok)
        assertEquals(before, extra.state)
    }
    @Test fun bestFriendsGrowEveryAttributeForBothFigures() {
        val initial = state(96)
        val beforeNpc = Life.attributeRatings(d, initial, "alex")
        val completed = Progression.train(d, initial, "train_fitness", partnerId = "alex")
        assertTrue(completed.messages.toString(), completed.ok)
        assertEquals("Unzertrennliches Team", Progression.partnerBoost(d, initial, "alex")?.label)
        val npc = Life.life(d, completed.state, "alex")
        for (a in Attr.all.filter { it != Attr.FITNESS }) {
            assertTrue("Spieler-Nebenfortschritt $a", (completed.state.player.trainingProgress[a] ?: 0) > 0)
            assertTrue("NPC-Nebenfortschritt $a", (npc.attributeProgress[a] ?: 0) > 0)
            assertTrue("NPC-Attribut gespeichert $a", (npc.attributes[a] ?: 0) >= (beforeNpc[a] ?: 0))
        }
        assertTrue("NPC-Hauptattribut wächst", (npc.attributes[Attr.FITNESS] ?: 0) >= (beforeNpc[Attr.FITNESS] ?: 0))
    }

    @Test fun michaelAndAlexBecomeFriendsThroughGroupTraining() {
        var initial = state(96)
        initial = Relations.update(d, initial, "michael") { it.copy(friendship = 90, trust = 85) }
        assertNull(Progression.partnerLock(d, initial, "michael"))
        val baseline = Life.bond(initial, "alex", "michael")
        val session = Progression.train(d, initial, "train_combat", partnerId = "alex", trainerId = "michael")
        assertTrue(session.messages.toString(), session.ok)
        assertTrue("Michael und Alex bauen echte Freundschaft auf", Life.bond(session.state, "alex", "michael") > baseline)
        assertTrue("Alex gewinnt Prüfungsreife", Life.life(d, session.state, "alex").careerXp >= 65)
    }

    @Test fun teamActivitiesGiveBothNpcsCareerProgressAndRealNpcBonds() {
        var initial = state(95)
        initial = Relations.update(d, initial, "michael") { it.copy(friendship = 90, trust = 85) }
        val baseline = Life.bond(initial, "michael", "alex")
        val r = Progression.teamActivity(d, initial, "michael", "alex", "course")
        assertTrue(r.messages.toString(), r.ok)
        assertTrue(Life.life(d, r.state, "alex").careerXp > 0)
        assertTrue(Life.life(d, r.state, "michael").careerXp > 0)
        assertTrue(Life.bond(r.state, "michael", "alex") >= baseline + 12)
        assertEquals(1, r.state.career.sessionsToday)
        assertEquals(0, Relations.get(d, r.state, "alex").romanceStage)
        assertFalse(Progression.teamActivity(d, initial, "alex", "alex", "course").ok)
    }

    @Test fun teamMissionAcceleratesExamAndCannotRewardTwice() {
        var initial = state(95)
        initial = Relations.update(d, initial, "michael") { it.copy(friendship = 85) }
        val m = MissionDefinition(id = "test_cooperation", title = "Testeinsatz", locationId = "hq", type = "test", threat = 2)
        val run = MissionRun("test_cooperation", 748L, "done", team = listOf("alex", "michael"))
        val baseline = Life.bond(initial, "alex", "michael")
        val (after, notes) = Ops.afterMission(d, initial, m, run, "success")
        assertTrue(notes.toString(), notes.any { "Prüfungs-XP" in it })
        assertTrue(Life.life(d, after, "alex").careerXp >= 65)
        assertTrue(Life.bond(after, "alex", "michael") > baseline)
        val (repeat, _) = Ops.afterMission(d, after, m, run, "success")
        assertEquals("Einsatzbelohnungen dürfen nicht doppelt vergeben werden", after, repeat)
    }

    @Test fun codenameIsOptionalAndOldNameCanBeCleared() {
        val p = PlayerCharacter(firstName = "Lena", lastName = "Berg", codename = "")
        assertEquals("Lena Berg", p.identityName)
        assertEquals("Hallo Lena Berg!", Dialogues.format("Hallo {codename}!", GameState(player = p)))
        val oldSave = p.copy(codename = "Null")
        assertEquals("Null", oldSave.identityName)
        assertEquals("Lena Berg", oldSave.copy(codename = "").identityName)
    }

    @Test fun ratingGrowsBeyondOneHundredWithoutOverflow() {
        assertTrue(Stats.attributeCap(1) > 100)
        assertEquals(101 to 9, Progression.growRating(100, 49, 10))
    }

}

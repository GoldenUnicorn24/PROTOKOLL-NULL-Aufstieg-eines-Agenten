package de.protokollnull.agent

import de.protokollnull.agent.data.GameData
import de.protokollnull.agent.engine.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

object TestData {
    val root: File = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.exists() }
    val files: Set<String> = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path.replace('\\', '/') }.toSet()
    val data: GameData by lazy { GameData.load({ path -> File(root, path).readText() }, files) }
}

/** Automatische Validierung aller Kataloge, Assetpfade, IDs und Graphen. */
class ContentValidationTest {
    private val d = TestData.data

    private fun unique(name: String, ids: List<String>) {
        val dup = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("$name: doppelte IDs $dup", dup.isEmpty())
    }

    @Test fun catalogSizes() {
        assertTrue(d.items.size >= 102)
        assertTrue(d.agents.size >= 14)
        assertTrue(d.skills.size >= 27)
        assertEquals(12, d.ranks.size)
        assertEquals((1..12).toList(), d.ranks.map { it.rank })
        assertEquals(9, d.skills.map { it.tree }.distinct().size)
    }

    @Test fun idsAreUnique() {
        unique("items", d.items.map { it.id }); unique("agents", d.agents.map { it.id })
        unique("skills", d.skills.map { it.id }); unique("missions", d.missions.map { it.id })
        unique("dialogues", d.dialogues.map { it.id }); unique("quests", d.quests.map { it.id })
        unique("rooms", d.hq.rooms.map { it.id }); unique("story", d.story.map { it.id })
    }

    @Test fun assetPathsExist() {
        d.items.forEach { assertTrue("Icon fehlt: ${it.icon}", "items/${it.icon}.svg" in TestData.files) }
        d.agents.forEach { a -> a.portraitAsset?.let { assertTrue("Porträt fehlt: $it", it in TestData.files) } }
        d.agents.filter { it.seriesCharacter }.forEach { assertTrue("Serienfigur ohne Foto: ${it.id}", d.portraitFor(it.id) != null) }
        d.agents.filter { !it.seriesCharacter }.forEach { assertTrue("Fiktive Figur ohne Avatar: ${it.id}", it.avatar != null) }
    }

    @Test fun noUnlicensedSeriesStills() {
        // show_<id>.jpg darf nur mit verifiziertem Lizenzbeleg existieren
        TestData.files.filter { it.startsWith("cast/show_") }.forEach { f ->
            val id = f.removePrefix("cast/show_").removeSuffix(".jpg")
            assertTrue("Serienstill ohne Lizenznachweis: $f", id in d.licensedSeriesStills)
        }
        d.agents.forEach { assertTrue(d.portraitFor(it.id)?.startsWith("cast/show_") != true || it.id in d.licensedSeriesStills) }
    }

    @Test fun referencesResolve() {
        val itemIds = d.itemById.keys; val agentIds = d.agentById.keys; val roomIds = d.roomById.keys
        d.agents.forEach { a -> a.schedule.values.forEach { r -> assertTrue("${a.id}: Raum $r", r == "away" || r in roomIds) } }
        d.missions.forEach { m ->
            assertTrue("Ort ${m.locationId}", m.locationId in d.placeById.keys)
            m.giverId?.let { assertTrue("Auftraggeber $it", it in agentIds) }
            m.recommendedItems.forEach { assertTrue("Item $it", it in itemIds) }
            m.rewardItems.keys.forEach { assertTrue("Item $it", it in itemIds) }
            m.requires?.missionsDone?.forEach { assertTrue("Mission $it", it in d.missionById.keys) }
            assertTrue("Mission ${m.id} ohne Stages", m.stages.isNotEmpty())
            val stageIds = m.stages.map { it.id }.toSet()
            val ends = setOf("END_SUCCESS", "END_FAIL", "END_ABORT")
            m.stages.forEach { st ->
                st.next?.let { assertTrue("${m.id}/${st.id} next $it", it in stageIds || it in ends) }
                st.options.forEach { o ->
                    listOfNotNull(o.success.next, o.failure?.next).forEach { n -> assertTrue("${m.id}/${st.id}/${o.id} -> $n", n in stageIds || n in ends) }
                    o.success.effects?.giveItems?.keys?.forEach { assertTrue(it in itemIds) }
                }
                if (st.type == "tactical") {
                    val map = st.tactical!!.map
                    assertTrue("${m.id}: Spieler-Spawn", map.any { 'P' in it })
                    assertTrue("${m.id}: Gegner", map.any { 'E' in it })
                    if (st.tactical!!.objective == "reach") assertTrue("${m.id}: Ziel X", map.any { 'X' in it })
                }
                if (st.type == "search") assertTrue(st.spots.size >= st.cluesNeeded + 1)
                if (st.type == "observe") assertTrue(st.targets.size >= 2)
                if (st.type == "choice") assertTrue("${m.id}/${st.id} ohne Optionen", st.options.isNotEmpty())
            }
        }
        d.dialogues.forEach { dl ->
            val nodes = dl.nodes.map { it.id }.toSet()
            assertTrue("${dl.id} Start", dl.startNode in nodes)
            dl.npcId?.let { assertTrue("${dl.id} NPC $it", it in agentIds) }
            dl.nodes.forEach { n ->
                n.speakerId?.let { assertTrue("${dl.id} Sprecher $it", it in agentIds) }
                n.choices.forEach { c ->
                    c.nextNode?.let { assertTrue("${dl.id}/${n.id} -> $it", it in nodes) }
                    c.failNode?.let { assertTrue("${dl.id}/${n.id} fail -> $it", it in nodes) }
                    c.effects?.giveItems?.keys?.forEach { assertTrue("${dl.id} Item $it", it in itemIds) }
                    c.effects?.otherTrust?.keys?.forEach { assertTrue(it in agentIds) }
                }
            }
            // keine unerreichbaren Knoten
            val reached = mutableSetOf(dl.startNode)
            var frontier = listOf(dl.startNode)
            while (frontier.isNotEmpty()) {
                frontier = frontier.flatMap { id -> dl.nodes.first { it.id == id }.choices.flatMap { listOfNotNull(it.nextNode, it.failNode) } }.filter { reached.add(it) }
            }
            assertEquals("${dl.id}: unerreichbare Knoten", nodes, reached)
        }
        d.quests.forEach { q ->
            q.objectives.forEach { o ->
                o.missionId?.let { assertTrue("${q.id} Mission $it", it in d.missionById.keys) }
                o.dialogueId?.let { assertTrue("${q.id} Dialog $it", it in d.dialogueById.keys) }
            }
        }
        d.skills.forEach { s -> (listOfNotNull(s.prerequisite) + s.requires).forEach { assertTrue("${s.id} -> $it", it in d.skillById.keys) } }
        d.hq.rooms.forEach { r -> r.requiredItem?.let { assertTrue(it in itemIds) }; r.actions.forEach { a -> assertTrue("Aktion $a", World.actionInfo(a).hint.isNotEmpty()) } }
        d.events.forEach { e -> e.npcId?.let { assertTrue(it in agentIds) } }
    }

    @Test fun prologueGraphIsComplete() {
        val ids = d.storyById.keys
        val reached = mutableSetOf("prolog_01")
        var frontier = listOf("prolog_01")
        while (frontier.isNotEmpty()) {
            frontier = frontier.flatMap { id -> d.storyById.getValue(id).choices.mapNotNull { it.nextNode } }.filter { it in ids && reached.add(it) }
        }
        assertEquals("Unerreichbare Prolog-Knoten", ids, reached)
        d.story.forEach { n -> n.choices.forEach { c -> c.nextNode?.let { assertTrue("Prolog -> $it", it in ids) } } }
        assertTrue("Prolog muss enden", d.story.any { n -> n.choices.any { it.nextNode == null } })
    }

    @Test fun chapterOneHasTenMissions() {
        val ch1 = d.missions.filter { it.chapter.startsWith("Kapitel 1") }
        assertEquals(10, ch1.size)
        assertTrue(ch1.all { it.minAge <= 17 })
        val types = ch1.flatMap { m -> m.stages.map { it.type } }.toSet()
        assertTrue(types.containsAll(listOf("choice", "search", "observe", "tactical")))
    }
}

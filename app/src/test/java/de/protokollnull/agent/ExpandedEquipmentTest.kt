package de.protokollnull.agent

import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import org.junit.Assert.*
import org.junit.Test

/** Vollständige Tests für das neue Arsenal und alle Original-Icons. */
class ExpandedEquipmentTest {
    private val d = TestData.data
    private fun start(): GameState {
        val s = GameState(seed = 42L, player = PlayerCharacter(firstName = "Test", codename = "Null"))
        return Prologue.finish(d, s).state.copy(activeDialogue = null)
    }
    @Test fun originalIconsAndItemCatalogAreComplete() {
        val added = d.items.filter { it.id.startsWith("ars_") || it.id.startsWith("kit_") }
        assertEquals(60, added.size)
        assertEquals(24, added.count { it.category == "weapons" })
        assertEquals(36, added.count { it.category != "weapons" })
        assertEquals(102, d.items.size)
        assertEquals(60, added.map { it.icon }.distinct().size)
        val slots = setOf("none", "weapon", "armor", "gadget", "comms", "bag", "accessory")
        added.forEach { def ->
            assertTrue("SVG fehlt: ${def.icon}", "items/${def.icon}.svg" in TestData.files)
            assertTrue("Slot ${def.id}", def.slot in slots)
            assertTrue("Dienstgrad ${def.id}", def.minRank in 1..12)
            assertTrue("Gewicht ${def.id}", def.weightGrams > 0)
            assertTrue("Stapel ${def.id}", def.maxStack > 0)
            assertTrue("Preis ${def.id}", def.baseValue >= 0)
        }
    }
    @Test fun minorCannotBuyOrEquipOperationalWeapons() {
        val start = start()
        val teen = start.copy(player = start.player.copy(rank = 12, credits = 100000))
        assertEquals(16, Ages.player(teen))
        assertFalse(Inventory.buy(d, teen, "ars_glock17").ok)
        val found = Inventory.add(d, teen, "ars_glock17", 1).state
        val stack = found.player.inventory.first { it.itemId == "ars_glock17" }
        assertNotNull(Inventory.canEquip(d, found, stack))
        assertFalse(Inventory.equip(d, found, stack.uid).ok)
    }
    @Test fun adultCanBuyAndEquipNewWeaponWithRealTacticalStats() {
        val start = start()
        val adult = start.copy(clock = GameClock(day = 730),
            player = start.player.copy(rank = 9, credits = 250000))
        val bought = Inventory.buy(d, adult, "ars_m4a1")
        assertTrue(bought.ok)
        val stack = bought.state.player.inventory.first { it.itemId == "ars_m4a1" }
        assertNull(Inventory.canEquip(d, bought.state, stack))
        val equipped = Inventory.equip(d, bought.state, stack.uid)
        assertTrue(equipped.ok)
        assertEquals(stack.uid, equipped.state.player.equipment["weapon"])
        assertTrue(Stats.equipped(d, equipped.state.player).any { it.id == "ars_m4a1" })
        assertTrue(Inventory.compare(d, equipped.state, stack.uid).any { it.label == "Wirkung" })
    }
    @Test fun newConsumableWorksAndIsRemoved() {
        val start = start()
        val injured = start.copy(player = start.player.copy(health = 37))
        val acquired = Inventory.add(d, injured, "kit_trauma_kit", 1).state
        val stack = acquired.player.inventory.first { it.itemId == "kit_trauma_kit" }
        val used = Inventory.use(d, acquired, stack.uid)
        assertTrue(used.ok)
        assertTrue(used.state.player.health > 37)
        assertEquals(0, Inventory.count(used.state, "kit_trauma_kit"))
    }
    @Test fun storyEvidenceCannotBePurchased() {
        val start = start()
        val adult = start.copy(clock = GameClock(day = 730), player = start.player.copy(rank = 9, credits = 10000))
        assertFalse(Inventory.buy(d, adult, "kit_cipher_disk").ok)
        assertEquals(0, d.itemById.getValue("kit_cipher_disk").baseValue)
    }
}

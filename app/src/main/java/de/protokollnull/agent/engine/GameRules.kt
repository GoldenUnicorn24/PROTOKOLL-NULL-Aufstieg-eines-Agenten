package de.protokollnull.agent.engine

import de.protokollnull.agent.model.*

/** Pure and deterministic rules. Replace or expand with tested systems. */
object GameRules {
    fun requiredXp(level: Int): Int = 100 + (level * level * 40)

    fun grantXp(player: PlayerState, gained: Int): PlayerState {
        var p = player
        var remaining = (p.xp + gained.coerceAtLeast(0))
        var next = p.level
        var points = p.skillPoints
        while (remaining >= requiredXp(next) && next < 100) {
            remaining -= requiredXp(next)
            next++
            points += 2
        }
        return p.copy(xp = remaining, level = next, skillPoints = points)
    }

    fun advanceDay(player: PlayerState, days: Int = 1): PlayerState {
        val delta = days.coerceIn(1, 365)
        return player.copy(
            elapsedDays = player.elapsedDays + delta,
            energy = (player.energy + 15 * delta).coerceIn(0, 100),
            stress = (player.stress - 5 * delta).coerceIn(0, 100)
        )
    }

    fun train(player: PlayerState, skillId: String): PlayerState {
        val current = player.skills[skillId] ?: 0
        if (player.energy < 10 || current >= 5 || player.skillPoints <= 0) return player
        return player.copy(
            skills = player.skills + (skillId to (current + 1)),
            skillPoints = player.skillPoints - 1,
            energy = player.energy - 10
        )
    }

    fun addItem(player: PlayerState, id: String, quantity: Int): PlayerState {
        if (quantity <= 0) return player
        val existing = player.inventory.firstOrNull { it.itemId == id }
        val next = if (existing == null) {
            player.inventory + InventoryStack(id, quantity)
        } else {
            player.inventory.map {
                if (it.itemId == id) it.copy(quantity = it.quantity + quantity) else it
            }
        }
        return player.copy(inventory = next)
    }

    fun changeTrust(player: PlayerState, agentId: String, delta: Int): PlayerState {
        val value = (player.relationships[agentId] ?: 50) + delta
        return player.copy(relationships = player.relationships + (agentId to value.coerceIn(0, 100)))
    }

    fun canPromote(player: PlayerState, rank: RankDefinition): Boolean =
        player.level >= rank.minLevel &&
        player.age >= rank.minAge &&
        player.reputation >= rank.minReputation &&
        rank.rank == player.rank + 1

    fun canCreatePartner(player: PlayerState): Boolean = player.age >= 18

    fun canEnterMission(player: PlayerState, mission: MissionDefinition): Boolean =
        player.age >= mission.minAge && player.rank >= mission.minRank
}

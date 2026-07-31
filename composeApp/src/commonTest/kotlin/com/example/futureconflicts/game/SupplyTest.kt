package com.example.futureconflicts.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupplyTest {

    /** A wide, building-light map with the armies far apart so no combat ends the game
     *  before the 7-turn supply cadence can fire. The player has an HQ landing zone. */
    private fun peacefulBattle(seed: Long): Battle =
        Battle(
            Scenario(
                flatMap(40, 1),
                units = listOf(
                    Unit(UnitType.INFANTRY, Team.PLAYER, Pos(1, 0)),
                    Unit(UnitType.INFANTRY, Team.ENEMY, Pos(39, 0)),
                ),
                buildings = listOf(Building(Pos(0, 0), Building.Kind.HQ, owner = Team.PLAYER)),
            ),
            seed = seed,
        )

    // ---- Seeded RNG ----

    @Test
    fun roll_is_deterministic_for_a_given_seed() {
        val a = Random(7L)
        val b = Random(7L)
        val seqA = List(50) { Supply.roll(a) }
        val seqB = List(50) { Supply.roll(b) }
        assertEquals(seqA, seqB, "same seed must produce the same draw sequence")
        assertTrue(seqA.toSet().size >= 2, "a healthy RNG should draw more than one kind")
    }

    @Test
    fun roll_follows_the_weight_ordering() {
        assertEquals(10, Supply.totalWeight)
        val rng = Random(42L)
        val counts = HashMap<SupplyKind, Int>()
        repeat(10_000) { counts.merge(Supply.roll(rng), 1, Int::plus) }

        // Every kind should show up, and frequency should track the weights 4>3>2>1.
        SupplyKind.entries.forEach { assertTrue((counts[it] ?: 0) > 0, "$it never drawn") }
        assertTrue(counts.getValue(SupplyKind.GOLD) > counts.getValue(SupplyKind.REINFORCE))
        assertTrue(counts.getValue(SupplyKind.REINFORCE) > counts.getValue(SupplyKind.HEAL))
        assertTrue(counts.getValue(SupplyKind.HEAL) > counts.getValue(SupplyKind.REVEAL))
    }

    // ---- Cadence ----

    @Test
    fun a_supply_drop_fires_on_the_seventh_player_turn() {
        val battle = peacefulBattle(seed = 1L)
        repeat(5) { battle.endPlayerTurn() } // now day 6
        assertEquals(6, battle.day)
        assertNull(battle.lastSupplyKind, "no drop before the 7th turn")

        battle.endPlayerTurn() // day 7 — player's 7th turn
        assertEquals(7, battle.day)
        assertNotNull(battle.lastSupplyKind, "a drop should fire on the 7th turn")
        assertEquals(Team.PLAYER, battle.lastSupplyTeam)
        assertNull(battle.winner, "the peaceful setup must not end early")
    }

    @Test
    fun the_same_seed_yields_the_same_drop() {
        val a = peacefulBattle(seed = 12345L)
        val b = peacefulBattle(seed = 12345L)
        repeat(6) { a.endPlayerTurn(); b.endPlayerTurn() }
        assertNotNull(a.lastSupplyKind)
        assertEquals(a.lastSupplyKind, b.lastSupplyKind, "seeded drops must be reproducible")
    }

    // ---- Individual boons (applied directly, RNG-independent) ----

    private fun boonBattle(vararg units: Unit, buildings: List<Building> = emptyList()): Battle =
        battleWith(flatMap(6, 1), units.toList(), buildings)

    @Test
    fun gold_windfall_adds_gold() {
        val battle = boonBattle(
            Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
            Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0)),
        )
        val before = battle.goldOf(Team.PLAYER)
        assertTrue(battle.apply(Action.SupplyDrop(Team.PLAYER, SupplyKind.GOLD)))

        assertEquals(before + Supply.GOLD_WINDFALL, battle.goldOf(Team.PLAYER))
        assertEquals(SupplyKind.GOLD, battle.lastSupplyKind)
        assertEquals(Team.PLAYER, battle.lastSupplyTeam)
    }

    @Test
    fun reinforce_spawns_a_free_unit_at_the_hq() {
        val battle = boonBattle(
            Unit(UnitType.INFANTRY, Team.PLAYER, Pos(2, 0)),
            Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0)),
            buildings = listOf(Building(Pos(0, 0), Building.Kind.HQ, owner = Team.PLAYER)),
        )
        val before = battle.goldOf(Team.PLAYER)
        assertTrue(battle.apply(Action.SupplyDrop(Team.PLAYER, SupplyKind.REINFORCE)))

        val spawned = battle.unitAt(Pos(0, 0))
        assertNotNull(spawned)
        assertEquals(Supply.REINFORCE_UNIT, spawned.type)
        assertEquals(Team.PLAYER, spawned.team)
        assertTrue(spawned.hasActed, "reinforcements can't act the turn they arrive")
        assertEquals(before, battle.goldOf(Team.PLAYER), "reinforce shouldn't also pay gold")
    }

    @Test
    fun reinforce_without_a_landing_zone_pays_gold_instead() {
        // Player has no HQ, so there is nowhere to drop reinforcements.
        val battle = boonBattle(
            Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
            Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0)),
        )
        val units = battle.units.size
        val before = battle.goldOf(Team.PLAYER)
        assertTrue(battle.apply(Action.SupplyDrop(Team.PLAYER, SupplyKind.REINFORCE)))

        assertEquals(units, battle.units.size, "no unit spawns without an HQ")
        assertEquals(before + Supply.GOLD_WINDFALL, battle.goldOf(Team.PLAYER))
    }

    @Test
    fun heal_restores_hp_and_clamps_at_max() {
        val hurt = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0), hp = 4)
        val full = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(1, 0), hp = Unit.MAX_HP)
        val enemy = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0), hp = 5)
        val battle = boonBattle(hurt, full, enemy)

        assertTrue(battle.apply(Action.SupplyDrop(Team.PLAYER, SupplyKind.HEAL)))

        assertEquals(4 + Supply.HEAL_AMOUNT, hurt.hp)
        assertEquals(Unit.MAX_HP, full.hp, "healing can't exceed max HP")
        assertEquals(5, enemy.hp, "the enemy isn't healed by our supply drop")
    }

    @Test
    fun reveal_uncovers_the_whole_map_and_hidden_enemies() {
        val enemy = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0)) // far away, on plains
        val battle = boonBattle(
            Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
            enemy,
        )
        assertTrue(battle.fogEnabled)
        assertFalse(battle.isUnitVisible(Team.PLAYER, enemy), "distant enemy is fogged pre-drop")

        assertTrue(battle.apply(Action.SupplyDrop(Team.PLAYER, SupplyKind.REVEAL)))

        assertEquals(6, battle.visibleTiles(Team.PLAYER).size, "whole 6x1 map is revealed")
        assertTrue(battle.isUnitVisible(Team.PLAYER, enemy), "revealed enemy is now visible")
    }
}

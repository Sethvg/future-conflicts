package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EconomyTest {

    @Test
    fun income_accrues_at_the_start_of_a_turn() {
        val map = flatMap(9, 1)
        val buildings = listOf(
            Building(Pos(0, 0), Building.Kind.HQ, owner = Team.PLAYER),
            Building(Pos(1, 0), Building.Kind.CITY, owner = Team.PLAYER, level = 1),
        )
        val battle = battleWith(
            map,
            units = listOf(
                Unit(UnitType.INFANTRY, Team.PLAYER, Pos(2, 0)),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(8, 0)),
            ),
            buildings = buildings,
        )
        val before = battle.goldOf(Team.PLAYER)
        battle.endPlayerTurn() // -> enemy turn -> player's new turn grants income

        // HQ (100) + city L1 (100) = 200.
        assertEquals(before + 200, battle.goldOf(Team.PLAYER))
    }

    @Test
    fun upgrading_a_city_costs_gold_and_raises_income() {
        val map = flatMap(3, 1)
        val city = Building(Pos(1, 0), Building.Kind.CITY, owner = Team.PLAYER, level = 1)
        val battle = battleWith(
            map,
            units = listOf(
                Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(2, 0)),
            ),
            buildings = listOf(city),
        )
        val before = battle.goldOf(Team.PLAYER)
        assertTrue(battle.apply(Action.Upgrade(Pos(1, 0))))

        assertEquals(before - Economy.CITY_UPGRADE_COST, battle.goldOf(Team.PLAYER))
        assertEquals(2, battle.buildingAt(Pos(1, 0))!!.level)
        assertEquals(200, battle.buildingAt(Pos(1, 0))!!.incomePerTurn)
    }

    @Test
    fun capturing_an_upgraded_city_razes_it_to_level_1() {
        val map = flatMap(6, 1)
        // captureLeft below the infantry's 10 HP so one capture flips it.
        val city = Building(Pos(1, 0), Building.Kind.CITY, owner = Team.ENEMY, level = 3, captureLeft = 5)
        val battle = battleWith(
            map,
            units = listOf(
                Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0)), // keeps the game going
            ),
            buildings = listOf(city),
        )
        assertTrue(battle.apply(Action.Capture(Pos(0, 0), Pos(1, 0))))

        val b = battle.buildingAt(Pos(1, 0))!!
        assertEquals(Team.PLAYER, b.owner)
        assertEquals(1, b.level, "a captured city is razed back to level 1")
        assertEquals(Economy.CAPTURE_POINTS, b.captureLeft)
        assertNull(battle.winner)
    }

    @Test
    fun partial_capture_requires_multiple_turns() {
        val map = flatMap(6, 1)
        val city = Building(Pos(1, 0), Building.Kind.CITY, owner = Team.ENEMY, level = 1)
        val inf = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)) // 10 HP, 20 to capture
        val battle = battleWith(
            map,
            units = listOf(inf, Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0))),
            buildings = listOf(city),
        )
        assertTrue(battle.apply(Action.Capture(Pos(0, 0), Pos(1, 0))))
        assertEquals(Team.ENEMY, battle.buildingAt(Pos(1, 0))!!.owner, "not captured in one turn")
        assertEquals(10, battle.buildingAt(Pos(1, 0))!!.captureLeft)
    }

    @Test
    fun building_from_hq_spends_gold_and_spawns_a_spent_unit() {
        val map = flatMap(5, 1)
        val battle = battleWith(
            map,
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(4, 0))),
            buildings = listOf(Building(Pos(0, 0), Building.Kind.HQ, owner = Team.PLAYER)),
        )
        val before = battle.goldOf(Team.PLAYER)
        assertTrue(battle.apply(Action.Build(UnitType.INFANTRY, Pos(0, 0))))

        assertEquals(before - UnitType.INFANTRY.cost, battle.goldOf(Team.PLAYER))
        val spawned = battle.unitAt(Pos(0, 0))
        assertTrue(spawned != null && spawned.team == Team.PLAYER)
        assertTrue(spawned!!.hasActed, "a freshly built unit can't act this turn")
    }

    @Test
    fun cannot_build_a_unit_you_cannot_afford() {
        val map = flatMap(5, 1)
        val battle = battleWith(
            map,
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(4, 0))),
            buildings = listOf(Building(Pos(0, 0), Building.Kind.FACTORY, owner = Team.PLAYER)),
        )
        val before = battle.goldOf(Team.PLAYER)
        // A Factory *can* build a Tank, but it costs 7000 and we start with 5000.
        assertFalse(battle.apply(Action.Build(UnitType.TANK, Pos(0, 0))))
        assertEquals(before, battle.goldOf(Team.PLAYER))
        assertNull(battle.unitAt(Pos(0, 0)))
    }

    @Test
    fun capturing_the_enemy_hq_wins_the_game() {
        val map = flatMap(6, 1)
        val hq = Building(Pos(1, 0), Building.Kind.HQ, owner = Team.ENEMY, captureLeft = 5)
        val battle = battleWith(
            map,
            units = listOf(
                Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0)), // enemy still has a unit
            ),
            buildings = listOf(hq),
        )
        assertTrue(battle.apply(Action.Capture(Pos(0, 0), Pos(1, 0))))
        assertEquals(Team.PLAYER, battle.winner, "seizing the HQ wins even with enemy units alive")
    }

    @Test
    fun commander_rebuy_cost_compounds_per_loss() {
        val ps = PlayerState()
        assertEquals(10000, ps.commanderCost(base = 10000, multiplier = 1.5))
        ps.commanderLosses = 1
        assertEquals(15000, ps.commanderCost(base = 10000, multiplier = 1.5))
        ps.commanderLosses = 2
        assertEquals(22500, ps.commanderCost(base = 10000, multiplier = 1.5))
    }
}

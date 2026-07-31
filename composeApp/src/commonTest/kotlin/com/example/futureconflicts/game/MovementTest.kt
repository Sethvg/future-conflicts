package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovementTest {

    @Test
    fun reachable_includes_origin_at_zero_cost() {
        val map = flatMap(5, 5)
        val u = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(2, 2))
        val reach = Movement.reachable(map, u, occupancy(listOf(u)))
        assertEquals(0, reach[Pos(2, 2)])
    }

    @Test
    fun respects_move_budget_on_open_ground() {
        val map = flatMap(7, 7)
        val u = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(3, 3)) // maxMove 3, plains cost 1
        val reach = Movement.reachable(map, u, occupancy(listOf(u)))
        assertTrue(Pos(0, 3) in reach, "distance 3 should be reachable")
        assertFalse(Pos(0, 0) in reach, "distance 6 must be out of range")
    }

    @Test
    fun terrain_cost_limits_range() {
        // A wall of forest (cost 2) in front of the unit halves how far it gets.
        val forest = buildMap {
            for (y in 0 until 7) put(Pos(3, y), Terrain.FOREST)
        }
        val map = flatMap(7, 7, overrides = forest)
        val u = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(2, 3)) // move 3
        val reach = Movement.reachable(map, u, occupancy(listOf(u)))
        // Enter forest at (3,3): cost 2. Then (4,3) would be 2+1=3 (ok).
        assertTrue(Pos(3, 3) in reach)
        assertTrue(Pos(4, 3) in reach)
        // (5,3) would cost 2+1+1 = 4 > 3.
        assertFalse(Pos(5, 3) in reach)
    }

    @Test
    fun impassable_terrain_is_excluded() {
        val map = flatMap(5, 5, overrides = mapOf(Pos(3, 2) to Terrain.SEA))
        val u = Unit(UnitType.RECON, Team.PLAYER, Pos(2, 2)) // fast, would otherwise reach it
        val reach = Movement.reachable(map, u, occupancy(listOf(u)))
        assertFalse(Pos(3, 2) in reach, "ground units can't stand on sea")
    }

    @Test
    fun enemy_blocks_passage() {
        val map = flatMap(7, 1) // single row corridor
        val mover = Unit(UnitType.RECON, Team.PLAYER, Pos(0, 0))
        val enemy = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(2, 0))
        val reach = Movement.reachable(map, mover, occupancy(listOf(mover, enemy)))
        assertTrue(Pos(1, 0) in reach, "can approach the enemy")
        assertFalse(Pos(2, 0) in reach, "cannot enter the enemy's tile")
        assertFalse(Pos(3, 0) in reach, "cannot pass through the enemy")
    }

    @Test
    fun cannot_stop_on_ally_but_can_pass_through() {
        val map = flatMap(7, 1)
        val mover = Unit(UnitType.RECON, Team.PLAYER, Pos(0, 0))
        val ally = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(2, 0))
        val reach = Movement.reachable(map, mover, occupancy(listOf(mover, ally)))
        assertFalse(Pos(2, 0) in reach, "cannot end on an ally's tile")
        assertTrue(Pos(3, 0) in reach, "but can pass through it to a free tile beyond")
    }
}

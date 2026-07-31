package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionTest {

    @Test
    fun a_unit_sees_within_its_sight_radius() {
        val inf = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(5, 5)) // vision 2
        val battle = battleOf(flatMap(12, 12), inf)
        val visible = battle.visibleTiles(Team.PLAYER)
        assertTrue(Pos(5, 5) in visible)
        assertTrue(Pos(5, 7) in visible, "2 tiles away is seen")
        assertFalse(Pos(5, 8) in visible, "3 tiles away is not")
    }

    @Test
    fun recon_sees_farther_than_infantry() {
        val recon = Unit(UnitType.RECON, Team.PLAYER, Pos(5, 5)) // vision 5
        val battle = battleOf(flatMap(12, 12), recon)
        assertTrue(Pos(5, 10) in battle.visibleTiles(Team.PLAYER))
    }

    @Test
    fun standing_on_a_mountain_extends_sight() {
        val flat = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(5, 5))
        val onMountain = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(5, 5))
        val flatBattle = battleOf(flatMap(12, 12), flat)
        val mtnBattle = battleOf(flatMap(12, 12, overrides = mapOf(Pos(5, 5) to Terrain.MOUNTAIN)), onMountain)
        assertFalse(Pos(5, 8) in flatBattle.visibleTiles(Team.PLAYER))
        assertTrue(Pos(5, 8) in mtnBattle.visibleTiles(Team.PLAYER), "mountain adds +1 sight")
    }

    @Test
    fun owned_buildings_grant_vision() {
        val battle = battleWith(
            flatMap(12, 12),
            units = emptyList(),
            buildings = listOf(Building(Pos(3, 3), Building.Kind.HQ, Team.PLAYER)),
        )
        val visible = battle.visibleTiles(Team.PLAYER)
        assertTrue(Pos(3, 3) in visible)
        assertTrue(Pos(3, 5) in visible, "building sight radius 2")
        assertFalse(Pos(3, 6) in visible)
    }

    @Test
    fun enemy_in_forest_is_hidden_until_adjacent() {
        val scout = Unit(UnitType.RECON, Team.PLAYER, Pos(0, 0)) // vision 5, sees the forest tile
        val hidden = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(3, 0))
        val battle = battleWith(
            flatMap(8, 1, overrides = mapOf(Pos(3, 0) to Terrain.FOREST)),
            units = listOf(scout, hidden),
            buildings = emptyList(),
        )
        val visible = battle.visibleTiles(Team.PLAYER)
        assertTrue(Pos(3, 0) in visible, "the forest tile itself is within sight")
        assertFalse(battle.isUnitVisible(Team.PLAYER, hidden, visible), "but the unit hiding in it is not seen")
    }

    @Test
    fun adjacent_unit_reveals_a_forest_hider() {
        val spotter = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(2, 0)) // adjacent to the forest tile
        val hidden = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(3, 0))
        val battle = battleWith(
            flatMap(8, 1, overrides = mapOf(Pos(3, 0) to Terrain.FOREST)),
            units = listOf(spotter, hidden),
            buildings = emptyList(),
        )
        assertTrue(battle.isUnitVisible(Team.PLAYER, hidden))
    }

    @Test
    fun fog_disabled_reveals_everything() {
        val battle = battleOf(
            flatMap(12, 12),
            Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
            Unit(UnitType.TANK, Team.ENEMY, Pos(11, 11)),
        )
        battle.fogEnabled = false
        val visible = battle.visibleTiles(Team.PLAYER)
        assertTrue(Pos(11, 11) in visible)
        assertTrue(battle.isUnitVisible(Team.PLAYER, battle.unitAt(Pos(11, 11))!!, visible))
    }
}

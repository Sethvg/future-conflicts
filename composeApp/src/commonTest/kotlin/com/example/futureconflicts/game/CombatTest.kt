package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatTest {

    private fun unit(type: UnitType, hp: Int = Unit.MAX_HP) =
        Unit(type, Team.PLAYER, Pos(0, 0), hp)

    @Test
    fun base_matchup_is_looked_up_both_ways() {
        assertEquals(75, Combat.basePercent(UnitType.TANK, UnitType.INFANTRY))
        assertEquals(5, Combat.basePercent(UnitType.INFANTRY, UnitType.TANK))
    }

    @Test
    fun tank_vs_infantry_on_plains_is_deterministic() {
        // 75/10 * (10/10) * (1 - 0.1*1*(10/10)) = 7.5 * 0.9 = 6.75 -> 7
        val dmg = Combat.damage(unit(UnitType.TANK), unit(UnitType.INFANTRY), Terrain.PLAINS)
        assertEquals(7, dmg)
    }

    @Test
    fun damage_scales_with_attacker_hp() {
        val full = Combat.damage(unit(UnitType.TANK, 10), unit(UnitType.INFANTRY), Terrain.PLAINS)
        val hurt = Combat.damage(unit(UnitType.TANK, 5), unit(UnitType.INFANTRY), Terrain.PLAINS)
        assertTrue(hurt < full, "a wounded attacker deals less: $hurt !< $full")
    }

    @Test
    fun terrain_defense_reduces_damage() {
        val onPlains = Combat.damage(unit(UnitType.TANK), unit(UnitType.INFANTRY), Terrain.PLAINS)
        val onMountain = Combat.damage(unit(UnitType.TANK), unit(UnitType.INFANTRY), Terrain.MOUNTAIN)
        assertTrue(onMountain < onPlains, "mountain cover should reduce damage: $onMountain !< $onPlains")
    }

    @Test
    fun damage_never_exceeds_defender_hp() {
        val victim = unit(UnitType.RECON, hp = 2)
        val dmg = Combat.damage(unit(UnitType.TANK), victim, Terrain.PLAINS)
        assertEquals(2, dmg)
    }

    @Test
    fun range_rules_direct_and_indirect() {
        assertTrue(Combat.inRange(UnitType.INFANTRY, Pos(0, 0), Pos(1, 0)))
        assertFalse(Combat.inRange(UnitType.INFANTRY, Pos(0, 0), Pos(2, 0)))
        // Artillery: 2..3
        assertFalse(Combat.inRange(UnitType.ARTILLERY, Pos(0, 0), Pos(1, 0)))
        assertTrue(Combat.inRange(UnitType.ARTILLERY, Pos(0, 0), Pos(2, 0)))
        assertTrue(Combat.inRange(UnitType.ARTILLERY, Pos(0, 0), Pos(3, 0)))
        assertFalse(Combat.inRange(UnitType.ARTILLERY, Pos(0, 0), Pos(4, 0)))
    }
}

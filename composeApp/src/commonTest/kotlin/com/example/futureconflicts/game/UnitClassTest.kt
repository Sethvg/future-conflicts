package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The unit behaviour hierarchy: each unit delegates movement/capture/fuel to its class. */
class UnitClassTest {

    @Test
    fun every_unit_has_a_class_matching_its_move_class() {
        for (t in UnitType.entries) {
            assertEquals(t.unitClass.moveClass, t.moveClass, "$t must delegate its move class")
        }
    }

    @Test
    fun only_foot_units_capture() {
        val capturing = UnitType.entries.filter { it.canCapture }.toSet()
        assertEquals(setOf(UnitType.INFANTRY, UnitType.MECH), capturing)
        assertTrue(UnitType.INFANTRY.unitClass is FootClass)
        assertTrue(UnitType.TANK.unitClass is GroundClass)
    }

    @Test
    fun air_class_flies_anywhere_ground_does_not() {
        val air = UnitType.GUNSHIP.unitClass
        assertTrue(air is AirClass)
        assertTrue(air.canEnter(Terrain.SEA) && air.canEnter(Terrain.MOUNTAIN))
        assertTrue(air.ignoresTerrain)

        val ground = UnitType.TANK.unitClass
        assertFalse(ground.canEnter(Terrain.SEA), "ground can't enter water")
        assertTrue(ground.canEnter(Terrain.PLAINS))
        assertFalse(ground.ignoresTerrain)
    }

    @Test
    fun naval_class_is_confined_to_water_and_serviced_by_a_port() {
        val ship = NavalClass()
        assertTrue(ship.canEnter(Terrain.SEA))
        assertFalse(ship.canEnter(Terrain.PLAINS), "ships stay at sea")
        assertTrue(ship.refuelsAt(Building.Kind.PORT))
        assertFalse(ship.refuelsAt(Building.Kind.AIRPORT))
    }

    @Test
    fun fuel_and_refuel_bases_come_from_the_class() {
        assertTrue(UnitType.GUNSHIP.fuelLimited)
        assertEquals(20, UnitType.GUNSHIP.maxFuel)
        assertFalse(UnitType.TANK.fuelLimited)

        val gunship = Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(0, 0))
        assertTrue(gunship.refuelsAt(Building.Kind.AIRPORT))
        assertTrue(gunship.refuelsAt(Building.Kind.HQ))
        assertFalse(gunship.refuelsAt(Building.Kind.FACTORY))
        assertFalse(Unit(UnitType.TANK, Team.PLAYER, Pos(0, 0)).refuelsAt(Building.Kind.AIRPORT))
    }

    @Test
    fun anti_air_is_the_only_ground_unit_that_hits_air() {
        val groundHittingAir = UnitType.entries.filter { !it.air && it.hitsAir }.toSet()
        assertEquals(setOf(UnitType.ANTI_AIR), groundHittingAir)
    }
}

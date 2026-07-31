package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Slice 2: the air movement class, air immunity (only anti-air hits aircraft), and fuel. */
class AirTest {

    // ---- Movement ----

    @Test
    fun air_crosses_water_that_blocks_ground() {
        val map = flatMap(6, 1, overrides = mapOf(Pos(2, 0) to Terrain.SEA))
        val gunship = Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(0, 0))
        val infantry = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0))
        assertTrue(Pos(5, 0) in Movement.reachable(map, gunship, { null }), "air flies over the sea")
        assertFalse(Pos(3, 0) in Movement.reachable(map, infantry, { null }), "ground can't cross the sea")
    }

    @Test
    fun air_flies_over_enemy_units() {
        val map = flatMap(5, 1)
        val gunship = Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(0, 0))
        val enemy = Unit(UnitType.TANK, Team.ENEMY, Pos(1, 0))
        val reach = Movement.reachable(map, gunship, occupancy(listOf(gunship, enemy)))
        assertTrue(Pos(2, 0) in reach, "flies over the blocker")
        assertFalse(Pos(1, 0) in reach, "can't stop on an occupied tile")
    }

    // ---- Air immunity / targeting ----

    @Test
    fun only_anti_air_and_air_can_hit_aircraft() {
        assertFalse(Combat.canTarget(UnitType.TANK, UnitType.GUNSHIP))
        assertFalse(Combat.canTarget(UnitType.INFANTRY, UnitType.GUNSHIP))
        assertFalse(Combat.canTarget(UnitType.ARTILLERY, UnitType.GUNSHIP))
        assertTrue(Combat.canTarget(UnitType.ANTI_AIR, UnitType.GUNSHIP))
        assertTrue(Combat.canTarget(UnitType.GUNSHIP, UnitType.GUNSHIP))
        assertTrue(Combat.canTarget(UnitType.GUNSHIP, UnitType.TANK), "air hits ground")
    }

    @Test
    fun ground_cannot_attack_an_aircraft() {
        val gunship = Unit(UnitType.GUNSHIP, Team.ENEMY, Pos(1, 0))
        val b = battleWith(
            flatMap(6, 1),
            listOf(Unit(UnitType.TANK, Team.PLAYER, Pos(0, 0)), gunship, Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0))),
            emptyList(),
        )
        assertFalse(b.apply(Action.Attack(Pos(0, 0), Pos(0, 0), Pos(1, 0))), "tank can't shoot the gunship")
        assertEquals(10, gunship.hp)
    }

    @Test
    fun anti_air_wrecks_a_gunship() {
        val gunship = Unit(UnitType.GUNSHIP, Team.ENEMY, Pos(1, 0))
        val b = battleWith(
            flatMap(6, 1),
            listOf(Unit(UnitType.ANTI_AIR, Team.PLAYER, Pos(0, 0)), gunship, Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0))),
            emptyList(),
        )
        assertTrue(b.apply(Action.Attack(Pos(0, 0), Pos(0, 0), Pos(1, 0))))
        assertTrue(gunship.hp < 10, "anti-air shreds aircraft")
    }

    @Test
    fun gunship_hitting_ground_takes_no_counterattack() {
        val gunship = Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(0, 0))
        val tank = Unit(UnitType.TANK, Team.ENEMY, Pos(1, 0))
        val b = battleWith(
            flatMap(6, 1),
            listOf(gunship, tank, Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0))),
            emptyList(),
        )
        assertTrue(b.apply(Action.Attack(Pos(0, 0), Pos(0, 0), Pos(1, 0))))
        assertEquals(10, gunship.hp, "ground can't counter an aircraft")
        assertTrue(tank.hp < 10)
    }

    // ---- Fuel ----

    @Test
    fun aircraft_burns_fuel_and_crashes_when_dry() {
        val b = battleWith(
            flatMap(40, 1),
            listOf(
                Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(1, 0), fuel = 2),
                Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(39, 0)),
            ),
            emptyList(),
        )
        b.endPlayerTurn() // player's next turn burns 2 fuel -> 0 -> crash
        assertTrue(b.units.none { it.type == UnitType.GUNSHIP }, "a dry aircraft crashes")
        assertNull(b.winner)
    }

    @Test
    fun aircraft_refuels_on_an_owned_airport() {
        val gunship = Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(0, 0), fuel = 2)
        val b = battleWith(
            flatMap(40, 1),
            listOf(gunship, Unit(UnitType.INFANTRY, Team.PLAYER, Pos(1, 0)), Unit(UnitType.INFANTRY, Team.ENEMY, Pos(39, 0))),
            listOf(Building(Pos(0, 0), Building.Kind.AIRPORT, owner = Team.PLAYER)),
        )
        b.endPlayerTurn()
        assertEquals(UnitType.GUNSHIP.maxFuel, gunship.fuel, "refuelled at the airport")
    }

    // ---- Production ----

    @Test
    fun airport_builds_aircraft_and_factory_builds_anti_air() {
        val air = battleWith(
            flatMap(10, 1), listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            listOf(Building(Pos(0, 0), Building.Kind.AIRPORT, owner = Team.PLAYER)),
        )
        air.onTap(Pos(0, 0))
        assertTrue(air.buildableHere().any { it.type == UnitType.GUNSHIP }, "airport builds aircraft")

        val fac = battleWith(
            flatMap(10, 1), listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            listOf(Building(Pos(0, 0), Building.Kind.FACTORY, owner = Team.PLAYER)),
        )
        fac.onTap(Pos(0, 0))
        val facTypes = fac.buildableHere().map { it.type }
        assertTrue(UnitType.ANTI_AIR in facTypes, "factory builds anti-air")
        assertFalse(UnitType.GUNSHIP in facTypes, "gunship isn't a factory unit")
    }

    @Test
    fun a_fuel_crash_can_end_the_game() {
        // Player's only unit is an aircraft that runs dry -> elimination win for the enemy.
        val b = battleWith(
            flatMap(40, 1),
            listOf(
                Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(1, 0), fuel = 2),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(39, 0)),
            ),
            emptyList(),
        )
        b.endPlayerTurn()
        assertEquals(Team.ENEMY, b.winner, "losing your last unit to fuel loses the game")
    }

    @Test
    fun anti_air_counters_a_gunship_that_attacks_it() {
        val gunship = Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(0, 0))
        val b = battleWith(
            flatMap(6, 1),
            listOf(gunship, Unit(UnitType.ANTI_AIR, Team.ENEMY, Pos(1, 0)), Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 0))),
            emptyList(),
        )
        assertTrue(b.apply(Action.Attack(Pos(0, 0), Pos(0, 0), Pos(1, 0))))
        assertTrue(gunship.hp < 10, "anti-air counters an attacking aircraft")
    }

    @Test
    fun aircraft_does_not_refuel_on_a_neutral_airport() {
        val gunship = Unit(UnitType.GUNSHIP, Team.PLAYER, Pos(0, 0), fuel = 4)
        val b = battleWith(
            flatMap(40, 1),
            listOf(gunship, Unit(UnitType.INFANTRY, Team.PLAYER, Pos(1, 0)), Unit(UnitType.INFANTRY, Team.ENEMY, Pos(39, 0))),
            listOf(Building(Pos(0, 0), Building.Kind.AIRPORT, owner = null)), // neutral airport
        )
        b.endPlayerTurn()
        assertEquals(2, gunship.fuel, "a neutral airport doesn't refuel you")
    }
}

package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Slice 3: the Drone Command's persistent, free, autonomous scout flight. */
class DroneTest {

    private fun droneBattle(
        level: Int = 1,
        owner: Team? = Team.PLAYER,
        at: Pos = Pos(0, 0),
        extraUnits: List<Unit> = emptyList(),
    ): Battle {
        // A wide fogged map: the player has a Drone Command, the enemy is far away.
        val map = flatMap(20, 3)
        return battleWith(
            map,
            units = listOf(
                Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 2)),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(19, 2)),
            ) + extraUnits,
            buildings = listOf(Building(at, Building.Kind.DRONE_COMMAND, owner = owner, level = level)),
        )
    }

    private fun dronesOf(b: Battle, team: Team = Team.PLAYER) =
        b.units.filter { it.alive && it.team == team && it.type == UnitType.DRONE }

    @Test
    fun a_drone_command_launches_one_drone_per_level() {
        val b = droneBattle(level = 2)
        assertEquals(0, dronesOf(b).size, "no drones before the first turn begins")
        b.endPlayerTurn() // -> enemy turn -> player's turn runs the drone phase
        assertEquals(2, dronesOf(b).size, "a level-2 command fields 2 drones")
    }

    @Test
    fun drones_are_free() {
        val b = droneBattle(level = 1)
        val before = b.goldOf(Team.PLAYER)
        b.endPlayerTurn()
        // Income is added at turn start; the drones themselves must not deduct anything.
        assertTrue(b.goldOf(Team.PLAYER) >= before, "drones never cost gold")
        assertEquals(1, dronesOf(b).size)
    }

    @Test
    fun the_flight_does_not_grow_beyond_its_level() {
        val b = droneBattle(level = 1)
        repeat(3) { b.endPlayerTurn() }
        assertEquals(1, dronesOf(b).size, "a level-1 command tops up to exactly 1 drone")
    }

    @Test
    fun a_neutral_drone_command_fields_nothing() {
        val b = droneBattle(level = 2, owner = null)
        b.endPlayerTurn()
        assertEquals(0, dronesOf(b).size, "you must own the building")
    }

    @Test
    fun drones_fly_themselves_and_reveal_fog() {
        val b = droneBattle(level = 1)
        val seenBefore = b.visibleTiles(Team.PLAYER).size
        b.endPlayerTurn()
        val drone = dronesOf(b).single()

        assertTrue(drone.hasActed, "drones are flown by the AI, not the player")
        assertTrue(drone.pos != Pos(0, 0), "the drone left base to scout")
        assertTrue(
            b.visibleTiles(Team.PLAYER).size > seenBefore,
            "scouting strips fog: $seenBefore -> ${b.visibleTiles(Team.PLAYER).size}",
        )
    }

    @Test
    fun a_low_fuel_drone_heads_home() {
        val b = droneBattle(level = 1)
        b.endPlayerTurn()
        val drone = dronesOf(b).single()
        // Enough to survive the turn-start burn, but low enough to trigger the return.
        drone.fuel = 6
        val distBefore = drone.pos.manhattan(Pos(0, 0))
        assertTrue(distBefore > 0, "the drone should have flown out first")

        b.endPlayerTurn()
        val after = dronesOf(b).single()
        assertTrue(
            after.pos.manhattan(Pos(0, 0)) < distBefore,
            "a low-fuel drone closes on base ($distBefore -> ${after.pos.manhattan(Pos(0, 0))})",
        )
    }

    @Test
    fun a_drone_that_runs_dry_crashes_and_books_a_cooldown() {
        val b = droneBattle(level = 1)
        b.endPlayerTurn()
        val drone = dronesOf(b).single()
        drone.pos = Pos(19, 0)                  // far from base, so it can't land
        drone.fuel = Battle.FUEL_BURN_PER_TURN  // this turn's burn empties it

        b.endPlayerTurn()
        assertEquals(0, dronesOf(b).size, "a dry drone crashes")
        // The slot is on cooldown, so no instant replacement.
        b.endPlayerTurn()
        assertEquals(0, dronesOf(b).size, "the crashed slot waits out its cooldown")
    }

    @Test
    fun drones_do_not_keep_a_defeated_army_alive() {
        // The player's last real unit dies; only a drone + its command remain.
        val infantry = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 2))
        val b = battleWith(
            flatMap(20, 3),
            units = listOf(infantry, Unit(UnitType.INFANTRY, Team.ENEMY, Pos(19, 2))),
            buildings = listOf(Building(Pos(0, 0), Building.Kind.DRONE_COMMAND, owner = Team.PLAYER, level = 1)),
        )
        b.endPlayerTurn()
        assertEquals(1, dronesOf(b).size, "a drone is airborne")

        // Mirror what a killing blow does: remove the dead, then evaluate victory.
        infantry.hp = 0
        b.removeDead()
        b.checkVictory()
        assertEquals(Team.ENEMY, b.winner, "a drone-only side is still eliminated")
    }

    @Test
    fun the_enemy_planner_never_re_drives_an_autonomous_drone() {
        val b = battleWith(
            flatMap(20, 3),
            units = listOf(
                Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 2)),
                Unit(UnitType.INFANTRY, Team.ENEMY, Pos(19, 2)),
            ),
            buildings = listOf(Building(Pos(19, 0), Building.Kind.DRONE_COMMAND, owner = Team.ENEMY, level = 1)),
        )
        b.endPlayerTurn() // enemy turn: drone phase launches + flies, then the planner runs
        val enemyDrones = dronesOf(b, Team.ENEMY)
        assertEquals(1, enemyDrones.size)
        assertTrue(enemyDrones.single().hasActed, "the drone's action stays spent after the AI turn")
    }

    @Test
    fun drone_flights_are_deterministic() {
        fun trace(): List<Pos> {
            val b = droneBattle(level = 2)
            repeat(3) { b.endPlayerTurn() }
            return dronesOf(b).map { it.pos }.sortedWith(compareBy({ it.y }, { it.x }))
        }
        assertEquals(trace(), trace(), "the same inputs must produce the same flight")
    }

    @Test
    fun a_downed_drone_leaves_its_slot_empty_for_the_cooldown() {
        val b = droneBattle(level = 1)
        b.endPlayerTurn()
        val drone = dronesOf(b).single()

        // Shoot it down (removeDead is the sim's own cleanup, as after a real attack).
        drone.hp = 0
        b.removeDead()
        assertEquals(0, dronesOf(b).size)

        b.endPlayerTurn()
        assertEquals(0, dronesOf(b).size, "the slot is still on cooldown, not instantly refilled")
    }

    @Test
    fun the_slot_refills_after_the_cooldown_expires() {
        val b = droneBattle(level = 1)
        b.endPlayerTurn()
        dronesOf(b).single().hp = 0
        b.removeDead()
        assertEquals(0, dronesOf(b).size)

        repeat(Drones.REBUILD_COOLDOWN + 1) { b.endPlayerTurn() }
        assertEquals(1, dronesOf(b).size, "a replacement launches once the cooldown expires")
    }

    @Test
    fun drones_are_unarmed_but_anti_air_shreds_them() {
        assertFalse(Combat.canTarget(UnitType.DRONE, UnitType.INFANTRY), "the scout drone has no weapon")
        assertTrue(Combat.canTarget(UnitType.ANTI_AIR, UnitType.DRONE))
        assertTrue(Combat.canTarget(UnitType.GUNSHIP, UnitType.DRONE))
        assertFalse(Combat.canTarget(UnitType.TANK, UnitType.DRONE), "ground units still can't hit air")
    }

    @Test
    fun a_drone_command_upgrade_buys_another_drone() {
        val b = droneBattle(level = 1)
        val before = b.goldOf(Team.PLAYER)
        assertTrue(b.apply(Action.Upgrade(Pos(0, 0))))
        assertEquals(before - Drones.UPGRADE_COST, b.goldOf(Team.PLAYER))
        assertEquals(2, b.buildingAt(Pos(0, 0))!!.level)

        b.endPlayerTurn()
        assertEquals(2, dronesOf(b).size, "the bigger flight launches next turn")
    }

    @Test
    fun a_drone_command_produces_no_income_and_builds_no_units() {
        val dc = Building(Pos(0, 0), Building.Kind.DRONE_COMMAND, owner = Team.PLAYER, level = 3)
        assertEquals(0, dc.incomePerTurn, "it's not an income building")
        assertEquals(null, Building.Kind.DRONE_COMMAND.builds, "units aren't purchased here")
    }

    @Test
    fun drones_are_not_offered_in_any_build_menu() {
        assertFalse(UnitType.DRONE.basic, "drones are launched, never purchased")
        val b = battleWith(
            flatMap(10, 1),
            listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            listOf(Building(Pos(0, 0), Building.Kind.AIRPORT, owner = Team.PLAYER)),
        )
        b.onTap(Pos(0, 0))
        assertNotNull(b.buildableHere())
        assertFalse(b.buildableHere().any { it.type == UnitType.DRONE })
    }
}

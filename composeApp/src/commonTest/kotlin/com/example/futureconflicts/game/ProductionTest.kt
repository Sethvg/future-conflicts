package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Slice 1: production is distributed across category-specific buildings. */
class ProductionTest {

    private fun bldg(pos: Pos, kind: Building.Kind, owner: Team?, captureLeft: Int = Economy.CAPTURE_POINTS) =
        Building(pos, kind, owner = owner, captureLeft = captureLeft)

    /** A wide map with an enemy unit far off so the game doesn't end mid-test. */
    private fun battle(units: List<Unit>, buildings: List<Building>) =
        battleWith(flatMap(10, 1), units, buildings)

    @Test
    fun hq_builds_infantry_without_a_barracks() {
        val b = battle(
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            buildings = listOf(bldg(Pos(0, 0), Building.Kind.HQ, Team.PLAYER)),
        )
        assertTrue(b.apply(Action.Build(UnitType.INFANTRY, Pos(0, 0))), "HQ doubles as a Barracks")
        assertEquals(UnitType.INFANTRY, b.unitAt(Pos(0, 0))?.type)
    }

    @Test
    fun barracks_builds_foot_units() {
        val b = battle(
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            buildings = listOf(bldg(Pos(0, 0), Building.Kind.BARRACKS, Team.PLAYER)),
        )
        assertTrue(b.apply(Action.Build(UnitType.MECH, Pos(0, 0))), "barracks builds the infantry category")
        assertEquals(UnitType.MECH, b.unitAt(Pos(0, 0))?.type)
    }

    @Test
    fun factory_builds_vehicles() {
        val b = battle(
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            buildings = listOf(bldg(Pos(0, 0), Building.Kind.FACTORY, Team.PLAYER)),
        )
        // Recon (4000) is affordable from the 5000 starting gold.
        assertTrue(b.apply(Action.Build(UnitType.RECON, Pos(0, 0))))
        assertEquals(UnitType.RECON, b.unitAt(Pos(0, 0))?.type)
    }

    @Test
    fun a_barracks_cannot_build_vehicles() {
        val b = battle(
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            buildings = listOf(bldg(Pos(0, 0), Building.Kind.BARRACKS, Team.PLAYER)),
        )
        val gold = b.goldOf(Team.PLAYER)
        assertFalse(b.apply(Action.Build(UnitType.RECON, Pos(0, 0))), "wrong category rejected")
        assertEquals(gold, b.goldOf(Team.PLAYER), "a rejected build costs nothing")
        assertTrue(b.units.none { it.type == UnitType.RECON })
    }

    @Test
    fun the_commander_is_hq_only() {
        val b = battle(
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            buildings = listOf(bldg(Pos(0, 0), Building.Kind.FACTORY, Team.PLAYER)),
        )
        // Rejected on the building rule before cost even matters.
        assertFalse(b.apply(Action.Build(UnitType.COMMANDER, Pos(0, 0))), "no Commander from a Factory")
        assertTrue(b.units.none { it.type == UnitType.COMMANDER })
    }

    @Test
    fun capturing_a_factory_grants_build_access_but_never_converts_units() {
        val infantry = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0))
        val enemyTank = Unit(UnitType.TANK, Team.ENEMY, Pos(9, 0))
        val b = battle(
            units = listOf(infantry, enemyTank),
            // captureLeft below the infantry's 10 HP so one capture flips it.
            buildings = listOf(bldg(Pos(1, 0), Building.Kind.FACTORY, Team.ENEMY, captureLeft = 5)),
        )
        assertTrue(b.apply(Action.Capture(Pos(0, 0), Pos(1, 0))))
        assertEquals(Team.PLAYER, b.buildingAt(Pos(1, 0))?.owner, "factory captured")
        assertEquals(Team.ENEMY, enemyTank.team, "capture never converts existing units")

        // Now the capturer can build vehicles there.
        assertTrue(b.apply(Action.Build(UnitType.RECON, Pos(1, 0))))
        assertTrue(b.units.any { it.team == Team.PLAYER && it.type == UnitType.RECON })
    }

    @Test
    fun buildable_menu_lists_the_building_s_category() {
        val factory = battle(
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            buildings = listOf(bldg(Pos(0, 0), Building.Kind.FACTORY, Team.PLAYER)),
        )
        factory.onTap(Pos(0, 0)) // opens the build menu
        val vehicleTypes = factory.buildableHere().map { it.type }.toSet()
        assertTrue(UnitType.RECON in vehicleTypes && UnitType.TANK in vehicleTypes && UnitType.ARTILLERY in vehicleTypes)
        assertFalse(UnitType.INFANTRY in vehicleTypes)
        assertFalse(UnitType.COMMANDER in vehicleTypes, "no Commander outside the HQ menu")

        val barracks = battle(
            units = listOf(Unit(UnitType.INFANTRY, Team.ENEMY, Pos(9, 0))),
            buildings = listOf(bldg(Pos(0, 0), Building.Kind.BARRACKS, Team.PLAYER)),
        )
        barracks.onTap(Pos(0, 0))
        val footTypes = barracks.buildableHere().map { it.type }.toSet()
        assertEquals(setOf(UnitType.INFANTRY, UnitType.MECH), footTypes)
    }

    @Test
    fun twin_ridges_gives_each_side_a_barracks_and_a_factory() {
        val b = Battle() // default = Twin Ridges
        val kinds = b.buildingsView.filter { it.owner == Team.PLAYER }.map { it.kind }
        assertTrue(Building.Kind.BARRACKS in kinds, "player starts with a Barracks")
        assertTrue(Building.Kind.FACTORY in kinds, "player starts with a Factory")
        assertTrue(Building.Kind.HQ in kinds)
    }
}

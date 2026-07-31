package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleTest {

    @Test
    fun selecting_a_ready_friendly_unit_shows_its_range() {
        val map = flatMap(5, 5)
        val tank = Unit(UnitType.TANK, Team.PLAYER, Pos(2, 2))
        val battle = battleOf(map, tank)

        battle.onTap(Pos(2, 2))
        assertEquals(Battle.Phase.MOVING, battle.phase)
        assertEquals(tank, battle.selected)
        assertTrue(battle.reachable.isNotEmpty())
    }

    @Test
    fun tapping_an_enemy_unit_does_not_select_it() {
        val map = flatMap(5, 5)
        val enemy = Unit(UnitType.TANK, Team.ENEMY, Pos(1, 1))
        val battle = battleOf(map, enemy)

        battle.onTap(Pos(1, 1))
        assertNull(battle.selected)
        assertEquals(Battle.Phase.IDLE, battle.phase)
    }

    @Test
    fun move_then_attack_damages_enemy_and_draws_counter() {
        val map = flatMap(5, 5)
        val tank = Unit(UnitType.TANK, Team.PLAYER, Pos(0, 0))
        // Mech counters armor hard, so the counterattack is non-zero.
        val enemy = Unit(UnitType.MECH, Team.ENEMY, Pos(2, 0))
        val battle = battleOf(map, tank, enemy)

        battle.onTap(Pos(0, 0))            // select
        battle.onTap(Pos(1, 0))            // preview move adjacent to enemy
        assertEquals(Battle.Phase.ACTION, battle.phase)
        battle.onTap(Pos(2, 0))            // attack

        assertTrue(enemy.hp < Unit.MAX_HP, "enemy should have taken damage")
        assertTrue(tank.hp < Unit.MAX_HP, "adjacent mech should counter the tank")
        assertEquals(Pos(1, 0), tank.pos, "attack commits the previewed move")
        assertTrue(tank.hasActed)
        assertEquals(Battle.Phase.IDLE, battle.phase)
    }

    @Test
    fun artillery_cannot_move_and_fire_but_can_fire_in_place() {
        val map = flatMap(6, 3)
        val arty = Unit(UnitType.ARTILLERY, Team.PLAYER, Pos(0, 1))
        val enemy = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(2, 1)) // manhattan 2 from origin
        val battle = battleOf(map, arty, enemy)

        // Fire in place: select, then tap own tile -> targets available.
        battle.onTap(Pos(0, 1))
        battle.onTap(Pos(0, 1))
        assertEquals(Battle.Phase.ACTION, battle.phase)
        battle.onTap(Pos(2, 1))
        assertTrue(enemy.hp < Unit.MAX_HP, "artillery should hit from range without moving")
        // Indirect units take no counter.
        assertEquals(Unit.MAX_HP, arty.hp)
    }

    @Test
    fun artillery_that_moves_gets_no_targets() {
        val map = flatMap(6, 3)
        val arty = Unit(UnitType.ARTILLERY, Team.PLAYER, Pos(0, 1))
        val enemy = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(3, 1))
        val battle = battleOf(map, arty, enemy)

        battle.onTap(Pos(0, 1))            // select
        battle.onTap(Pos(1, 1))            // preview move — indirect can't then fire
        assertEquals(Battle.Phase.ACTION, battle.phase)
        assertTrue(battle.targets.isEmpty(), "moved indirect unit has no targets")
        battle.waitHere()                  // confirm the move
        assertEquals(Unit.MAX_HP, enemy.hp, "no shot after moving")
        assertTrue(arty.hasActed)
        assertEquals(Pos(1, 1), arty.pos)
    }

    @Test
    fun eliminating_the_last_enemy_wins() {
        val map = flatMap(3, 1)
        val tank = Unit(UnitType.TANK, Team.PLAYER, Pos(0, 0))
        val enemy = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(1, 0), hp = 1)
        val battle = battleOf(map, tank, enemy)

        battle.onTap(Pos(0, 0))            // select (already adjacent)
        battle.onTap(Pos(0, 0))            // stay -> targeting
        battle.onTap(Pos(1, 0))            // attack, kills the 1-HP infantry

        assertEquals(Team.PLAYER, battle.winner)
        assertTrue(battle.units.none { it.team == Team.ENEMY })
    }

    @Test
    fun ending_the_turn_runs_the_enemy_and_returns_to_player_next_day() {
        val map = flatMap(9, 1)
        val inf = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0))
        val enemy = Unit(UnitType.INFANTRY, Team.ENEMY, Pos(8, 0)) // far away, no clash
        val battle = battleOf(map, inf, enemy)

        assertEquals(1, battle.day)
        battle.endPlayerTurn()

        assertEquals(Team.PLAYER, battle.turn)
        assertEquals(2, battle.day)
        assertNull(battle.winner)
        assertTrue(!inf.hasActed, "player units are reset ready for the new turn")
    }

    @Test
    fun restart_restores_the_default_scenario() {
        val battle = Battle()
        val startCount = battle.units.size
        // Mutate: kill a unit.
        battle.units.removeAt(0)
        assertTrue(battle.units.size < startCount)

        battle.restart()
        assertEquals(startCount, battle.units.size)
        assertEquals(1, battle.day)
        assertEquals(Team.PLAYER, battle.turn)
        assertNull(battle.winner)
    }

    @Test
    fun enemy_advances_toward_the_player_when_it_cannot_attack() {
        // 13-wide corridor: a move-8 Recon at x=12 cannot reach the player at x=0
        // this turn, so it should close distance rather than attack.
        val map = flatMap(13, 1)
        val inf = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0))
        val enemy = Unit(UnitType.RECON, Team.ENEMY, Pos(12, 0))
        val battle = battleOf(map, inf, enemy)

        val before = enemy.pos.x
        battle.endPlayerTurn()
        assertNotNull(battle)
        assertTrue(enemy.pos.x < before, "enemy should advance toward the player")
        assertEquals(Unit.MAX_HP, inf.hp, "player should not have been reachable to attack")
    }
}

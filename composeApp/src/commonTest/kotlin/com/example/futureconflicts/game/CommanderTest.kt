package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommanderTest {

    // Deal one tank->mech hit and report the mech's remaining HP.
    private fun mechHpAfterHit(
        attackerElite: Boolean = false,
        playerCmd: Commander? = null,
        enemyCmd: Commander? = null,
    ): Int {
        val mech = Unit(UnitType.MECH, Team.ENEMY, Pos(1, 0))
        val battle = Battle(
            Scenario(
                flatMap(3, 1),
                listOf(Unit(UnitType.TANK, Team.PLAYER, Pos(0, 0), elite = attackerElite), mech),
            ),
            playerCommander = playerCmd,
            enemyCommander = enemyCmd,
        )
        battle.apply(Action.Attack(Pos(0, 0), Pos(0, 0), Pos(1, 0)))
        return mech.hp
    }

    @Test
    fun move_passive_adds_movement() {
        val inf = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0))
        val battle = battleWithCmd(inf, Commanders.STORM) // STORM: +1 Move
        assertEquals(4, battle.effectiveMove(inf))
    }

    @Test
    fun range_passive_extends_indirect_units_only() {
        val arty = Unit(UnitType.ARTILLERY, Team.PLAYER, Pos(0, 0))
        val inf = Unit(UnitType.INFANTRY, Team.PLAYER, Pos(1, 0))
        val battle = Battle(
            Scenario(flatMap(5, 1), listOf(arty, inf, Unit(UnitType.INFANTRY, Team.ENEMY, Pos(4, 0)))),
            playerCommander = Commanders.SIEGE, // SIEGE: +1 Range
        )
        assertEquals(4, battle.effectiveMaxRange(arty), "artillery gains range")
        assertEquals(1, battle.effectiveMaxRange(inf), "direct units stay melee")
    }

    @Test
    fun firepower_passive_increases_damage_dealt() {
        val plain = mechHpAfterHit(playerCmd = null)
        val boosted = mechHpAfterHit(playerCmd = Commanders.IRON) // +10% Firepower
        assertTrue(boosted < plain, "firepower should raise damage: $boosted !< $plain")
    }

    @Test
    fun armor_passive_reduces_damage_taken() {
        val plain = mechHpAfterHit(enemyCmd = null)
        val armored = mechHpAfterHit(enemyCmd = Commanders.IRON) // +15% Armor on the defender
        assertTrue(armored > plain, "armor should reduce damage: $armored !> $plain")
    }

    @Test
    fun elite_units_move_further_and_hit_harder() {
        val recon = Unit(UnitType.RECON, Team.PLAYER, Pos(0, 0))
        val eliteRecon = Unit(UnitType.RECON, Team.PLAYER, Pos(0, 0), elite = true)
        val battle = Battle(Scenario(flatMap(5, 5), listOf(recon)))
        assertEquals(8, battle.effectiveMove(recon))
        // Elite bonus is applied even without a commander.
        val eliteBattle = Battle(Scenario(flatMap(5, 5), listOf(eliteRecon)))
        assertEquals(9, eliteBattle.effectiveMove(eliteRecon))

        assertTrue(mechHpAfterHit(attackerElite = true) < mechHpAfterHit(attackerElite = false))
    }

    @Test
    fun income_passive_boosts_gold() {
        val battle = Battle(
            Scenario(
                flatMap(9, 1),
                units = listOf(
                    Unit(UnitType.INFANTRY, Team.PLAYER, Pos(2, 0)),
                    Unit(UnitType.INFANTRY, Team.ENEMY, Pos(8, 0)),
                ),
                buildings = listOf(
                    Building(Pos(0, 0), Building.Kind.HQ, Team.PLAYER),
                    Building(Pos(1, 0), Building.Kind.CITY, Team.PLAYER, level = 1),
                ),
            ),
            playerCommander = Commanders.STORM, // +10% income
        )
        val before = battle.goldOf(Team.PLAYER)
        battle.endPlayerTurn()
        // base 200 (HQ+city) +10% = 220.
        assertEquals(before + 220, battle.goldOf(Team.PLAYER))
    }

    @Test
    fun elite_signature_pricing_and_discount() {
        val storm = battleWithCmd(Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)), Commanders.STORM)
        // STORM signature is RECON (cost 4000) -> elite costs 2x.
        assertEquals(8000, storm.buildCost(Team.PLAYER, UnitType.RECON, elite = true))
        assertNull(storm.buildCost(Team.PLAYER, UnitType.TANK, elite = true), "elite only for signature")

        val iron = battleWithCmd(Unit(UnitType.INFANTRY, Team.PLAYER, Pos(0, 0)), Commanders.IRON)
        // IRON: -10% unit cost -> Tank 7000 -> 6300.
        assertEquals(6300, iron.buildCost(Team.PLAYER, UnitType.TANK, elite = false))
    }

    @Test
    fun losing_the_commander_raises_its_rebuy_cost() {
        val battle = Battle(
            Scenario(
                flatMap(5, 1),
                listOf(
                    Unit(UnitType.COMMANDER, Team.PLAYER, Pos(0, 0), hp = 1),
                    Unit(UnitType.INFANTRY, Team.PLAYER, Pos(4, 0)), // keeps the game alive
                    Unit(UnitType.TANK, Team.ENEMY, Pos(1, 0)),      // will kill the commander
                ),
            ),
            playerCommander = Commanders.STORM, // rebuy x1.5
        )
        assertEquals(16000, battle.commanderPrice(Team.PLAYER))
        assertTrue(battle.hasCommander(Team.PLAYER))

        battle.endPlayerTurn() // enemy tank executes the 1-HP commander

        assertFalse(battle.hasCommander(Team.PLAYER))
        assertEquals(24000, battle.commanderPrice(Team.PLAYER)) // 16000 * 1.5
    }

    private fun battleWithCmd(unit: Unit, cmd: Commander): Battle =
        Battle(
            Scenario(flatMap(6, 6), listOf(unit, Unit(UnitType.INFANTRY, Team.ENEMY, Pos(5, 5)))),
            playerCommander = cmd,
        )
}

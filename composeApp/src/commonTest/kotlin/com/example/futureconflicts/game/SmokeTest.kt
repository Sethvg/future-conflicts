package com.example.futureconflicts.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sanity check that the host test pipeline runs the pure game core. */
class SmokeTest {
    @Test
    fun scenario_loads_with_both_armies() {
        val battle = Battle()
        assertTrue(battle.units.any { it.team == Team.PLAYER })
        assertTrue(battle.units.any { it.team == Team.ENEMY })
        assertEquals(Team.PLAYER, battle.turn)
    }
}

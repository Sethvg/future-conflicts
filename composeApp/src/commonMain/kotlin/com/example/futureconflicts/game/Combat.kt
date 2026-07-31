package com.example.futureconflicts.game

import kotlin.math.roundToInt

/**
 * Damage resolution, modelled on Advance Wars.
 *
 * Base damage is a percentage (of a full-health target) looked up by the
 * attacker/defender type matchup. The realised HP loss then scales with the
 * attacker's remaining health and is mitigated by the defender's terrain stars:
 *
 *   dmgHp = base/10 · (attackerHp/10) · (1 − 0.1 · defStars · defenderHp/10)
 *
 * Ranges are Manhattan. Direct units (range 1) receive a counterattack from a
 * surviving defender that is in range; indirect units never trigger a counter.
 */
object Combat {

    /** base[attacker][defender] = base damage percent vs a full-HP defender. */
    private val base: Map<UnitType, Map<UnitType, Int>> = mapOf(
        UnitType.INFANTRY to mapOf(
            UnitType.INFANTRY to 55, UnitType.MECH to 45, UnitType.RECON to 12,
            UnitType.TANK to 5, UnitType.ARTILLERY to 15,
        ),
        UnitType.MECH to mapOf(
            UnitType.INFANTRY to 65, UnitType.MECH to 55, UnitType.RECON to 18,
            UnitType.TANK to 55, UnitType.ARTILLERY to 70,
        ),
        UnitType.RECON to mapOf(
            UnitType.INFANTRY to 70, UnitType.MECH to 65, UnitType.RECON to 35,
            UnitType.TANK to 6, UnitType.ARTILLERY to 45,
        ),
        UnitType.TANK to mapOf(
            UnitType.INFANTRY to 75, UnitType.MECH to 70, UnitType.RECON to 85,
            UnitType.TANK to 55, UnitType.ARTILLERY to 70,
        ),
        UnitType.ARTILLERY to mapOf(
            UnitType.INFANTRY to 90, UnitType.MECH to 85, UnitType.RECON to 80,
            UnitType.TANK to 70, UnitType.ARTILLERY to 75,
        ),
    )

    fun basePercent(attacker: UnitType, defender: UnitType): Int =
        base[attacker]?.get(defender) ?: 0

    /**
     * HP damage [attacker] deals to [defender] standing on [defenderTerrain].
     * Does not mutate anything.
     */
    fun damage(attacker: Unit, defender: Unit, defenderTerrain: Terrain): Int {
        val b = basePercent(attacker.type, defender.type)
        if (b == 0) return 0
        val raw = (b / 10.0) *
            (attacker.hp.toDouble() / Unit.MAX_HP) *
            (1.0 - 0.1 * defenderTerrain.defense * (defender.hp.toDouble() / Unit.MAX_HP))
        return raw.roundToInt().coerceIn(0, defender.hp)
    }

    /** True if [attacker] at [from] can strike a unit at [target]. */
    fun inRange(attacker: UnitType, from: Pos, target: Pos): Boolean {
        val d = from.manhattan(target)
        return d in attacker.minRange..attacker.maxRange
    }
}

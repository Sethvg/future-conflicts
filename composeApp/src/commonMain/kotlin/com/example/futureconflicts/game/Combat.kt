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

    /**
     * base[attacker][defender] = base damage percent vs a full-HP defender.
     * **Air units (Gunship) appear as a defender only in the Anti-Air and Gunship rows** —
     * ground/indirect units have no entry vs air, so they simply cannot target it.
     */
    private val base: Map<UnitType, Map<UnitType, Int>> = mapOf(
        UnitType.INFANTRY to mapOf(
            UnitType.INFANTRY to 55, UnitType.MECH to 45, UnitType.RECON to 12,
            UnitType.TANK to 5, UnitType.ARTILLERY to 15, UnitType.COMMANDER to 8,
            UnitType.ANTI_AIR to 10,
        ),
        UnitType.MECH to mapOf(
            UnitType.INFANTRY to 65, UnitType.MECH to 55, UnitType.RECON to 18,
            UnitType.TANK to 55, UnitType.ARTILLERY to 70, UnitType.COMMANDER to 45,
            UnitType.ANTI_AIR to 55,
        ),
        UnitType.RECON to mapOf(
            UnitType.INFANTRY to 70, UnitType.MECH to 65, UnitType.RECON to 35,
            UnitType.TANK to 6, UnitType.ARTILLERY to 45, UnitType.COMMANDER to 8,
            UnitType.ANTI_AIR to 60,
        ),
        UnitType.TANK to mapOf(
            UnitType.INFANTRY to 75, UnitType.MECH to 70, UnitType.RECON to 85,
            UnitType.TANK to 55, UnitType.ARTILLERY to 70, UnitType.COMMANDER to 45,
            UnitType.ANTI_AIR to 65,
        ),
        UnitType.ARTILLERY to mapOf(
            UnitType.INFANTRY to 90, UnitType.MECH to 85, UnitType.RECON to 80,
            UnitType.TANK to 70, UnitType.ARTILLERY to 75, UnitType.COMMANDER to 65,
            UnitType.ANTI_AIR to 75,
        ),
        UnitType.COMMANDER to mapOf(
            UnitType.INFANTRY to 90, UnitType.MECH to 85, UnitType.RECON to 90,
            UnitType.TANK to 75, UnitType.ARTILLERY to 90, UnitType.COMMANDER to 60,
            UnitType.ANTI_AIR to 80,
        ),
        UnitType.ANTI_AIR to mapOf(
            UnitType.INFANTRY to 60, UnitType.MECH to 55, UnitType.RECON to 55,
            UnitType.TANK to 25, UnitType.ARTILLERY to 45, UnitType.COMMANDER to 30,
            UnitType.ANTI_AIR to 45, UnitType.GUNSHIP to 105,
        ),
        UnitType.GUNSHIP to mapOf(
            UnitType.INFANTRY to 75, UnitType.MECH to 70, UnitType.RECON to 70,
            UnitType.TANK to 55, UnitType.ARTILLERY to 65, UnitType.COMMANDER to 50,
            UnitType.ANTI_AIR to 45, UnitType.GUNSHIP to 55,
        ),
    )

    fun basePercent(attacker: UnitType, defender: UnitType): Int =
        base[attacker]?.get(defender) ?: 0

    /** Whether [attacker] can hit [defender]. Air units are hit only by [UnitType.hitsAir]
     *  attackers (explicit — not inferred from a zero in the damage table); ground/naval
     *  targets just need a non-zero matchup. */
    fun canTarget(attacker: UnitType, defender: UnitType): Boolean =
        if (defender.air) attacker.hitsAir else basePercent(attacker, defender) > 0

    /**
     * HP damage [attacker] deals to [defender] standing on [defenderTerrain].
     * [attackMul]/[defenseMul] fold in Commander/Elite firepower & armor (1.0 = none).
     * Does not mutate anything.
     */
    fun damage(
        attacker: Unit,
        defender: Unit,
        defenderTerrain: Terrain,
        attackMul: Double = 1.0,
        defenseMul: Double = 1.0,
    ): Int {
        val b = basePercent(attacker.type, defender.type)
        if (b == 0) return 0
        val raw = (b / 10.0) *
            (attacker.hp.toDouble() / Unit.MAX_HP) *
            (1.0 - 0.1 * defenderTerrain.defense * (defender.hp.toDouble() / Unit.MAX_HP))
        return (raw * attackMul * defenseMul).roundToInt().coerceIn(0, defender.hp)
    }

    /** True if a unit with the given range band at [from] can strike [target]. */
    fun inRange(from: Pos, target: Pos, minRange: Int, maxRange: Int): Boolean {
        val d = from.manhattan(target)
        return d in minRange..maxRange
    }

    /** Convenience using a unit type's base range band. */
    fun inRange(attacker: UnitType, from: Pos, target: Pos): Boolean =
        inRange(from, target, attacker.minRange, attacker.maxRange)
}

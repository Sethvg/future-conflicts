package com.example.futureconflicts.game

/**
 * The Commander/Elite **stat pipeline** and combat-math helpers: how base unit stats
 * are folded with army-wide passives and Elite bonuses into effective firepower, armor,
 * damage, range, and reachability. Pure derivations over [Battle] state.
 *
 * (Implementation split out of [Battle] — see that file's header for the layout.)
 */

internal fun Battle.passive(team: Team, kind: PassiveKind): Int =
    commanders[team]?.amount(kind) ?: 0

/** Movement points [u] actually has: base + MOVE passive + Elite bonus. */
fun Battle.effectiveMove(u: Unit): Int {
    var m = u.type.maxMove + passive(u.team, PassiveKind.MOVE)
    if (u.elite) m += Elite.MOVE_BONUS
    return m.coerceAtLeast(1)
}

/** Range passives only extend indirect units, so direct units stay melee. */
fun Battle.effectiveMaxRange(u: Unit): Int =
    if (u.type.indirect) u.type.maxRange + passive(u.team, PassiveKind.RANGE) else u.type.maxRange

internal fun Battle.attackMul(u: Unit): Double {
    var pct = passive(u.team, PassiveKind.FIREPOWER)
    if (u.elite) pct += Elite.FIREPOWER_BONUS
    return 1.0 + pct / 100.0
}

internal fun Battle.defenseMul(u: Unit): Double {
    var pct = passive(u.team, PassiveKind.ARMOR)
    if (u.elite) pct += Elite.ARMOR_BONUS
    return (1.0 - pct / 100.0).coerceAtLeast(0.1)
}

internal fun Battle.damageOf(attacker: Unit, defender: Unit): Int =
    Combat.damage(attacker, defender, map[defender.pos], attackMul(attacker), defenseMul(defender))

internal fun Battle.inRangeEff(u: Unit, from: Pos, target: Pos): Boolean =
    Combat.inRange(from, target, u.type.minRange, effectiveMaxRange(u))

internal fun Battle.canAct(u: Unit): Boolean = u.team == turn && !u.hasActed

internal fun Battle.reachableFor(u: Unit): Map<Pos, Int> =
    Movement.reachable(map, u, { unitAt(it) }, effectiveMove(u))

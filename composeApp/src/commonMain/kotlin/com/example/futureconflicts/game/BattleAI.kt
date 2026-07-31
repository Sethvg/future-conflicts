package com.example.futureconflicts.game

/**
 * The enemy (Red Army) planner: a greedy per-unit AI that emits [Action]s through the
 * same [Battle.apply] funnel the player uses. Kept in its own file so AI work doesn't
 * collide with rules/turn changes.
 *
 * (Implementation split out of [Battle] — see that file's header for the layout.)
 */

/** Greedy AI: best attack, else capture a reachable building, else advance. */
internal fun Battle.runEnemyTurn() {
    for (u in units.filter { it.team == Team.ENEMY }.toList()) {
        // hasActed skips units the drone phase already flew — the planner must never
        // re-drive an autonomous drone (and re-planning them would be wasted work).
        if (!u.alive || u.hasActed || winner != null) continue
        planEnemy(u)?.let { apply(it) }
    }
    if (winner == null) message = "Red Army finished its turn."
}

internal fun Battle.planEnemy(u: Unit): Action? {
    val reach = reachableFor(u)
    val stand = reach.keys + u.pos

    // 1. Best attack across every reachable firing position.
    var bestTile: Pos? = null
    var bestTarget: Pos? = null
    var bestScore = -1
    for (tile in stand) {
        if (u.type.indirect && tile != u.pos) continue
        for (victim in units.filter { it.alive && it.team == Team.PLAYER }) {
            if (!inRangeEff(u, tile, victim.pos)) continue
            if (!Combat.canTarget(u.type, victim.type)) continue
            val saved = u.pos
            u.pos = tile
            val dmg = damageOf(u, victim)
            u.pos = saved
            val score = dmg + if (dmg >= victim.hp) 100 else 0
            if (score > bestScore) { bestScore = score; bestTile = tile; bestTarget = victim.pos }
        }
    }
    if (bestTile != null && bestTarget != null) return Action.Attack(u.pos, bestTile, bestTarget)

    // 2. Capture a reachable enemy/neutral building (foot units only).
    if (u.type.canCapture) {
        val cap = stand.firstOrNull { buildings[it]?.let { b -> b.owner != Team.ENEMY } == true }
        if (cap != null) return Action.Capture(u.pos, cap)
    }

    // 3. Advance toward the nearest player unit.
    val nearest = units.filter { it.alive && it.team == Team.PLAYER }
        .minByOrNull { it.pos.manhattan(u.pos) } ?: return Action.Wait(u.pos, u.pos)
    val step = reach.keys.minByOrNull { it.manhattan(nearest.pos) }
    return if (step != null && step.manhattan(nearest.pos) < u.pos.manhattan(nearest.pos)) {
        Action.Wait(u.pos, step)
    } else {
        Action.Wait(u.pos, u.pos)
    }
}

package com.example.futureconflicts.game

/**
 * The interactive tap-handling implementation behind [Battle.onTap]: selecting units,
 * previewing a move, entering the action phase, and maintaining the selection/preview
 * state the renderer reads. Convenience over [Battle.apply]; never the source of truth.
 *
 * (Implementation split out of [Battle] — see that file's header for the layout.)
 */

internal fun Battle.onTapIdle(p: Pos) {
    val u = unitAt(p)
    if (u != null && u.team == Team.PLAYER && !u.hasActed) { select(u); return }
    val b = buildings[p]
    if (b != null && b.owner == Team.PLAYER && u == null) {
        when {
            b.kind == Building.Kind.CITY -> {
                upgradeAt = p
                message = if (b.level >= Economy.CITY_MAX_LEVEL) "City is fully upgraded."
                else "Upgrade city to L${b.level + 1}? (${Economy.CITY_UPGRADE_COST}g)"
            }
            b.kind == Building.Kind.DRONE_COMMAND -> {
                upgradeAt = p
                message = if (b.level >= Drones.MAX_LEVEL) "Drone Command is at max (${b.level} drones)."
                else "Upgrade Drone Command to L${b.level + 1}? (${Drones.UPGRADE_COST}g)"
            }
            b.kind.builds != null -> { buildMenuAt = p; message = "Build a unit here." }
        }
        return
    }
    clearSelection()
}

internal fun Battle.onTapMoving(p: Pos) {
    val u = selected ?: run { clearSelection(); return }
    val other = unitAt(p)
    if (other != null && other.team == Team.PLAYER && !other.hasActed && other !== u) {
        select(other); return
    }
    if (p == u.pos || reachable.containsKey(p)) {
        enterActionPhase(u, p)
    } else {
        clearSelection()
    }
}

internal fun Battle.onTapAction(p: Pos) {
    val u = selected ?: run { clearSelection(); return }
    val dest = previewPos ?: u.pos
    when {
        p in targets -> apply(Action.Attack(u.pos, dest, p))
        p == dest -> if (canCaptureHere) apply(Action.Capture(u.pos, dest))
                     else apply(Action.Wait(u.pos, dest))
        else -> resetToMoving(u) // tap elsewhere cancels back to move selection
    }
}

/** Return the selected unit to the MOVING phase (shared by tap-elsewhere and Cancel). */
internal fun Battle.resetToMoving(u: Unit) {
    phase = Battle.Phase.MOVING
    previewPos = u.pos
    targets = emptySet()
    canCaptureHere = false
    reachable = reachableFor(u)
}

internal fun Battle.select(u: Unit) {
    dismissMenus()
    selected = u
    previewPos = u.pos
    reachable = reachableFor(u)
    targets = emptySet()
    canCaptureHere = false
    phase = Battle.Phase.MOVING
    message = "${u.type.label}: tap where to move."
}

internal fun Battle.enterActionPhase(u: Unit, dest: Pos) {
    previewPos = dest
    reachable = emptyMap()
    targets = if (u.type.indirect && dest != u.pos) emptySet()
    else units.filter {
        it.alive && it.team != u.team && inRangeEff(u, dest, it.pos) &&
            Combat.canTarget(u.type, it.type)
    }.map { it.pos }.toSet()
    canCaptureHere = u.type.canCapture && buildings[dest]?.let { it.owner != u.team } == true
    phase = Battle.Phase.ACTION
    message = "Choose an action."
}

internal fun Battle.clearSelection() {
    selected = null
    previewPos = null
    reachable = emptyMap()
    targets = emptySet()
    canCaptureHere = false
    phase = Battle.Phase.IDLE
}

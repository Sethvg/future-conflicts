package com.example.futureconflicts.game

/**
 * The action executors the [Battle.apply] funnel dispatches to (move / attack / capture /
 * build / upgrade / supply drop), plus combat resolution, unit removal, and the victory
 * check. Every game-state mutation ultimately runs through here.
 *
 * (Implementation split out of [Battle] — see that file's header for the layout.)
 */

/** Validate + perform the movement part of an action. Returns the unit, or null. */
internal fun Battle.execMove(from: Pos, to: Pos): Unit? {
    val u = unitAt(from) ?: return null
    if (!canAct(u)) return null
    if (to != from && !reachableFor(u).containsKey(to)) return null
    u.pos = to
    return u
}

internal fun Battle.execAttack(a: Action.Attack): Boolean {
    val u = unitAt(a.from) ?: return false
    if (!canAct(u)) return false
    if (u.type.indirect && a.to != a.from) return false           // can't move and fire
    if (a.to != a.from && !reachableFor(u).containsKey(a.to)) return false
    val victim = unitAt(a.target) ?: return false
    if (victim.team == u.team) return false
    if (!inRangeEff(u, a.to, a.target)) return false
    if (!Combat.canTarget(u.type, victim.type)) return false // e.g. ground units can't hit air
    u.pos = a.to
    resolveAttack(u, victim)
    finishUnit(u, null)
    return true
}

internal fun Battle.execCapture(a: Action.Capture): Boolean {
    val u = unitAt(a.from) ?: return false
    if (!canAct(u) || !u.type.canCapture) return false
    if (a.to != a.from && !reachableFor(u).containsKey(a.to)) return false
    val b = buildings[a.to] ?: return false
    if (b.owner == u.team) return false
    u.pos = a.to
    b.captureLeft -= u.hp
    if (b.captureLeft <= 0) {
        b.owner = u.team
        b.level = 1                       // razed on capture — upgrades are lost
        b.captureLeft = Economy.CAPTURE_POINTS
        if (b.kind == Building.Kind.HQ) {
            winner = u.team
            message = "${u.team.label} seized the enemy HQ — victory!"
        } else {
            message = "${u.team.label} captured a city."
        }
    } else {
        message = "${u.team.label} capturing (${b.captureLeft} to go)."
    }
    finishUnit(u, null)
    return true
}

internal fun Battle.execBuild(a: Action.Build): Boolean {
    val b = buildings[a.at] ?: return false
    if (b.owner != turn) return false
    // The Commander is an HQ-only purchase; every other unit needs a production
    // building whose category matches (the HQ also builds the infantry category).
    if (a.type == UnitType.COMMANDER) {
        if (b.kind != Building.Kind.HQ || hasCommander(turn)) return false
    } else if (b.kind.builds != a.type.category) {
        return false
    }
    val cost = buildCost(turn, a.type, a.elite) ?: return false
    val ps = players.getValue(turn)
    if (ps.gold < cost) return false
    val spawn = spawnTileFor(a.at, a.type) ?: return false
    ps.gold -= cost
    units.add(Unit(a.type, turn, spawn, elite = a.elite).also { it.hasActed = true })
    val name = if (a.elite) "Elite ${a.type.label}" else a.type.label
    message = "${turn.label} built $name (−${cost}g)."
    return true
}

internal fun Battle.execUpgrade(a: Action.Upgrade): Boolean {
    val b = buildings[a.at] ?: return false
    if (b.owner != turn) return false
    val ps = players.getValue(turn)
    return when (b.kind) {
        Building.Kind.CITY -> {
            if (b.level >= Economy.CITY_MAX_LEVEL) return false
            if (ps.gold < Economy.CITY_UPGRADE_COST) return false
            ps.gold -= Economy.CITY_UPGRADE_COST
            b.level++
            message = "City upgraded to L${b.level} (+${Economy.CITY_INCOME_PER_LEVEL}/turn)."
            true
        }
        // A Drone Command's level *is* its flight size, so upgrading buys another drone.
        Building.Kind.DRONE_COMMAND -> {
            if (b.level >= Drones.MAX_LEVEL) return false
            if (ps.gold < Drones.UPGRADE_COST) return false
            ps.gold -= Drones.UPGRADE_COST
            b.level++
            message = "Drone Command upgraded to L${b.level} (${b.level} drones)."
            true
        }
        else -> false
    }
}

/** Apply a supply-drop boon to [a.team]. Effects are fixed by [a.kind] (no RNG here). */
internal fun Battle.execSupplyDrop(a: Action.SupplyDrop): Boolean {
    val team = a.team
    lastSupplyKind = a.kind
    lastSupplyTeam = team
    when (a.kind) {
        SupplyKind.GOLD -> {
            players.getValue(team).gold += Supply.GOLD_WINDFALL
            message = "${team.label} supply drop: +${Supply.GOLD_WINDFALL}g."
        }
        SupplyKind.REINFORCE -> {
            val hq = buildings.values.firstOrNull { it.owner == team && it.kind == Building.Kind.HQ }
            val spawn = hq?.let { spawnTileFor(it.pos, Supply.REINFORCE_UNIT) }
            if (spawn != null) {
                units.add(Unit(Supply.REINFORCE_UNIT, team, spawn).also { it.hasActed = true })
                message = "${team.label} supply drop: ${Supply.REINFORCE_UNIT.label} reinforcements."
            } else {
                // No HQ / no landing zone — never waste the drop, pay out gold instead.
                players.getValue(team).gold += Supply.GOLD_WINDFALL
                message = "${team.label} supply drop: +${Supply.GOLD_WINDFALL}g (no landing zone)."
            }
        }
        SupplyKind.HEAL -> {
            units.filter { it.alive && it.team == team }.forEach {
                it.hp += Supply.HEAL_AMOUNT
                it.clampHp()
            }
            message = "${team.label} supply drop: field repairs (+${Supply.HEAL_AMOUNT} HP)."
        }
        SupplyKind.REVEAL -> {
            supplyRevealFor = team
            message = "${team.label} supply drop: recon sweep reveals the map."
        }
    }
    return true
}

/** Free tile the new unit's class can occupy: the building's tile, else a neighbour.
 *  (A ship must launch onto water, a ground unit onto land — hence the class check.) */
internal fun Battle.spawnTileFor(at: Pos, type: UnitType = UnitType.INFANTRY): Pos? {
    val cls = type.unitClass
    if (unitAt(at) == null && cls.canEnter(map[at])) return at
    return map.neighbors(at).firstOrNull { cls.canEnter(map[it]) && unitAt(it) == null }
}

internal fun Battle.resolveAttack(attacker: Unit, defender: Unit) {
    val dealt = damageOf(attacker, defender)
    defender.hp -= dealt
    defender.clampHp()

    var note = "${attacker.type.label} hits ${defender.type.label} for $dealt."
    if (defender.alive &&
        !defender.type.indirect &&
        Combat.canTarget(defender.type, attacker.type) &&
        inRangeEff(defender, defender.pos, attacker.pos)
    ) {
        val back = damageOf(defender, attacker)
        attacker.hp -= back
        attacker.clampHp()
        note += " Counter for $back."
    }
    removeDead()
    message = note
}

internal fun Battle.removeDead() {
    for (u in units) {
        if (u.alive) continue
        // A destroyed Commander compounds that side's future rebuy cost.
        if (u.type == UnitType.COMMANDER) players.getValue(u.team).commanderLosses++
        // A downed drone's slot sits empty for a cooldown instead of costing gold.
        if (u.type == UnitType.DRONE) noteDroneLost(u.team)
    }
    units.removeAll { !it.alive }
}

internal fun Battle.finishUnit(u: Unit, msg: String?) {
    u.hasActed = true
    clearSelection()
    if (msg != null) message = msg
    checkVictory()
}

internal fun Battle.checkVictory() {
    if (winner != null) return // a captured HQ already decided it
    // Drones are free, respawning recon — they don't keep a defeated army "alive",
    // otherwise a side with only a Drone Command could never be eliminated.
    fun fields(t: Team) = units.any { it.team == t && it.type != UnitType.DRONE }
    val playerAlive = fields(Team.PLAYER)
    val enemyAlive = fields(Team.ENEMY)
    winner = when {
        !enemyAlive -> Team.PLAYER
        !playerAlive -> Team.ENEMY
        else -> null
    }
    if (winner != null) message = "${winner!!.label} wins! Tap Restart."
}

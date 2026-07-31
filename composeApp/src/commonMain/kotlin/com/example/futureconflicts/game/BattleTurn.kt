package com.example.futureconflicts.game

/**
 * The turn lifecycle: ending the player's turn hands off to the enemy and back, and
 * `beginTurn` grants income, resets action flags, refuels/crashes aircraft, and rolls
 * the periodic supply drop. Fuel handling lives here since it fires at each turn's start.
 *
 * (Implementation split out of [Battle] — see that file's header for the layout.)
 */

internal fun Battle.advanceTurn() {
    clearSelection()
    dismissMenus()
    beginTurn(Team.ENEMY)
    runEnemyTurn()
    if (winner != null) return
    beginTurn(Team.PLAYER)
}

internal fun Battle.beginTurn(team: Team) {
    turn = team
    if (team == Team.PLAYER) { day++; message = "Blue Army — Day $day." }
    if (supplyRevealFor == team) supplyRevealFor = null // a prior recon sweep expires
    turnsTaken[team] = turnsTaken.getValue(team) + 1
    val base = buildings.values.filter { it.owner == team }.sumOf { it.incomePerTurn }
    players.getValue(team).gold += base + base * passive(team, PassiveKind.INCOME) / 100
    units.filter { it.team == team }.forEach { it.hasActed = false }
    refuelOrCrash(team)
    // The autonomous drone flight acts before the owner takes manual control.
    runDronePhase(team)
    clearSelection()
    // Every INTERVAL turns a side takes, it receives a seeded supply drop.
    if (turnsTaken.getValue(team) % Supply.INTERVAL == 0) {
        apply(Action.SupplyDrop(team, Supply.roll(rng)))
    }
}

/** Air units refuel on an owned Airport/HQ at turn start; otherwise they burn fuel and,
 *  if it runs dry, crash (are lost). */
internal fun Battle.refuelOrCrash(team: Team) {
    val crashed = ArrayList<Unit>()
    for (u in units) {
        if (u.team != team || !u.type.fuelLimited) continue
        val b = buildings[u.pos]
        // Which buildings service a unit is the unit class's business (air -> Airport/HQ).
        val onBase = b != null && b.owner == team && u.refuelsAt(b.kind)
        if (onBase) {
            u.fuel = u.type.maxFuel
        } else {
            u.fuel -= Battle.FUEL_BURN_PER_TURN
            if (u.fuel <= 0) crashed += u
        }
    }
    if (crashed.isNotEmpty()) {
        crashed.filter { it.type == UnitType.DRONE }.forEach { noteDroneLost(it.team, fromCrash = true) }
        units.removeAll(crashed)
        message = "${team.label} lost ${crashed.size} aircraft to fuel exhaustion."
        checkVictory() // a fuel crash can eliminate a side's last unit
    }
}

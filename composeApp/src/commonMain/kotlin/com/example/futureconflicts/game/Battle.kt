package com.example.futureconflicts.game

/**
 * The whole game state and its rules: turn order, selection/move/attack flow,
 * a simple enemy AI, and victory detection. Pure logic — no rendering, no
 * platform, no Compose — so it can be unit-tested on the host.
 *
 * The UI drives this with exactly two verbs: [onTap] (a grid cell was tapped)
 * and [endPlayerTurn] (the End Turn button). Everything else is read-only state
 * the renderer observes.
 */
class Battle(scenario: Scenario = Scenarios.twinRidges()) {

    val map: GameMap = scenario.map
    val units: MutableList<Unit> = scenario.units.toMutableList()

    var turn: Team = Team.PLAYER
        private set
    var day: Int = 1
        private set
    var winner: Team? = null
        private set
    var message: String = "Blue Army — Day 1. Tap a unit to command it."
        private set

    // ---- Selection / interaction state (read by the renderer) ----
    enum class Phase { IDLE, MOVING, TARGETING }

    var phase: Phase = Phase.IDLE
        private set
    var selected: Unit? = null
        private set
    var reachable: Map<Pos, Int> = emptyMap()
        private set
    var targets: Set<Pos> = emptySet()
        private set

    fun unitAt(p: Pos): Unit? = units.firstOrNull { it.alive && it.pos == p }

    // ---------------------------------------------------------------
    // Player input
    // ---------------------------------------------------------------

    fun onTap(p: Pos) {
        if (winner != null || turn != Team.PLAYER) return
        if (!map.inBounds(p)) { clearSelection(); return }

        when (phase) {
            Phase.IDLE -> selectFriendly(p)
            Phase.MOVING -> handleMoveTap(p)
            Phase.TARGETING -> handleTargetTap(p)
        }
    }

    private fun selectFriendly(p: Pos) {
        val u = unitAt(p)
        if (u != null && u.team == Team.PLAYER && !u.hasActed) {
            selected = u
            actionOrigin = u.pos
            reachable = Movement.reachable(map, u, ::unitAt)
            targets = emptySet()
            phase = Phase.MOVING
            message = "${u.type.label}: move, then choose a target."
        } else {
            clearSelection()
        }
    }

    private fun handleMoveTap(p: Pos) {
        val u = selected ?: run { clearSelection(); return }

        // Re-tapping another ready friendly unit switches the selection.
        val other = unitAt(p)
        if (other != null && other.team == Team.PLAYER && !other.hasActed && other !== u) {
            selectFriendly(p)
            return
        }

        if (p == u.pos || reachable.containsKey(p)) {
            u.pos = p // commit the move
            targets = computeTargets(u)
            if (targets.isEmpty()) {
                finishUnit(u, "${u.type.label} holds position.")
            } else {
                phase = Phase.TARGETING
                reachable = emptyMap()
                message = "${u.type.label}: tap a target, or tap again to wait."
            }
        } else {
            clearSelection()
        }
    }

    private fun handleTargetTap(p: Pos) {
        val u = selected ?: run { clearSelection(); return }
        val victim = unitAt(p)
        if (victim != null && victim.team == Team.ENEMY && p in targets) {
            resolveAttack(u, victim)
            finishUnit(u, null)
        } else {
            finishUnit(u, "${u.type.label} waits.")
        }
    }

    /** Enemy tiles the (already-moved) unit can currently strike. */
    private fun computeTargets(u: Unit): Set<Pos> {
        // Indirect units cannot move and fire in the same turn.
        if (u.type.indirect && movedThisAction(u)) return emptySet()
        return units.filter {
            it.alive && it.team != u.team && Combat.inRange(u.type, u.pos, it.pos)
        }.map { it.pos }.toSet()
    }

    // Whether the selected unit changed tiles this action. We approximate by
    // comparing to the reachable set's origin; simpler: a moved indirect unit has
    // reachable cleared. We instead track via a dedicated field.
    private var actionOrigin: Pos? = null
    private fun movedThisAction(u: Unit): Boolean = actionOrigin != null && actionOrigin != u.pos

    private fun finishUnit(u: Unit, msg: String?) {
        u.hasActed = true
        clearSelection()
        if (msg != null) message = msg
        checkVictory()
    }

    private fun clearSelection() {
        selected = null
        reachable = emptyMap()
        targets = emptySet()
        actionOrigin = null
        phase = Phase.IDLE
    }

    // ---------------------------------------------------------------
    // Combat
    // ---------------------------------------------------------------

    private fun resolveAttack(attacker: Unit, defender: Unit) {
        val dealt = Combat.damage(attacker, defender, map[defender.pos])
        defender.hp -= dealt
        defender.clampHp()

        var note = "${attacker.type.label} hits ${defender.type.label} for $dealt."
        if (defender.alive &&
            !defender.type.indirect &&
            Combat.inRange(defender.type, defender.pos, attacker.pos)
        ) {
            val back = Combat.damage(defender, attacker, map[attacker.pos])
            attacker.hp -= back
            attacker.clampHp()
            note += " Counter for $back."
        }
        removeDead()
        message = note
    }

    private fun removeDead() {
        units.removeAll { !it.alive }
    }

    private fun checkVictory() {
        val playerAlive = units.any { it.team == Team.PLAYER && it.alive }
        val enemyAlive = units.any { it.team == Team.ENEMY && it.alive }
        winner = when {
            !enemyAlive -> Team.PLAYER
            !playerAlive -> Team.ENEMY
            else -> null
        }
        if (winner != null) {
            message = "${winner!!.label} wins! Tap Restart."
        }
    }

    // ---------------------------------------------------------------
    // Turn flow
    // ---------------------------------------------------------------

    fun endPlayerTurn() {
        if (winner != null || turn != Team.PLAYER) return
        clearSelection()
        beginTurn(Team.ENEMY)
        runEnemyTurn()
        if (winner != null) return
        beginTurn(Team.PLAYER)
        message = "Blue Army — Day $day."
    }

    private fun beginTurn(team: Team) {
        turn = team
        if (team == Team.PLAYER) day++
        units.filter { it.team == team }.forEach { it.hasActed = false }
        clearSelection()
    }

    /** A greedy AI: each unit takes the best available attack, else advances. */
    private fun runEnemyTurn() {
        for (u in units.filter { it.team == Team.ENEMY }.toList()) {
            if (!u.alive || winner != null) continue
            takeEnemyAction(u)
            checkVictory()
        }
        message = "Red Army finished its turn."
    }

    private fun takeEnemyAction(u: Unit) {
        val reach = Movement.reachable(map, u, ::unitAt)
        val stand = reach.keys + u.pos

        // Best attack: over every tile it can reach, find the highest-damage strike.
        var bestTile: Pos? = null
        var bestVictim: Unit? = null
        var bestDamage = -1
        for (tile in stand) {
            if (u.type.indirect && tile != u.pos) continue // indirect can't move + fire
            for (victim in units.filter { it.alive && it.team == Team.PLAYER }) {
                if (!Combat.inRange(u.type, tile, victim.pos)) continue
                val atFull = u.pos
                u.pos = tile
                val dmg = Combat.damage(u, victim, map[victim.pos])
                u.pos = atFull
                // Prefer outright kills, then raw damage.
                val score = dmg + if (dmg >= victim.hp) 100 else 0
                if (score > bestDamage) {
                    bestDamage = score; bestTile = tile; bestVictim = victim
                }
            }
        }

        if (bestTile != null && bestVictim != null) {
            u.pos = bestTile
            resolveAttack(u, bestVictim)
            u.hasActed = true
            return
        }

        // No attack — advance toward the nearest player unit.
        val nearest = units.filter { it.alive && it.team == Team.PLAYER }
            .minByOrNull { it.pos.manhattan(u.pos) }
        if (nearest != null) {
            val step = reach.keys.minByOrNull { it.manhattan(nearest.pos) }
            if (step != null && step.manhattan(nearest.pos) < u.pos.manhattan(nearest.pos)) {
                u.pos = step
            }
        }
        u.hasActed = true
    }

    // ---------------------------------------------------------------

    fun restart() {
        val fresh = Scenarios.twinRidges()
        units.clear()
        units.addAll(fresh.units)
        turn = Team.PLAYER
        day = 1
        winner = null
        clearSelection()
        message = "Blue Army — Day 1. Tap a unit to command it."
    }
}

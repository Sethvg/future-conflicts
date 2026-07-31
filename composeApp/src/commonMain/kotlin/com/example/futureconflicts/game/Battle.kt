package com.example.futureconflicts.game

/**
 * The whole game state and its rules. Pure logic — no rendering, no platform, no
 * Compose — so it can be unit-tested on the host.
 *
 * ## Two layers
 * 1. **Authoritative simulation** — [apply] executes a serializable [Action] and is
 *    the *only* thing that mutates game state. The UI, the AI, and (later) the
 *    network all funnel through it, which keeps the sim deterministic and portable
 *    to multiplayer.
 * 2. **Interactive helper** — [onTap] plus the button verbs ([waitHere],
 *    [captureHere], [buildUnit], …) translate touch input into [Action]s. This is a
 *    convenience over [apply]; it also maintains selection/preview state the
 *    renderer reads. During the ACTION phase the selected unit has *not* moved yet;
 *    [previewPos] is where it would go, and [apply] performs the move atomically.
 */
class Battle(scenario: Scenario = Scenarios.twinRidges()) {

    val map: GameMap = scenario.map
    val units: MutableList<Unit> = scenario.units.toMutableList()
    private val buildings: MutableMap<Pos, Building> =
        scenario.buildings.associateByTo(LinkedHashMap()) { it.pos }
    private val players: Map<Team, PlayerState> =
        mapOf(Team.PLAYER to PlayerState(), Team.ENEMY to PlayerState())

    var turn: Team = Team.PLAYER
        private set
    var day: Int = 1
        private set
    var winner: Team? = null
        private set
    var message: String = "Blue Army — Day 1. Tap a unit, or your HQ to build."
        private set

    // ---- Interactive selection/preview state (read by the renderer) ----
    enum class Phase { IDLE, MOVING, ACTION }

    var phase: Phase = Phase.IDLE
        private set
    var selected: Unit? = null
        private set
    var previewPos: Pos? = null
        private set
    var reachable: Map<Pos, Int> = emptyMap()
        private set
    var targets: Set<Pos> = emptySet()
        private set
    var canCaptureHere: Boolean = false
        private set
    var buildMenuAt: Pos? = null
        private set
    var upgradeAt: Pos? = null
        private set

    // ---- Read accessors for the UI ----
    fun unitAt(p: Pos): Unit? = units.firstOrNull { it.alive && it.pos == p }
    fun buildingAt(p: Pos): Building? = buildings[p]
    val buildingsView: Collection<Building> get() = buildings.values
    fun goldOf(team: Team): Int = players.getValue(team).gold
    val buildMenuOpen: Boolean get() = buildMenuAt != null
    val upgradeOpen: Boolean get() = upgradeAt != null

    /** The city targeted by the upgrade prompt, if it can still be upgraded & afforded. */
    fun upgradeableCity(): Building? {
        val b = upgradeAt?.let { buildings[it] } ?: return null
        if (b.kind != Building.Kind.CITY || b.owner != Team.PLAYER) return null
        if (b.level >= Economy.CITY_MAX_LEVEL) return null
        return b
    }

    // ===============================================================
    // Authoritative simulation — the single mutation funnel
    // ===============================================================

    fun apply(action: Action): Boolean {
        if (winner != null) return false
        return when (action) {
            is Action.Wait -> {
                val u = execMove(action.from, action.to) ?: return false
                finishUnit(u, "${u.type.label} holds position.")
                true
            }
            is Action.Attack -> execAttack(action)
            is Action.Capture -> execCapture(action)
            is Action.Build -> execBuild(action)
            is Action.Upgrade -> execUpgrade(action)
            Action.EndTurn -> if (turn == Team.PLAYER) { advanceTurn(); true } else false
        }
    }

    private fun canAct(u: Unit): Boolean = u.team == turn && !u.hasActed

    private fun reachableFor(u: Unit): Map<Pos, Int> = Movement.reachable(map, u, ::unitAt)

    /** Validate + perform the movement part of an action. Returns the unit, or null. */
    private fun execMove(from: Pos, to: Pos): Unit? {
        val u = unitAt(from) ?: return null
        if (!canAct(u)) return null
        if (to != from && !reachableFor(u).containsKey(to)) return null
        u.pos = to
        return u
    }

    private fun execAttack(a: Action.Attack): Boolean {
        val u = unitAt(a.from) ?: return false
        if (!canAct(u)) return false
        if (u.type.indirect && a.to != a.from) return false           // can't move and fire
        if (a.to != a.from && !reachableFor(u).containsKey(a.to)) return false
        val victim = unitAt(a.target) ?: return false
        if (victim.team == u.team) return false
        if (!Combat.inRange(u.type, a.to, a.target)) return false
        u.pos = a.to
        resolveAttack(u, victim)
        finishUnit(u, null)
        return true
    }

    private fun execCapture(a: Action.Capture): Boolean {
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

    private fun execBuild(a: Action.Build): Boolean {
        val hq = buildings[a.at] ?: return false
        if (hq.kind != Building.Kind.HQ || hq.owner != turn) return false
        val ps = players.getValue(turn)
        if (ps.gold < a.type.cost) return false
        val spawn = spawnTileFor(a.at) ?: return false
        ps.gold -= a.type.cost
        units.add(Unit(a.type, turn, spawn).also { it.hasActed = true })
        message = "${turn.label} built ${a.type.label} (−${a.type.cost}g)."
        return true
    }

    private fun execUpgrade(a: Action.Upgrade): Boolean {
        val b = buildings[a.at] ?: return false
        if (b.kind != Building.Kind.CITY || b.owner != turn) return false
        if (b.level >= Economy.CITY_MAX_LEVEL) return false
        val ps = players.getValue(turn)
        if (ps.gold < Economy.CITY_UPGRADE_COST) return false
        ps.gold -= Economy.CITY_UPGRADE_COST
        b.level++
        message = "City upgraded to L${b.level} (+${Economy.CITY_INCOME_PER_LEVEL}/turn)."
        return true
    }

    /** Free, passable tile to place a newly built unit: the HQ tile, else a neighbor. */
    private fun spawnTileFor(hq: Pos): Pos? {
        if (unitAt(hq) == null && map[hq].passable) return hq
        return map.neighbors(hq).firstOrNull { map[it].passable && unitAt(it) == null }
    }

    // ===============================================================
    // Combat
    // ===============================================================

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

    private fun finishUnit(u: Unit, msg: String?) {
        u.hasActed = true
        clearSelection()
        if (msg != null) message = msg
        checkVictory()
    }

    private fun checkVictory() {
        if (winner != null) return // a captured HQ already decided it
        val playerAlive = units.any { it.team == Team.PLAYER }
        val enemyAlive = units.any { it.team == Team.ENEMY }
        winner = when {
            !enemyAlive -> Team.PLAYER
            !playerAlive -> Team.ENEMY
            else -> null
        }
        if (winner != null) message = "${winner!!.label} wins! Tap Restart."
    }

    // ===============================================================
    // Interactive helper (translates taps into Actions)
    // ===============================================================

    fun onTap(p: Pos) {
        if (winner != null || turn != Team.PLAYER) return
        if (buildMenuOpen || upgradeOpen) { dismissMenus(); return }
        if (!map.inBounds(p)) { clearSelection(); return }
        when (phase) {
            Phase.IDLE -> onTapIdle(p)
            Phase.MOVING -> onTapMoving(p)
            Phase.ACTION -> onTapAction(p)
        }
    }

    private fun onTapIdle(p: Pos) {
        val u = unitAt(p)
        if (u != null && u.team == Team.PLAYER && !u.hasActed) { select(u); return }
        val b = buildings[p]
        if (b != null && b.owner == Team.PLAYER && u == null) {
            when (b.kind) {
                Building.Kind.HQ -> { buildMenuAt = p; message = "Build a unit at HQ." }
                Building.Kind.CITY -> {
                    upgradeAt = p
                    message = if (b.level >= Economy.CITY_MAX_LEVEL) "City is fully upgraded."
                    else "Upgrade city to L${b.level + 1}? (${Economy.CITY_UPGRADE_COST}g)"
                }
            }
            return
        }
        clearSelection()
    }

    private fun onTapMoving(p: Pos) {
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

    private fun onTapAction(p: Pos) {
        val u = selected ?: run { clearSelection(); return }
        val dest = previewPos ?: u.pos
        when {
            p in targets -> apply(Action.Attack(u.pos, dest, p))
            p == dest -> if (canCaptureHere) apply(Action.Capture(u.pos, dest))
                         else apply(Action.Wait(u.pos, dest))
            else -> { // tap elsewhere cancels back to move selection
                phase = Phase.MOVING
                previewPos = u.pos
                targets = emptySet()
                canCaptureHere = false
                reachable = reachableFor(u)
            }
        }
    }

    private fun select(u: Unit) {
        dismissMenus()
        selected = u
        previewPos = u.pos
        reachable = reachableFor(u)
        targets = emptySet()
        canCaptureHere = false
        phase = Phase.MOVING
        message = "${u.type.label}: tap where to move."
    }

    private fun enterActionPhase(u: Unit, dest: Pos) {
        previewPos = dest
        reachable = emptyMap()
        targets = if (u.type.indirect && dest != u.pos) emptySet()
        else units.filter { it.alive && it.team != u.team && Combat.inRange(u.type, dest, it.pos) }
            .map { it.pos }.toSet()
        canCaptureHere = u.type.canCapture && buildings[dest]?.let { it.owner != u.team } == true
        phase = Phase.ACTION
        message = "Choose an action."
    }

    // Button verbs the UI calls during the ACTION phase / menus.
    fun waitHere() { val u = selected ?: return; apply(Action.Wait(u.pos, previewPos ?: u.pos)) }
    fun captureHere() { val u = selected ?: return; apply(Action.Capture(u.pos, previewPos ?: u.pos)) }
    fun cancelAction() {
        val u = selected ?: return clearSelection()
        phase = Phase.MOVING
        previewPos = u.pos
        targets = emptySet()
        canCaptureHere = false
        reachable = reachableFor(u)
    }

    fun buildUnit(type: UnitType) {
        val at = buildMenuAt ?: return
        apply(Action.Build(type, at))
        dismissMenus()
    }

    fun upgradeCity() {
        val at = upgradeAt ?: return
        apply(Action.Upgrade(at))
        dismissMenus()
    }

    fun dismissMenus() { buildMenuAt = null; upgradeAt = null }

    private fun clearSelection() {
        selected = null
        previewPos = null
        reachable = emptyMap()
        targets = emptySet()
        canCaptureHere = false
        phase = Phase.IDLE
    }

    // ===============================================================
    // Turn flow
    // ===============================================================

    fun endPlayerTurn() { apply(Action.EndTurn) }

    private fun advanceTurn() {
        clearSelection()
        dismissMenus()
        beginTurn(Team.ENEMY)
        runEnemyTurn()
        if (winner != null) return
        beginTurn(Team.PLAYER)
        message = "Blue Army — Day $day."
    }

    private fun beginTurn(team: Team) {
        turn = team
        if (team == Team.PLAYER) day++
        players.getValue(team).gold +=
            buildings.values.filter { it.owner == team }.sumOf { it.incomePerTurn }
        units.filter { it.team == team }.forEach { it.hasActed = false }
        clearSelection()
    }

    /** Greedy AI: best attack, else capture a reachable building, else advance. */
    private fun runEnemyTurn() {
        for (u in units.filter { it.team == Team.ENEMY }.toList()) {
            if (!u.alive || winner != null) continue
            planEnemy(u)?.let { apply(it) }
        }
        if (winner == null) message = "Red Army finished its turn."
    }

    private fun planEnemy(u: Unit): Action? {
        val reach = reachableFor(u)
        val stand = reach.keys + u.pos

        // 1. Best attack across every reachable firing position.
        var bestTile: Pos? = null
        var bestTarget: Pos? = null
        var bestScore = -1
        for (tile in stand) {
            if (u.type.indirect && tile != u.pos) continue
            for (victim in units.filter { it.alive && it.team == Team.PLAYER }) {
                if (!Combat.inRange(u.type, tile, victim.pos)) continue
                val saved = u.pos
                u.pos = tile
                val dmg = Combat.damage(u, victim, map[victim.pos])
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

    // ===============================================================

    fun restart() {
        val fresh = Scenarios.twinRidges()
        units.clear(); units.addAll(fresh.units)
        buildings.clear(); fresh.buildings.forEach { buildings[it.pos] = it }
        players.getValue(Team.PLAYER).let { it.gold = Economy.STARTING_GOLD; it.commanderLosses = 0 }
        players.getValue(Team.ENEMY).let { it.gold = Economy.STARTING_GOLD; it.commanderLosses = 0 }
        turn = Team.PLAYER
        day = 1
        winner = null
        clearSelection(); dismissMenus()
        message = "Blue Army — Day 1. Tap a unit, or your HQ to build."
    }
}

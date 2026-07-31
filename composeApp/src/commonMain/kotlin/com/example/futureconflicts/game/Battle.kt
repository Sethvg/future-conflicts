package com.example.futureconflicts.game

import kotlin.random.Random

/** One buildable option offered by an open production building: a unit type plus
 *  whether it's the Elite (signature) variant. */
data class BuildOption(val type: UnitType, val elite: Boolean)

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
class Battle(
    scenario: Scenario = Scenarios.twinRidges(),
    playerCommander: Commander? = null,
    enemyCommander: Commander? = null,
    /** Seed for the supply-drop RNG. Fixed by default so games are reproducible. */
    private val seed: Long = DEFAULT_SEED,
) {

    val map: GameMap = scenario.map
    val units: MutableList<Unit> = scenario.units.toMutableList()
    private val buildings: MutableMap<Pos, Building> =
        scenario.buildings.associateByTo(LinkedHashMap()) { it.pos }
    private val players: Map<Team, PlayerState> =
        mapOf(Team.PLAYER to PlayerState(), Team.ENEMY to PlayerState())
    private val commanders: MutableMap<Team, Commander?> =
        mutableMapOf(Team.PLAYER to playerCommander, Team.ENEMY to enemyCommander)

    // ---- Supply drops (seeded, deterministic) ----
    private var rng: Random = Random(seed)

    /** Turns each side has *begun*. The opening player turn isn't run through
     *  [beginTurn], so PLAYER starts pre-counted at 1 to keep both sides' cadence aligned. */
    private val turnsTaken: MutableMap<Team, Int> =
        mutableMapOf(Team.PLAYER to 1, Team.ENEMY to 0)

    /** While set, that side's REVEAL boon is active and it sees the whole map. */
    private var supplyRevealFor: Team? = null

    /** The most recent supply drop, for the HUD and tests (null until the first drop). */
    var lastSupplyKind: SupplyKind? = null
        private set
    var lastSupplyTeam: Team? = null
        private set

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

    /** Fog of war toggle. When off, everything is visible (useful for debugging). */
    var fogEnabled: Boolean = true

    /** Tiles [team] can currently see (all tiles when fog is off or a REVEAL drop is active). */
    fun visibleTiles(team: Team): Set<Pos> =
        if (!fogEnabled || supplyRevealFor == team) allTiles
        else Vision.visibleTiles(map, team, units, buildings.values)

    /** Whether [viewer] can see [unit] (pass the precomputed [visible] set to avoid rework). */
    fun isUnitVisible(viewer: Team, unit: Unit, visible: Set<Pos> = visibleTiles(viewer)): Boolean =
        !fogEnabled || Vision.isUnitVisible(map, viewer, unit, visible, units)

    private val allTiles: Set<Pos> by lazy {
        buildSet { for (y in 0 until map.rows) for (x in 0 until map.cols) add(Pos(x, y)) }
    }
    fun goldOf(team: Team): Int = players.getValue(team).gold
    val buildMenuOpen: Boolean get() = buildMenuAt != null
    val upgradeOpen: Boolean get() = upgradeAt != null

    // ---- Commanders & the stat pipeline ----
    fun commanderOf(team: Team): Commander? = commanders[team]
    val needsCommanderChoice: Boolean get() = commanders[Team.PLAYER] == null

    /** Choose the player's Commander; the enemy takes a different one automatically. */
    fun chooseCommander(id: String) {
        commanders[Team.PLAYER] = Commanders.byId(id)
        commanders[Team.ENEMY] = Commanders.all.firstOrNull { it.id != id } ?: Commanders.all.first()
        message = "Blue Army — Day 1. Tap a unit, or your HQ to build."
    }

    private fun passive(team: Team, kind: PassiveKind): Int = commanders[team]?.amount(kind) ?: 0

    fun effectiveMove(u: Unit): Int {
        var m = u.type.maxMove + passive(u.team, PassiveKind.MOVE)
        if (u.elite) m += Elite.MOVE_BONUS
        return m.coerceAtLeast(1)
    }

    /** Range passives only extend indirect units, so direct units stay melee. */
    fun effectiveMaxRange(u: Unit): Int =
        if (u.type.indirect) u.type.maxRange + passive(u.team, PassiveKind.RANGE) else u.type.maxRange

    private fun attackMul(u: Unit): Double {
        var pct = passive(u.team, PassiveKind.FIREPOWER)
        if (u.elite) pct += Elite.FIREPOWER_BONUS
        return 1.0 + pct / 100.0
    }

    private fun defenseMul(u: Unit): Double {
        var pct = passive(u.team, PassiveKind.ARMOR)
        if (u.elite) pct += Elite.ARMOR_BONUS
        return (1.0 - pct / 100.0).coerceAtLeast(0.1)
    }

    private fun damageOf(attacker: Unit, defender: Unit): Int =
        Combat.damage(attacker, defender, map[defender.pos], attackMul(attacker), defenseMul(defender))

    private fun inRangeEff(u: Unit, from: Pos, target: Pos): Boolean =
        Combat.inRange(from, target, u.type.minRange, effectiveMaxRange(u))

    // ---- HQ production pricing (used by the UI too) ----
    fun hasCommander(team: Team): Boolean =
        units.any { it.alive && it.team == team && it.type == UnitType.COMMANDER }

    fun commanderPrice(team: Team): Int {
        val c = commanders[team] ?: return UnitType.COMMANDER.cost
        return players.getValue(team).commanderCost(UnitType.COMMANDER.cost, c.rebuyMultiplier)
    }

    /** Gold cost to build [type] (optionally [elite]) for [team], or null if illegal. */
    fun buildCost(team: Team, type: UnitType, elite: Boolean): Int? {
        if (type == UnitType.COMMANDER) return if (elite) null else commanderPrice(team)
        if (!type.basic) return null
        if (elite && commanders[team]?.signature != type) return null
        val discounted = type.cost * (100 - passive(team, PassiveKind.DISCOUNT)) / 100
        return if (elite) (discounted * Elite.COST_MULTIPLIER).toInt() else discounted
    }

    /** The city targeted by the upgrade prompt, if it can still be upgraded & afforded. */
    fun upgradeableCity(): Building? {
        val b = upgradeAt?.let { buildings[it] } ?: return null
        if (b.kind != Building.Kind.CITY || b.owner != Team.PLAYER) return null
        if (b.level >= Economy.CITY_MAX_LEVEL) return null
        return b
    }

    /** Units the currently open production building ([buildMenuAt]) can build this turn. */
    fun buildableHere(): List<BuildOption> {
        val at = buildMenuAt ?: return emptyList()
        val b = buildings[at] ?: return emptyList()
        val team = turn
        val cat = b.kind.builds
        val out = mutableListOf<BuildOption>()
        if (cat != null) {
            for (t in UnitType.entries) if (t.basic && t.category == cat) out += BuildOption(t, elite = false)
            commanders[team]?.signature?.let { sig ->
                if (sig.basic && sig.category == cat) out += BuildOption(sig, elite = true)
            }
        }
        if (b.kind == Building.Kind.HQ && !hasCommander(team)) out += BuildOption(UnitType.COMMANDER, elite = false)
        return out
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
            is Action.SupplyDrop -> execSupplyDrop(action)
            Action.EndTurn -> if (turn == Team.PLAYER) { advanceTurn(); true } else false
        }
    }

    private fun canAct(u: Unit): Boolean = u.team == turn && !u.hasActed

    private fun reachableFor(u: Unit): Map<Pos, Int> =
        Movement.reachable(map, u, ::unitAt, effectiveMove(u))

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
        if (!inRangeEff(u, a.to, a.target)) return false
        if (!Combat.canTarget(u.type, victim.type)) return false // e.g. ground units can't hit air
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
        val spawn = spawnTileFor(a.at) ?: return false
        ps.gold -= cost
        units.add(Unit(a.type, turn, spawn, elite = a.elite).also { it.hasActed = true })
        val name = if (a.elite) "Elite ${a.type.label}" else a.type.label
        message = "${turn.label} built $name (−${cost}g)."
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

    /** Apply a supply-drop boon to [a.team]. Effects are fixed by [a.kind] (no RNG here). */
    private fun execSupplyDrop(a: Action.SupplyDrop): Boolean {
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
                val spawn = hq?.let { spawnTileFor(it.pos) }
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

    /** Free, passable tile to place a newly built unit: the HQ tile, else a neighbor. */
    private fun spawnTileFor(hq: Pos): Pos? {
        if (unitAt(hq) == null && map[hq].passable) return hq
        return map.neighbors(hq).firstOrNull { map[it].passable && unitAt(it) == null }
    }

    // ===============================================================
    // Combat
    // ===============================================================

    private fun resolveAttack(attacker: Unit, defender: Unit) {
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

    private fun removeDead() {
        // A destroyed Commander compounds that side's future rebuy cost.
        for (u in units) if (!u.alive && u.type == UnitType.COMMANDER) {
            players.getValue(u.team).commanderLosses++
        }
        units.removeAll { !it.alive }
    }

    /** Air units refuel on an owned Airport/HQ at turn start; otherwise they burn fuel and,
     *  if it runs dry, crash (are lost). */
    private fun refuelOrCrash(team: Team) {
        val crashed = ArrayList<Unit>()
        for (u in units) {
            if (u.team != team || !u.type.fuelLimited) continue
            val b = buildings[u.pos]
            val onBase = b != null && b.owner == team &&
                (b.kind == Building.Kind.AIRPORT || b.kind == Building.Kind.HQ)
            if (onBase) {
                u.fuel = u.type.maxFuel
            } else {
                u.fuel -= FUEL_BURN_PER_TURN
                if (u.fuel <= 0) crashed += u
            }
        }
        if (crashed.isNotEmpty()) {
            units.removeAll(crashed)
            message = "${team.label} lost ${crashed.size} aircraft to fuel exhaustion."
            checkVictory() // a fuel crash can eliminate a side's last unit
        }
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
            when {
                b.kind == Building.Kind.CITY -> {
                    upgradeAt = p
                    message = if (b.level >= Economy.CITY_MAX_LEVEL) "City is fully upgraded."
                    else "Upgrade city to L${b.level + 1}? (${Economy.CITY_UPGRADE_COST}g)"
                }
                b.kind.builds != null -> { buildMenuAt = p; message = "Build a unit here." }
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
        else units.filter {
            it.alive && it.team != u.team && inRangeEff(u, dest, it.pos) &&
                Combat.canTarget(u.type, it.type)
        }.map { it.pos }.toSet()
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

    fun buildUnit(type: UnitType, elite: Boolean = false) {
        val at = buildMenuAt ?: return
        apply(Action.Build(type, at, elite))
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
    }

    private fun beginTurn(team: Team) {
        turn = team
        if (team == Team.PLAYER) { day++; message = "Blue Army — Day $day." }
        if (supplyRevealFor == team) supplyRevealFor = null // a prior recon sweep expires
        turnsTaken[team] = turnsTaken.getValue(team) + 1
        val base = buildings.values.filter { it.owner == team }.sumOf { it.incomePerTurn }
        players.getValue(team).gold += base + base * passive(team, PassiveKind.INCOME) / 100
        units.filter { it.team == team }.forEach { it.hasActed = false }
        refuelOrCrash(team)
        clearSelection()
        // Every INTERVAL turns a side takes, it receives a seeded supply drop.
        if (turnsTaken.getValue(team) % Supply.INTERVAL == 0) {
            apply(Action.SupplyDrop(team, Supply.roll(rng)))
        }
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
        rng = Random(seed)
        turnsTaken[Team.PLAYER] = 1
        turnsTaken[Team.ENEMY] = 0
        supplyRevealFor = null
        lastSupplyKind = null
        lastSupplyTeam = null
        clearSelection(); dismissMenus()
        message = "Blue Army — Day 1. Tap a unit, or your HQ to build."
    }

    companion object {
        /** Default supply-drop RNG seed; a fixed value keeps the shipped game reproducible. */
        const val DEFAULT_SEED = 0x5EED_1234L

        /** Fuel an airborne unit burns each of its turns when not refuelling at base. */
        const val FUEL_BURN_PER_TURN = 2
    }
}

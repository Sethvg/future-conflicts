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
 *    [captureHere], [buildUnit], …) translate touch input into [Action]s.
 *
 * ## File layout (kept modular for maintenance, parallel work, and testing)
 * This file holds the **state + public API + the [apply] funnel**. The implementation
 * lives in cohesive sibling files as `internal` extensions on `Battle` (state is
 * `internal` so they can reach it): `BattleStats` (derived stats & reach),
 * `BattleActions` (action executors, combat resolution, victory), `BattleTurn`
 * (turn lifecycle & fuel), `BattleAI` (enemy planner), `BattleInteraction` (tap
 * handling). Behaviour is identical to the old monolith.
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
    internal val buildings: MutableMap<Pos, Building> =
        scenario.buildings.associateByTo(LinkedHashMap()) { it.pos }
    internal val players: Map<Team, PlayerState> =
        mapOf(Team.PLAYER to PlayerState(), Team.ENEMY to PlayerState())
    internal val commanders: MutableMap<Team, Commander?> =
        mutableMapOf(Team.PLAYER to playerCommander, Team.ENEMY to enemyCommander)

    // ---- Supply drops (seeded, deterministic) ----
    internal var rng: Random = Random(seed)

    /** Turns each side has *begun*. The opening player turn isn't run through
     *  `beginTurn`, so PLAYER starts pre-counted at 1 to keep both sides' cadence aligned. */
    internal val turnsTaken: MutableMap<Team, Int> =
        mutableMapOf(Team.PLAYER to 1, Team.ENEMY to 0)

    /** While set, that side's REVEAL boon is active and it sees the whole map. */
    internal var supplyRevealFor: Team? = null

    /** Per-team drone slots waiting out [Drones.REBUILD_COOLDOWN] after a loss
     *  (one entry per empty slot; see BattleDrones.kt). */
    internal val droneCooldowns: MutableMap<Team, MutableList<Int>> = mutableMapOf()

    /** The most recent supply drop, for the HUD and tests (null until the first drop). */
    var lastSupplyKind: SupplyKind? = null
        internal set
    var lastSupplyTeam: Team? = null
        internal set

    var turn: Team = Team.PLAYER
        internal set
    var day: Int = 1
        internal set
    var winner: Team? = null
        internal set
    var message: String = "Blue Army — Day 1. Tap a unit, or your HQ to build."
        internal set

    // ---- Interactive selection/preview state (read by the renderer) ----
    enum class Phase { IDLE, MOVING, ACTION }

    var phase: Phase = Phase.IDLE
        internal set
    var selected: Unit? = null
        internal set
    var previewPos: Pos? = null
        internal set
    var reachable: Map<Pos, Int> = emptyMap()
        internal set
    var targets: Set<Pos> = emptySet()
        internal set
    var canCaptureHere: Boolean = false
        internal set
    var buildMenuAt: Pos? = null
        internal set
    var upgradeAt: Pos? = null
        internal set

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

    internal val allTiles: Set<Pos> by lazy {
        buildSet { for (y in 0 until map.rows) for (x in 0 until map.cols) add(Pos(x, y)) }
    }
    fun goldOf(team: Team): Int = players.getValue(team).gold
    val buildMenuOpen: Boolean get() = buildMenuAt != null
    val upgradeOpen: Boolean get() = upgradeAt != null

    // ---- Commanders & the stat pipeline (impl in BattleStats.kt) ----
    fun commanderOf(team: Team): Commander? = commanders[team]
    val needsCommanderChoice: Boolean get() = commanders[Team.PLAYER] == null

    /** Choose the player's Commander; the enemy takes a different one automatically. */
    fun chooseCommander(id: String) {
        commanders[Team.PLAYER] = Commanders.byId(id)
        commanders[Team.ENEMY] = Commanders.all.firstOrNull { it.id != id } ?: Commanders.all.first()
        message = "Blue Army — Day 1. Tap a unit, or your HQ to build."
    }

    // effectiveMove / effectiveMaxRange live in BattleStats.kt with the rest of the pipeline.

    // ---- Production pricing / queries (used by the UI; executors in BattleActions.kt) ----
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

    /** The building targeted by the upgrade prompt, if it can still be upgraded. */
    fun upgradeableCity(): Building? {
        val b = upgradeAt?.let { buildings[it] } ?: return null
        if (b.owner != Team.PLAYER) return null
        return when (b.kind) {
            Building.Kind.CITY -> if (b.level < Economy.CITY_MAX_LEVEL) b else null
            Building.Kind.DRONE_COMMAND -> if (b.level < Drones.MAX_LEVEL) b else null
            else -> null
        }
    }

    /** Gold to upgrade the building in the upgrade prompt (city vs drone command differ). */
    fun upgradeCost(): Int =
        if (upgradeAt?.let { buildings[it] }?.kind == Building.Kind.DRONE_COMMAND)
            Drones.UPGRADE_COST else Economy.CITY_UPGRADE_COST

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

    // ---- Button verbs the UI calls during the ACTION phase / menus ----
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

    fun waitHere() { val u = selected ?: return; apply(Action.Wait(u.pos, previewPos ?: u.pos)) }
    fun captureHere() { val u = selected ?: return; apply(Action.Capture(u.pos, previewPos ?: u.pos)) }
    fun cancelAction() {
        val u = selected ?: return clearSelection()
        resetToMoving(u)
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

    fun endPlayerTurn() { apply(Action.EndTurn) }

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
        droneCooldowns.clear()
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

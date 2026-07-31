package com.example.futureconflicts.game

/**
 * The **behavioural** side of a unit, as an object hierarchy.
 *
 * `UnitType` stays a flat enum because it is the unit's *identity* — serializable,
 * exhaustive in `when`, usable as a map key in the damage table. What varies per unit
 * is its **domain behaviour**, and that lives here as polymorphic [UnitClass]
 * subclasses instead of a widening pile of boolean flags:
 *
 * ```
 * UnitClass                  movement, targeting, capture, fuel, refuel bases
 *  ├── GroundClass           terrain-bound; FootClass adds capture
 *  │    └── FootClass
 *  ├── AirClass              ignores terrain, flies over units, carries fuel
 *  └── NavalClass            sea-only (Slice 4)
 * ```
 *
 * Adding a unit = picking (or adding) a class + its stats — no `if (type == …)`
 * branching in the rules. Each unit exposes its class via [UnitType.unitClass].
 */
sealed class UnitClass {
    /** How the unit traverses the board. */
    abstract val moveClass: MoveClass

    /** Can it capture buildings? (Foot units only.) */
    open val canCapture: Boolean get() = false

    /** Can it attack air units? Explicit, so ground units never accidentally gain it. */
    open val hitsAir: Boolean get() = false

    /** Fuel capacity; 0 = not fuel-limited. */
    open val maxFuel: Int get() = 0

    /** Ignores terrain move cost and passes over other units. */
    open val ignoresTerrain: Boolean get() = false

    /** Tiles this unit may enter, given the terrain. */
    open fun canEnter(terrain: Terrain): Boolean = terrain.passable

    /** Buildings that refuel/repair this unit when it starts a turn on one it owns. */
    open fun refuelsAt(kind: Building.Kind): Boolean = false

    val fuelLimited: Boolean get() = maxFuel > 0
}

/** Tracked/wheeled units: bound by terrain cost, blocked by enemies. */
open class GroundClass(
    override val hitsAir: Boolean = false,
) : UnitClass() {
    override val moveClass: MoveClass get() = MoveClass.GROUND
}

/** Infantry on foot — the only units that capture buildings. */
class FootClass : GroundClass() {
    override val canCapture: Boolean get() = true
}

/** Aircraft: ignore terrain, fly over everything, burn fuel, refuel at an Airport/HQ. */
class AirClass(
    override val maxFuel: Int,
    override val hitsAir: Boolean = true,
) : UnitClass() {
    override val moveClass: MoveClass get() = MoveClass.AIR
    override val ignoresTerrain: Boolean get() = true
    override fun canEnter(terrain: Terrain): Boolean = true // flies over sea and mountains alike
    override fun refuelsAt(kind: Building.Kind): Boolean =
        kind == Building.Kind.AIRPORT || kind == Building.Kind.HQ
}

/** Ships: confined to water, serviced by a Port. (Used from Slice 4.) */
class NavalClass(
    override val hitsAir: Boolean = false,
    override val maxFuel: Int = 0,
) : UnitClass() {
    override val moveClass: MoveClass get() = MoveClass.NAVAL
    override fun canEnter(terrain: Terrain): Boolean = terrain == Terrain.SEA
    override fun refuelsAt(kind: Building.Kind): Boolean = kind == Building.Kind.PORT
}

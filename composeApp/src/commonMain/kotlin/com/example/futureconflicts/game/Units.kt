package com.example.futureconflicts.game

/** Which production building fields a unit (see [Building.Kind.builds]). */
enum class Category { INFANTRY, VEHICLE, AIR, SHIP }

/** How a unit moves: ground (terrain-bound), air (ignores terrain), naval (sea only). */
enum class MoveClass { GROUND, AIR, NAVAL }

/**
 * The unit roster. Each entry is a unit's **identity + tuning numbers**; its
 * **behaviour** comes from a [UnitClass] object ([unitClass]) — see that file for the
 * hierarchy. Stats are tuned to feel Advance-Wars-like: cheap infantry that capture and
 * screen, fast recon, hard-hitting tanks, indirect artillery that out-ranges everything
 * but cannot move and fire, and aircraft that only anti-air can touch.
 *
 * @param maxMove movement points per turn.
 * @param minRange / [maxRange] attack range in tiles (Manhattan). A [maxRange] > 1
 *   marks an *indirect* unit.
 * @param cost production cost in gold.
 */
enum class UnitType(
    val label: String,
    val glyph: String,
    val maxMove: Int,
    val minRange: Int,
    val maxRange: Int,
    val cost: Int,
    val vision: Int,
    val category: Category,
    /** The behaviour object: movement, capture, targeting, fuel. */
    val unitClass: UnitClass,
) {
    INFANTRY("Infantry", "I", maxMove = 3, minRange = 1, maxRange = 1, cost = 1000, vision = 2, category = Category.INFANTRY, unitClass = FootClass()),
    MECH("Mech", "M", maxMove = 2, minRange = 1, maxRange = 1, cost = 3000, vision = 2, category = Category.INFANTRY, unitClass = FootClass()),
    RECON("Recon", "R", maxMove = 8, minRange = 1, maxRange = 1, cost = 4000, vision = 5, category = Category.VEHICLE, unitClass = GroundClass()),
    TANK("Tank", "T", maxMove = 6, minRange = 1, maxRange = 1, cost = 7000, vision = 3, category = Category.VEHICLE, unitClass = GroundClass()),
    ARTILLERY("Artillery", "A", maxMove = 5, minRange = 2, maxRange = 3, cost = 6000, vision = 2, category = Category.VEHICLE, unitClass = GroundClass()),

    /** Anti-air vehicle: shreds aircraft and infantry, weak against armor. Built at the Factory. */
    ANTI_AIR("Anti-Air", "K", maxMove = 6, minRange = 1, maxRange = 1, cost = 8000, vision = 3, category = Category.VEHICLE, unitClass = GroundClass(hitsAir = true)),

    /** Attack helicopter: flies (ignores terrain, crosses water), strong vs ground,
     *  only anti-air (and other aircraft) can hit it. Built at the Airport; carries fuel. */
    GUNSHIP("Gunship", "G", maxMove = 7, minRange = 1, maxRange = 1, cost = 9000, vision = 3, category = Category.AIR, unitClass = AirClass(maxFuel = 20)),

    /** The Commander hero unit: strong, expensive, one per player, rebuy escalates. Built at the HQ. */
    COMMANDER("Commander", "★", maxMove = 6, minRange = 1, maxRange = 1, cost = 16000, vision = 4, category = Category.VEHICLE, unitClass = GroundClass());

    /** Indirect units (artillery) attack at range but cannot move and fire. */
    val indirect: Boolean get() = maxRange > 1

    /** Buildable as a normal unit (the Commander has its own HQ-only path). */
    val basic: Boolean get() = this != COMMANDER

    // ---- Behaviour, delegated to the unit's class ----
    val moveClass: MoveClass get() = unitClass.moveClass
    val canCapture: Boolean get() = unitClass.canCapture
    val hitsAir: Boolean get() = unitClass.hitsAir
    val maxFuel: Int get() = unitClass.maxFuel
    val fuelLimited: Boolean get() = unitClass.fuelLimited

    /** Flies — ignores terrain move cost and is only hit by anti-air. */
    val air: Boolean get() = moveClass == MoveClass.AIR
}

/**
 * A single unit on the board. Mutable: HP, position, fuel, and per-turn action flags
 * change during play. Behaviour questions ("can it capture?", "can it enter this
 * tile?") delegate to [UnitType.unitClass].
 *
 * HP is tracked 0..[MAX_HP] (10). Damage math and the on-screen bar both use this.
 */
class Unit(
    val type: UnitType,
    val team: Team,
    var pos: Pos,
    var hp: Int = MAX_HP,
    /** Elite (signature) variant — a stat-boosted build of a Commander's signature chassis. */
    val elite: Boolean = false,
    /** Remaining fuel (air units); starts full. Ground units leave this at 0 and ignore it. */
    var fuel: Int = type.maxFuel,
) {
    /** True once this unit has spent its action this turn (moved+acted, or waited). */
    var hasActed: Boolean = false

    val alive: Boolean get() = hp > 0

    /** The behaviour object for this unit. */
    val unitClass: UnitClass get() = type.unitClass

    /** Can this unit end/pass through a tile of [terrain]? */
    fun canEnter(terrain: Terrain): Boolean = unitClass.canEnter(terrain)

    /** Does starting a turn on an owned [kind] refuel this unit? */
    fun refuelsAt(kind: Building.Kind): Boolean = unitClass.refuelsAt(kind)

    fun clampHp() {
        if (hp > MAX_HP) hp = MAX_HP
        if (hp < 0) hp = 0
    }

    companion object {
        const val MAX_HP = 10
    }
}

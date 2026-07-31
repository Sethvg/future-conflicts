package com.example.futureconflicts.game

/**
 * The unit roster. Stats are tuned to feel Advance-Wars-like: cheap infantry that
 * capture and screen, fast recon, hard-hitting tanks, and indirect artillery that
 * out-ranges everything but cannot move and fire in the same turn.
 *
 * @param maxMove movement points per turn.
 * @param minRange / [maxRange] attack range in tiles (Manhattan). A [maxRange] > 1
 *   marks an *indirect* unit.
 * @param cost production cost (funds), used once base-building lands.
 */
/** Which production building fields a unit (see [Building.Kind.builds]). */
enum class Category { INFANTRY, VEHICLE, AIR, SHIP }

/** How a unit moves: ground (terrain-bound), air (ignores terrain, flies over units,
 *  carries fuel), or naval (sea only). */
enum class MoveClass { GROUND, AIR, NAVAL }

enum class UnitType(
    val label: String,
    val glyph: String,
    val maxMove: Int,
    val minRange: Int,
    val maxRange: Int,
    val cost: Int,
    val vision: Int,
    val category: Category,
    val canCapture: Boolean = false,
    val moveClass: MoveClass = MoveClass.GROUND,
    val maxFuel: Int = 0,
    /** Can attack air units. Gates targeting explicitly, so ground units never
     *  accidentally become able to shoot aircraft as the roster grows. */
    val hitsAir: Boolean = false,
) {
    INFANTRY("Infantry", "I", maxMove = 3, minRange = 1, maxRange = 1, cost = 1000, vision = 2, category = Category.INFANTRY, canCapture = true),
    MECH("Mech", "M", maxMove = 2, minRange = 1, maxRange = 1, cost = 3000, vision = 2, category = Category.INFANTRY, canCapture = true),
    RECON("Recon", "R", maxMove = 8, minRange = 1, maxRange = 1, cost = 4000, vision = 5, category = Category.VEHICLE),
    TANK("Tank", "T", maxMove = 6, minRange = 1, maxRange = 1, cost = 7000, vision = 3, category = Category.VEHICLE),
    ARTILLERY("Artillery", "A", maxMove = 5, minRange = 2, maxRange = 3, cost = 6000, vision = 2, category = Category.VEHICLE),

    /** Anti-air vehicle: shreds aircraft and infantry, weak against armor. Built at the Factory. */
    ANTI_AIR("Anti-Air", "K", maxMove = 6, minRange = 1, maxRange = 1, cost = 8000, vision = 3, category = Category.VEHICLE, hitsAir = true),

    /** Attack helicopter: flies (ignores terrain, crosses water, over units), strong vs ground,
     *  only hit by anti-air. Built at the Airport; carries fuel. */
    GUNSHIP("Gunship", "G", maxMove = 7, minRange = 1, maxRange = 1, cost = 9000, vision = 3, category = Category.AIR, moveClass = MoveClass.AIR, maxFuel = 20, hitsAir = true),

    /** The Commander hero unit: strong, expensive, one per player, rebuy escalates. Built at the HQ. */
    COMMANDER("Commander", "★", maxMove = 6, minRange = 1, maxRange = 1, cost = 16000, vision = 4, category = Category.VEHICLE);

    /** Indirect units (artillery) attack at range but cannot move and fire. */
    val indirect: Boolean get() = maxRange > 1

    /** Buildable as a normal unit (the Commander has its own HQ-only path). */
    val basic: Boolean get() = this != COMMANDER

    /** Flies — ignores terrain move cost, crosses water, and is only hit by anti-air. */
    val air: Boolean get() = moveClass == MoveClass.AIR

    /** Carries fuel and must refuel at base or it is lost. */
    val fuelLimited: Boolean get() = maxFuel > 0
}

/**
 * A single unit on the board. Mutable: HP, position, and per-turn action flags
 * change during play.
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

    fun clampHp() {
        if (hp > MAX_HP) hp = MAX_HP
        if (hp < 0) hp = 0
    }

    companion object {
        const val MAX_HP = 10
    }
}

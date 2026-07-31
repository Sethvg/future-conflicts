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
enum class UnitType(
    val label: String,
    val glyph: String,
    val maxMove: Int,
    val minRange: Int,
    val maxRange: Int,
    val cost: Int,
    val canCapture: Boolean = false,
) {
    INFANTRY("Infantry", "I", maxMove = 3, minRange = 1, maxRange = 1, cost = 1000, canCapture = true),
    MECH("Mech", "M", maxMove = 2, minRange = 1, maxRange = 1, cost = 3000, canCapture = true),
    RECON("Recon", "R", maxMove = 8, minRange = 1, maxRange = 1, cost = 4000),
    TANK("Tank", "T", maxMove = 6, minRange = 1, maxRange = 1, cost = 7000),
    ARTILLERY("Artillery", "A", maxMove = 5, minRange = 2, maxRange = 3, cost = 6000);

    /** Indirect units (artillery) attack at range but cannot move and fire. */
    val indirect: Boolean get() = maxRange > 1
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

package com.example.futureconflicts.game

/**
 * Economy tuning constants. Kept together so balance lives in one place.
 * See [docs/VISION.md] for the design intent.
 */
object Economy {
    /** Gold each side starts a battle with. */
    const val STARTING_GOLD = 5000

    /** Income an owned HQ produces per turn. */
    const val HQ_INCOME = 100

    /** Income an owned city produces per turn, multiplied by its level. */
    const val CITY_INCOME_PER_LEVEL = 100

    /** Gold to raise a city one level. */
    const val CITY_UPGRADE_COST = 1000

    /** Cities cap out here. Level 1 is the un-upgraded baseline. */
    const val CITY_MAX_LEVEL = 3

    /** Capture points a building starts with / resets to; a capturer subtracts its HP. */
    const val CAPTURE_POINTS = 20
}

/** Per-team wallet and Commander-loss tracking. */
class PlayerState(
    var gold: Int = Economy.STARTING_GOLD,
    /** How many times this player's Commander has been destroyed (drives rebuy cost). */
    var commanderLosses: Int = 0,
) {
    /**
     * Rebuy cost for a Commander given its base cost and per-loss [multiplier]:
     * `base × multiplier^losses` (compounds each loss). Used once Commanders land.
     */
    fun commanderCost(base: Int, multiplier: Double): Int {
        var cost = base.toDouble()
        repeat(commanderLosses) { cost *= multiplier }
        return cost.toInt()
    }
}

/**
 * A capturable structure on the board: cities (income) and HQs (income + a capture
 * win condition). Ownership and level are mutable; terrain in [GameMap] stays static.
 */
class Building(
    val pos: Pos,
    val kind: Kind,
    var owner: Team?,          // null = neutral
    var level: Int = 1,
    var captureLeft: Int = Economy.CAPTURE_POINTS,
) {
    enum class Kind { CITY, HQ }

    /** Gold produced per turn while owned. */
    val incomePerTurn: Int
        get() = when (kind) {
            Kind.HQ -> Economy.HQ_INCOME
            Kind.CITY -> Economy.CITY_INCOME_PER_LEVEL * level
        }

    fun copy(): Building = Building(pos, kind, owner, level, captureLeft)
}

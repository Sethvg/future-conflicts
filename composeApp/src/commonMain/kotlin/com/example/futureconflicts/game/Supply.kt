package com.example.futureconflicts.game

import kotlin.random.Random

/**
 * Supply drops: a periodic random boon handed to a side to keep long games moving
 * and add swing. Every [INTERVAL] turns a side takes, it receives one [SupplyKind]
 * drawn from [WEIGHTS] using the battle's **seeded** RNG — so the whole sequence is
 * deterministic for a given seed (replay- and multiplayer-friendly).
 *
 * The *effects* of each kind are fixed constants, so a recorded
 * [Action.SupplyDrop] fully specifies the outcome without needing the RNG on replay.
 * Balance for all of it lives here.
 */
object Supply {
    /** A side gets a drop at the start of its turn every this many of its own turns. */
    const val INTERVAL = 7

    /** Gold granted by a [SupplyKind.GOLD] windfall (also the no-landing-zone fallback). */
    const val GOLD_WINDFALL = 3000

    /** HP restored to each friendly unit by a [SupplyKind.HEAL] drop (clamped to max). */
    const val HEAL_AMOUNT = 3

    /** The unit spawned at HQ by a [SupplyKind.REINFORCE] drop. */
    val REINFORCE_UNIT = UnitType.INFANTRY

    /** Draw weights (higher = more likely). Data-driven so balance + tests stay in one place. */
    val WEIGHTS: List<Pair<SupplyKind, Int>> = listOf(
        SupplyKind.GOLD to 4,
        SupplyKind.REINFORCE to 3,
        SupplyKind.HEAL to 2,
        SupplyKind.REVEAL to 1,
    )

    val totalWeight: Int get() = WEIGHTS.sumOf { it.second }

    /** Draw one boon from [rng] according to [WEIGHTS]. Advances the RNG by one int. */
    fun roll(rng: Random): SupplyKind {
        var r = rng.nextInt(totalWeight)
        for ((kind, weight) in WEIGHTS) {
            if (r < weight) return kind
            r -= weight
        }
        return WEIGHTS.last().first // unreachable; guards against rounding
    }
}

/** The kinds of supply-drop boon. [label] is shown in the HUD message. */
enum class SupplyKind(val label: String) {
    /** A lump of gold. */
    GOLD("gold windfall"),

    /** A free unit spawned at the side's HQ. */
    REINFORCE("reinforcements"),

    /** Every friendly unit is patched up. */
    HEAL("field repairs"),

    /** The whole map is revealed to the side until its next turn. */
    REVEAL("recon sweep"),
}

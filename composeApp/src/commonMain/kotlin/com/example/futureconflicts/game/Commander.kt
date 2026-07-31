package com.example.futureconflicts.game

/**
 * A Commander is a faction identity: a theme, a signature unit it can field in an
 * Elite version, a per-loss rebuy multiplier for its hero unit, and — the core of
 * it — **three fixed army-wide passive traits** ([passives]). Passives are data;
 * the stat pipeline in [Battle] folds them into every unit's effective stats.
 *
 * See [docs/VISION.md]: the 3 passives are fixed per Commander (not a loadout), and
 * losing the Commander hero unit compounds its rebuy cost by [rebuyMultiplier].
 */
class Commander(
    val id: String,
    val name: String,
    val theme: String,
    val passives: List<Passive>,
    val signature: UnitType,
    val rebuyMultiplier: Double,
) {
    fun amount(kind: PassiveKind): Int = passives.filter { it.kind == kind }.sumOf { it.amount }
}

/** What a [Passive] modifies. Amounts are additive within a kind. */
enum class PassiveKind {
    MOVE,       // + movement tiles (all units)
    RANGE,      // + max attack range (indirect units only, to keep direct units melee)
    FIREPOWER,  // +% damage dealt
    ARMOR,      // +% damage reduction
    INCOME,     // +% gold income
    DISCOUNT,   // +% cheaper unit production
}

data class Passive(val kind: PassiveKind, val amount: Int) {
    val label: String
        get() = when (kind) {
            PassiveKind.MOVE -> "+$amount Move"
            PassiveKind.RANGE -> "+$amount Range"
            PassiveKind.FIREPOWER -> "+$amount% Firepower"
            PassiveKind.ARMOR -> "+$amount% Armor"
            PassiveKind.INCOME -> "+$amount% Income"
            PassiveKind.DISCOUNT -> "-$amount% Unit Cost"
        }
}

/** Elite (signature) unit tuning — a mid tier between basic units and the Commander. */
object Elite {
    const val COST_MULTIPLIER = 2.0
    const val MOVE_BONUS = 1
    const val FIREPOWER_BONUS = 20 // percent
    const val ARMOR_BONUS = 10     // percent
}

/** The built-in Commander roster. Adding a faction = adding data here. */
object Commanders {
    val STORM = Commander(
        id = "storm",
        name = "Cmdr. Vale",
        theme = "Storm Vanguard — fast, aggressive blitz",
        passives = listOf(
            Passive(PassiveKind.MOVE, 1),
            Passive(PassiveKind.FIREPOWER, 10),
            Passive(PassiveKind.INCOME, 10),
        ),
        signature = UnitType.RECON,
        rebuyMultiplier = 1.5,
    )

    val IRON = Commander(
        id = "iron",
        name = "Cmdr. Krause",
        theme = "Iron Column — heavy armor, grinds forward",
        passives = listOf(
            Passive(PassiveKind.FIREPOWER, 10),
            Passive(PassiveKind.ARMOR, 15),
            Passive(PassiveKind.DISCOUNT, 10),
        ),
        signature = UnitType.TANK,
        rebuyMultiplier = 1.6,
    )

    val SIEGE = Commander(
        id = "siege",
        name = "Cmdr. Okonkwo",
        theme = "Siege Marshal — range and economy",
        passives = listOf(
            Passive(PassiveKind.RANGE, 1),
            Passive(PassiveKind.INCOME, 20),
            Passive(PassiveKind.ARMOR, 5),
        ),
        signature = UnitType.ARTILLERY,
        rebuyMultiplier = 1.4,
    )

    val all: List<Commander> = listOf(STORM, IRON, SIEGE)

    fun byId(id: String): Commander = all.first { it.id == id }
}

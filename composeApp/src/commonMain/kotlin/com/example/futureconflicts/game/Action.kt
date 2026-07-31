package com.example.futureconflicts.game

/**
 * Every state-changing intent, expressed as data. This is the single funnel the
 * core applies ([Battle.apply]); the UI, the AI, and (later) the network all
 * produce the same [Action]s. Units are referenced by their tile [Pos] (unique at
 * apply-time), which keeps actions serializable without a separate id scheme —
 * important for deterministic replay and multiplayer.
 *
 * `from` is where the acting unit currently stands; `to` is where it moves before
 * acting (`to == from` means it acts without moving).
 */
sealed interface Action {
    /** Move (or stay) and do nothing else. */
    data class Wait(val from: Pos, val to: Pos) : Action

    /** Move (or stay) then attack the unit standing on [target]. */
    data class Attack(val from: Pos, val to: Pos, val target: Pos) : Action

    /** Move (or stay) onto a capturable building at [to] and capture it. */
    data class Capture(val from: Pos, val to: Pos) : Action

    /** Produce a unit of [type] at the owned HQ at [at]; [elite] builds the signature variant. */
    data class Build(val type: UnitType, val at: Pos, val elite: Boolean = false) : Action

    /** Spend gold to raise the level of the owned city at [at]. */
    data class Upgrade(val at: Pos) : Action

    /**
     * Grant [team] a supply-drop boon of [kind]. Engine-emitted on the [Supply.INTERVAL]
     * cadence (the [kind] is pre-rolled from the seeded RNG), so the recorded action is
     * self-contained for replay.
     */
    data class SupplyDrop(val team: Team, val kind: SupplyKind) : Action

    /** End the current side's turn (runs income + the enemy turn). */
    data object EndTurn : Action
}

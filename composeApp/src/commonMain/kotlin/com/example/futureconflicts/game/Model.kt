package com.example.futureconflicts.game

/**
 * Core value types for the tactics model. This file — and everything else in
 * [com.example.futureconflicts.game] — is deliberately free of Compose and any
 * platform imports so the rules stay host-testable and portable to iOS.
 */

/** A cell on the battle grid. */
data class Pos(val x: Int, val y: Int) {
    fun manhattan(other: Pos): Int = kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y)
}

/** The two sides. A one-vs-one skirmish for the vertical slice. */
enum class Team {
    PLAYER,
    ENEMY;

    val other: Team get() = if (this == PLAYER) ENEMY else PLAYER
    val label: String get() = if (this == PLAYER) "Blue Army" else "Red Army"
}

package com.example.futureconflicts.game

/**
 * **Drone Command** tuning — the signature mechanic.
 *
 * A Drone Command building maintains a *flight* of up to [Building.level] drones. They
 * cost **no gold**: their price is paid in **logistics** instead —
 *  - they carry [DRONE_FUEL] fuel, burn it flying, and must return to the Drone Command
 *    to refuel (they crash if it runs dry, like any aircraft);
 *  - a drone that is destroyed or crashes leaves its slot empty for [REBUILD_COOLDOWN]
 *    of the owner's turns before a replacement launches.
 *
 * Drones act **autonomously at the start of the owner's turn, before manual control** —
 * they are **scouts**: they push toward unseen ground to strip fog, then head home when
 * low on fuel. They carry no weapon (a *strike package* is a planned upgrade), and
 * anti-air shreds them.
 */
object Drones {
    /** Fuel a drone launches with; also its capacity. */
    const val DRONE_FUEL = 12

    /** Owner-turns a destroyed drone's slot stays empty before it is replaced. */
    const val REBUILD_COOLDOWN = 5

    /** Drones head home once their remaining fuel is only just enough to get back. */
    const val RETURN_FUEL_MARGIN = 4

    /** Cap on level (and therefore flight size), mirroring city upgrades. */
    const val MAX_LEVEL = 3

    /** Gold to raise a Drone Command one level (one more drone). */
    const val UPGRADE_COST = 2500
}

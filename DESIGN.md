# Future Conflicts — Design

An **Advance Wars**-style turn-based tactics game. Two armies maneuver units across
a terrain grid; the goal (for now) is to destroy the opposing army.

## Core loop

1. On your turn, each of your units may **move** once and then take **one action**
   (attack an enemy in range, or wait).
2. Combat is deterministic: damage scales with the attacker's HP and is reduced by
   the defender's terrain. Direct attackers may be **counterattacked**.
3. Press **End Turn**; the enemy army acts, then control returns to you (a new
   "Day").
4. Eliminate the enemy army to win.

## Board & terrain

The map is a rectangular grid. Terrain sets movement cost and defense:

| Terrain  | Move cost | Defense | Notes                          |
|----------|-----------|---------|--------------------------------|
| Road     | 1         | 0       | fast, exposed                  |
| Plains   | 1         | 1       | open ground                    |
| Forest   | 2         | 2       | cover                          |
| City     | 1         | 3       | strong cover (capturable later)|
| Mountain | 3         | 4       | slow, best cover               |
| HQ       | 1         | 4       | capture to win (later)         |
| Sea      | —         | —       | impassable to ground units     |

## Units

| Unit      | Move | Range | Role                                        |
|-----------|------|-------|---------------------------------------------|
| Infantry  | 3    | 1     | cheap, captures (later), weak vs armor      |
| Mech      | 2    | 1     | slow, strong anti-armor                     |
| Recon     | 8    | 1     | fast scout, shreds infantry, dies to tanks  |
| Tank      | 6    | 1     | strong all-rounder                          |
| Artillery | 5    | 2–3   | indirect; huge damage, can't move and fire  |

## Combat math

Base damage is a percentage looked up per attacker/defender matchup, then:

```
dmgHP = base/10 · (attackerHP/10) · (1 − 0.1 · defenseStars · defenderHP/10)
```

HP is 0–10. A surviving **direct** defender counterattacks if the attacker is in
its range; **indirect** units neither counter nor get countered (they strike from
outside melee range).

## Enemy AI (current)

Greedy per-unit: take the highest-damage attack available this turn (preferring
kills); otherwise advance toward the nearest player unit. Deliberately simple —
smarter AI is on the roadmap.

## Design pillars

- **Readable at a glance** — flat colors, one glyph per unit, HP shown when hurt.
- **Deterministic** — no dice; positioning and matchups decide fights.
- **Portable rules** — the whole game is pure Kotlin, testable off-device, and the
  same code drives Android and iOS.

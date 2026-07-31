# Future Conflicts — Design (current mechanics)

An **Advance Wars**-style turn-based tactics game. Two armies (Blue = player,
Red = AI) maneuver units across a terrain grid, fund their war machine from
captured cities, and fight under fog of war. This doc describes **what's actually
implemented today**; the bigger vision is in [VISION.md](VISION.md), the ordered
plan in [../ROADMAP.md](../ROADMAP.md), deferred polish in [BACKLOG.md](BACKLOG.md).

## Core loop

1. **Pick a Commander** before the battle (Blue chooses; Red auto-picks another).
2. On your turn each ready unit may **move** once, then take **one action**:
   attack an enemy in range, **capture** a building it's standing on, or wait.
3. At an owned **production building** — Barracks (infantry), Factory (vehicles), or the
   HQ (which doubles as a Barracks) — spend gold to **build** units (basic / Elite
   signature); the Commander is an HQ-only purchase.
4. **End Turn** → income is collected, the enemy army acts, control returns to you
   (a new "Day").
5. **Win** by destroying the enemy army **or** capturing the enemy HQ.

## Board & terrain

Rectangular grid. Terrain sets movement cost, defense (damage reduction), and
affects vision:

| Terrain  | Move cost | Defense | Vision note                     |
|----------|-----------|---------|---------------------------------|
| Road     | 1         | 0       | fast, exposed                   |
| Plains   | 1         | 1       | open ground                     |
| Forest   | 2         | 2       | **hides units** from unadjacent enemies |
| City     | 1         | 3       | capturable; income              |
| Mountain | 3         | 4       | best cover; **+1 sight** to occupant |
| HQ       | 1         | 4       | capturable; income; **capture = win** |
| Sea      | —         | —       | impassable to ground units      |

## Units

HP is 0–10 (shown on the unit when damaged).

| Unit      | Move | Range | Vision | Cost   | Captures | Role                                   |
|-----------|------|-------|--------|--------|----------|----------------------------------------|
| Infantry  | 3    | 1     | 2      | 1000   | yes      | cheap, captures, weak vs armor         |
| Mech      | 2    | 1     | 2      | 3000   | yes      | slow, strong anti-armor                |
| Recon     | 8    | 1     | 5      | 4000   | no       | fast scout, shreds infantry, dies to tanks |
| Tank      | 6    | 1     | 3      | 7000   | no       | strong all-rounder                     |
| Artillery | 5    | 2–3   | 2      | 6000   | no       | indirect; big damage, can't move & fire |
| Anti-Air  | 6    | 1     | 3      | 8000   | no       | shreds aircraft & infantry, weak vs armor |
| Gunship   | 7    | 1     | 3      | 9000   | no       | **flies** (fuel); strong vs ground; only anti-air hits it |
| Commander | 6    | 1     | 4      | 16000  | no       | hero unit; strong; one per player      |

- **Movement classes:** ground (terrain-bound), **air** (ignores terrain cost, flies over
  units and water, only hit by anti-air), naval (later). Air units carry **fuel** — they
  burn it each turn, refuel on an owned **Airport**/HQ, and **crash** at zero.
- **Elite** units are a Commander's *signature chassis* built at 2× cost with
  +1 move / +20% firepower / +10% armor (marked with a gold outline).
- **Commander** hero unit: one per player. If it dies, its **rebuy cost multiplies**
  by the Commander's factor (compounds per loss).

## Combat math

Base damage is a percentage from the attacker/defender matchup table
([Combat.kt](../composeApp/src/commonMain/kotlin/com/example/futureconflicts/game/Combat.kt)), then:

```
dmgHP = base/10 · (attackerHP/10) · (1 − 0.1 · defenseStars · defenderHP/10)
                · attackMul · defenseMul
```

`attackMul`/`defenseMul` fold in Commander firepower/armor passives and the Elite
bonus. A surviving **direct** defender counterattacks if the attacker is in its
range; **indirect** units neither counter nor get countered.

## Economy

- Each owned **city** yields gold/turn (`100 × level`); the **HQ** yields a flat
  100. Income is granted at the start of each side's turn (+ Commander income %).
- **City upgrades:** 1000 gold → +1 level (+income), up to level 3.
- **Capture:** a foot unit (Infantry/Mech) on an enemy/neutral building subtracts
  its HP from the building's capture points (start 20); at 0 it flips owner and is
  **razed to level 1**. Capturing the enemy **HQ wins**. Capturing a *production*
  building grants build access only — it never converts existing units.
- **Production:** units come from owned category buildings — **Barracks** (infantry),
  **Factory** (vehicles); the **HQ** also builds infantry and is where the Commander is
  bought. (Airport/Port/Drone Command kinds exist for later slices.)

## Commanders & factions

Each Commander is a faction identity with **three fixed army-wide passive traits**
(data-driven; folded through a stat pipeline in `Battle`). Passive kinds: Move,
Range (indirect only), Firepower %, Armor %, Income %, Discount %. Built-in roster:
**Storm Vanguard** (Vale: +1 Move, +10% Firepower, +10% Income; sig Recon),
**Iron Column** (Krause: +10% Firepower, +15% Armor, −10% Cost; sig Tank),
**Siege Marshal** (Okonkwo: +1 Range, +20% Income, +5% Armor; sig Artillery).

## Fog of war

A **per-player view** derived from the world (the sim itself is never hidden).
A team sees the union of its units' sight radii (+1 on mountains) plus a radius-2
around owned buildings. Enemies standing on **forest** are hidden until one of your
units is adjacent. The renderer dims fogged tiles and hides unseen enemies.
Toggle via `Battle.fogEnabled`.

## Supply drops

Every `Supply.INTERVAL` (7) turns a side takes, it receives a random **supply drop**
at the start of its turn, drawn from a **seeded RNG** so the whole sequence is
reproducible (replay- and multiplayer-friendly). The boon is one of — weighted
4 / 3 / 2 / 1 — **gold windfall**, **reinforcements** (a free unit at HQ),
**field repairs** (heal every friendly unit), or a **recon sweep** (reveal the whole
map until your next turn). The draw is emitted as a serializable
`Action.SupplyDrop(team, kind)`, so a recorded match replays without the RNG.

## Enemy AI (current)

Greedy per-unit: take the best available attack (preferring kills); else capture a
reachable building; else advance toward the nearest player unit. It currently
**sees through fog** and does not use its economy — see [BACKLOG.md](BACKLOG.md).

## Design pillars

- **Readable at a glance** — flat colors + one glyph per unit today; gritty pixel
  art later.
- **Deterministic** — combat has no dice; positioning, matchups, and terrain decide
  fights. The only randomness (supply-drop draws) is seeded and reproducible.
- **Portable, testable rules** — pure Kotlin core, host-tested, one codebase for
  Android + iOS, and an Action funnel built for multiplayer.

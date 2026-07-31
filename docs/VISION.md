# Future Conflicts — Vision

The north-star design doc. If we take a break, **start here**, then read
[DESIGN.md](DESIGN.md) (current mechanics) and [ARCHITECTURE.md](ARCHITECTURE.md)
(how the code is organized to get there). The ordered build plan lives in
[../ROADMAP.md](../ROADMAP.md).

## Elevator pitch

A near-future turn-based tactics game — an **Advance Wars descendant** with more
grit, deeper economy, and collectible **Commanders** who each lead a themed,
uniquely-skinned army. Deterministic, positional combat; capture and upgrade
cities to fund your war machine; buy elite units and your hero Commander from HQ;
scout through fog; ride random supply drops. Built to grow into **online
multiplayer**.

## Pillars

1. **Positional, deterministic combat** — no dice. Terrain, matchups, and
   maneuver decide fights; the same inputs always produce the same result
   (essential for replays and netcode).
2. **Economy as strategy** — cities are the map's real objective. Capture, upgrade,
   and protect income; deny the enemy theirs.
3. **Commander identity** — you don't pick a color, you pick a *character*: a
   themed roster, a unique skin, and three army-wide passive traits.
4. **Readable grit** — a grittier, higher-fidelity look than Advance Wars, but
   always readable at phone tile-size. Style serves clarity.
5. **Built for multiplayer** — single-player and AI first, but every system is
   designed so two humans (async or live) can play the same simulation later.

## Core loop

Per turn, per player:
1. **Income** — collect gold from owned/upgraded cities and HQ.
2. **Produce** — at HQ, buy basic units, elite (unique) units, or your Commander.
3. **Maneuver** — move units (terrain modifies cost); scout to lift fog.
4. **Engage** — attack; damage scales with attacker HP and defender terrain;
   direct fights draw counterattacks; indirect units strike safely at range.
5. **Objectives** — capture cities/HQ; upgrade cities; hold ground.
6. **End turn** — opponent (AI or human) responds. Every 7 turns, a **supply
   drop** rolls a random boon.

Win by eliminating the enemy army **or** capturing their HQ.

## Commanders (the identity layer)

- Each Commander leads a **themed army** (e.g. rapid-strike air cavalry, entrenched
  armor, guerrilla infantry). Themes bias the roster and the recommended playstyle.
- **Three passive traits**, army-wide, defining the faction's edge — e.g.
  `+1 attack`, `+1 range`, `+1 movement`, cheaper-of-a-type, vision bonus,
  capture speed. Passives are **data**, stacked additively onto base stats.
- The **Commander is also a unit** you purchase from HQ — powerful, expensive,
  one per player. Losing it should hurt (morale/penalty TBD) but not auto-lose.
- **Top-down tokens are consistent:** the map representation of a unit is the **same
  across all factions** — one shared token per unit type — so the board stays readable.
  Faction identity shows in the **battle-animation scenes** (slight variations) and
  Commander portraits, *not* in the top-down token.
- **Any Commander, any color:** tokens are authored **neutral/desaturated** with a
  **team-color tint/mask** so the engine recolors the same art to any player color at
  runtime (blue vs red today). Color is a runtime parameter (see ARCHITECTURE.md).

## Production: capture your industry

Units are built from **category-specific production buildings** you own — each
capturable like a city, so **map control is your build order**:
- **Barracks** → infantry (the foot units that capture)
- **Factory** → vehicles (recon, tank, artillery, anti-air, APC…)
- **Airport** → aircraft (gunship, fighter, bomber…)
- **Port** → ships (lander, destroyer, battleship, submarine)
- **Drone Command** → an autonomous drone flight (special — see below)

The **HQ** is income + the capture-to-win seat, and where you buy your **Commander**.
Lose a production building and that whole category goes dark until you retake one.

Within a category, tiers price by power: **Basic** → **Elite/signature** (faction
variant: stronger, pricier) → **Commander** (the hero, top of the chart).

## Domains: land, air & sea *(direction)*

The battlefield spans **three movement classes**: **ground** (land only), **naval**
(sea only), and **air** (moves anywhere, ignores terrain cost, can't capture, hard-
countered by anti-air). Crossing land↔sea needs a transport (**lander** / transport
helo). Naval brings ships — **lander** (amphibious transport), **destroyer** (anti-ship
/ anti-air / anti-sub), **battleship** (long-range coastal bombardment), **submarine**
(stealth that leans on fog). **Fuel:** air units (and, later, naval) carry fuel and must
refuel at base or they're lost — the mechanic debuts with the drone flight below.
*Open: exact ship roster; coastal (ship↔land) combat rules.*

## Sea economy: oil wells *(direction)*

**Oil wells are cities on water** — capturable offshore platforms on `SEA` tiles that
generate gold/turn and upgrade like cities (razed to L1 on capture). Since foot units
can't stand on water, they're seized by a **naval unit occupying them** (or marines
delivered by a lander). Holding the sea now *pays*, giving a reason to contest it.
*Open: capture method (ship-occupies vs delivered infantry); upgrade curve.*

## Drone Command *(signature mechanic)*

A capturable, upgradeable building that fields an **autonomous scout-drone flight** —
up to **N persistent drones (N = its level)** that the AI flies **automatically, before**
the owner takes manual control. Their job (for now) is **reconnaissance**: fan out to
**reveal fog** ahead of your army, then return to base. Drones are **free** (no gold),
so their limits are logistical, not economic:
- **Fuel:** they burn fuel operating and must **return to the Drone Command to refuel**;
  run dry and they crash (lost).
- **Death cooldown:** a downed/crashed drone leaves its slot empty for a **long cooldown**
  before a replacement is built — free-ness paid for in downtime.
- **Countered by anti-air**, giving the air/drone layer a clean counter.
- **Future upgrade — strike package:** drones gain a weapon, turning the recon flight
  into an offensive one.

A persistent, self-replenishing AI **recon** flight balanced by fuel + downtime rather
than cost. Distinct from Advance Wars; it deepens the fog game.

## Economy

- **Cities** generate gold each turn. They can be **upgraded** with gold
  (e.g. 1000 → +100 gold/turn per level), stacking over levels.
- **Razing:** when a city is lost/razed it **drops back to level 1** — upgrades are
  an investment you must defend, a real risk/reward decision.
- **HQ** is production + a high-value capture objective.
- Gold sinks: unit production, city upgrades, (later) Commander powers.

## Fog of war

- Per-unit **vision radius** (terrain affects it — mountains see far, forests hide).
- Hidden enemy positions; scouting (fast Recon, high ground) is a core activity.
- Designed to be **per-player state** from day one so multiplayer reveals correctly.

## Terrain

Movement cost + defense modifiers (already in the slice): road/plains/forest/
mountain/city/HQ/sea, expanding to rivers, roads, bridges, buildings. Later:
per-unit movement classes (treads vs tires vs foot vs air) with distinct cost
tables, and air/sea units.

## Supply drops (every 7 turns)

A shared or per-player timer fires a **random boon** from a weighted table:
- spawn a free unit,
- a gold windfall,
- reveal a swath of fog,
- heal/resupply units,
- (future) a one-shot power.
Rolled from a **seeded RNG** so it's replay/multiplayer-deterministic.

## Art direction — gritty detailed pixel art *(decided)*

Grittier and higher-fidelity than Advance Wars' cartoon style; muted, near-future
military palette. Rationale over photorealism: **readability at tile size**,
**clean recoloring**, **feasible animation**, and it plays to our **PixelLab**
pipeline.

- **Recoloring:** author each top-down token **once** (neutral gray) + a **team-color
  tint/mask** the engine applies at draw time — one shared token set, any player color.
- **Battle animations — compose, don't multiply:** the battle scene is layered —
  *attacker animation + defender animation + terrain backdrop* — each authored
  independently. Cost is **additive (N + M)**, not every-pair (N × M). This keeps
  "an animation for every matchup" tractable. **Faction flavor lives here** — slight
  per-faction variations in the attacker/defender art, while the top-down token stays shared.
- **Where realism lives:** push detail/realism into **terrain and battle
  backdrops**; keep **units** stylized-but-detailed so silhouettes read.
- **Pipeline:** generate via the PixelLab MCP (`mcp__pixellab__*`); keep raw
  prompts/params with the assets so art is reproducible.

## Multiplayer readiness (design now, ship later)

We won't build netcode yet, but we won't paint ourselves out of it either:
- **Deterministic simulation** driven by serializable **Actions/Commands**
  (`Move`, `Attack`, `Build`, `UpgradeCity`, `EndTurn`) applied by the core.
- **Seeded RNG** for all randomness (supply drops, any variance).
- **Per-player view state** (fog) separate from the authoritative world.
- Same core runs local hot-seat, vs-AI, and (later) async/live online.

## Decided

- **Losing your Commander escalates its rebuy cost.** Each Commander has a specific
  multiplier; every time your Commander is destroyed, the cost to buy it again goes
  up by that multiplier (compounding per loss:
  `rebuyCost = baseCost × multiplier ^ timesLost`). So a fragile hero you keep
  feeding the front becomes ruinously expensive — losing the Commander is a real
  setback without being an instant game-over.
- **The 3 passives are fixed per Commander** (not a player-chosen loadout). Each
  Commander *is* its trait set — identity, not customization.

## Open questions (revisit)

- Supply-drop timer: shared clock or per-player? Boon table weights?
- Commander powers (active, chargeable) — in scope, or passives-only for v1?
- Map source: hand-authored only, or a map format + editor later?
- Monetization/meta (collecting Commanders) — out of scope for now; note it exists.

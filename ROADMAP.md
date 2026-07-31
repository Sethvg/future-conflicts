# Future Conflicts — Roadmap

The ordered plan. North star: [docs/VISION.md](docs/VISION.md). Current mechanics:
[docs/DESIGN.md](docs/DESIGN.md). Code conventions: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Tracked in plain git (like the sibling `stroads` project). Newest work at the top
of "Done".

## Done

- **Art — Batch 1 (units + terrain), wired.** 6 neutral-gray colorizable unit tokens +
  7 terrain tiles via the `pixel-sprite-smith` pipeline, drawn in `GameScreen.kt`
  (base64-embedded + `expect/actual` decode; team-tinted). Batches 2+ in [docs/ART.md](docs/ART.md).
- **Supply drops.** Seeded RNG in game state; every 7 turns a side takes, a weighted
  boon (gold windfall / reinforcements / field repairs / recon sweep) emitted as a
  serializable `Action.SupplyDrop(team, kind)` so recorded matches replay without the RNG.
- **Fog of war.** Per-player vision view (kept out of the authoritative sim for
  multiplayer correctness): per-unit sight radius, mountains extend sight, forests
  hide enemies until you're adjacent, owned buildings grant vision. Renderer dims
  fogged tiles and hides unseen enemies. `fogEnabled` toggle for debugging.
- **Commanders & factions.** Data-driven Commanders with 3 fixed army-wide passives
  (MOVE/RANGE/FIREPOWER/ARMOR/INCOME/DISCOUNT) folded through a stat pipeline;
  Commander hero unit (one per player, rebuy cost compounds per loss); Elite
  signature units (mid tier); pre-battle Commander pick + roster of 3 factions.
- **Economy & HQ purchasing + Action funnel.** Serializable `Action`s applied by
  `Battle.apply` (UI + AI both emit them); cities/HQ income, upgrades (raze→L1),
  capture, capture-the-HQ win.
- **Foundation & tests.** `jvm()` host-test target; **54 passing tests**
  (`./gradlew :composeApp:jvmTest`) across combat, movement, turn flow, economy,
  commanders, fog, and supply drops. High-level docs (Vision/Design/Architecture).
- **Vertical slice (playable, on device).** KMP + Compose Multiplatform scaffold;
  pure-Kotlin tactics core (terrain, 5 unit types, AW-style damage + counters,
  Dijkstra movement, select→move→attack flow, greedy enemy AI, win-on-elimination,
  Restart); Compose Canvas renderer + tap input.

## Next — sequenced

The big arc: distributed production → air + drones → naval → sea economy. Each slice
ships playable + host-tested. Art (Batch 2+) and game-feel polish run as a **parallel
track** ([docs/ART.md](docs/ART.md)); the renderer-touching steps are timed around the
AdMob edits so we don't collide on `GameScreen.kt`.

### 1. Production-building overhaul *(foundational — everything below needs it)*
- Generalize `Building` into production kinds: **Barracks** (infantry), **Factory**
  (vehicles), **Airport** (aircraft), **Port** (ships), plus income (City, Oil Well) and
  the HQ. Capturable like cities.
- Move `Action.Build` off the HQ: you build a category from an owned building of the
  matching kind. Build menu opens on tapping that building. HQ keeps income + Commander
  purchase + capture-to-win.
- Place the new buildings on Twin Ridges (each side starts with Barracks+Factory;
  Airport/Port/Drone Command/Oil Well are contested). Keep the current units working.

### 2. Air layer + Anti-Air *(introduces the air movement class + fuel)*
- Movement classes: ground/naval/**air** (flyers ignore terrain cost, can't capture).
- **Fuel** attribute (debuts on air): burns per turn, refuel at owned Airport/base,
  crash at 0. **Gunship** + **Anti-Air** (the hard counter). Built from Airport/Factory.

### 3. Drone Command *(signature mechanic)*
- Building maintains up to **N persistent scout drones** (N = level, free of gold) that
  the AI flies **autonomously before the player's turn** to **reveal fog**, refuelling at
  base (fuel); crash if dry. A downed drone → **long build cooldown** before replacement.
- Reuse the enemy-AI planner for the autonomous recon phase. *Future: strike upgrade.*

### 4. Naval + Port *(the sea domain)*
- `SEA` becomes navigable for ships; **Lander** (amphibious transport), **Destroyer**,
  **Battleship** (coastal bombardment), **Submarine** (fog-stealth). Port builds/repairs.
- Amphibious transport (load/unload across the land↔sea boundary).

### 5. Oil wells *(sea economy)*
- Capturable offshore income platforms on `SEA`, upgradeable like cities; captured by a
  **ship occupying** them (MVP). Holding the sea pays.

### 6. Fill-ins & tiers
- Fighter/Bomber, Heavy Tank, Rockets (indirect tier-2), APC land transport; Elite
  signature variants per production building.

### 7. Game feel & polish
- Step/animate the enemy (and drone) turn; damage preview; attack flashes; sound;
  proper end screen.

### 8. Multiplayer
- With Actions + determinism + per-player views in place: hot-seat first, then online.

## Polish, tech debt & known simplifications

These are tracked in **[docs/BACKLOG.md](docs/BACKLOG.md)** — the running list of
corners cut, balance to tune, and polish to revisit. When you defer something to
ship a feature, add a line there; when you fix it, delete the line.

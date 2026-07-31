# Future Conflicts — Roadmap

The ordered plan. North star: [docs/VISION.md](docs/VISION.md). Current mechanics:
[docs/DESIGN.md](docs/DESIGN.md). Code conventions: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Tracked in plain git (like the sibling `stroads` project). Newest work at the top
of "Done".

## Done

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

### 1. Art pass (gritty detailed pixel art) *(current focus)*
- PixelLab sprites for units (per faction) + terrain tiles; **team-color mask**
  recolor at runtime. Produced via the `pixel-sprite-smith` agent **in batches**,
  tracked in [docs/ART.md](docs/ART.md). Batch 1 (units + terrain) is staged in `art/`.
- **Battle animation scenes** composed from layers (attacker + defender + backdrop).
- Wire staged PNGs into the renderer (needs a platform image-load bridge — see ART.md).

### 2. Game feel & polish
- Animate/step the enemy turn; explicit Move/Attack/Wait menu + Cancel/undo;
  damage preview before committing; attack flashes; sound.

### 3. Multiplayer
- With Actions + determinism + per-player views already in place: hot-seat first,
  then async/live online.

## Polish, tech debt & known simplifications

These are tracked in **[docs/BACKLOG.md](docs/BACKLOG.md)** — the running list of
corners cut, balance to tune, and polish to revisit. When you defer something to
ship a feature, add a line there; when you fix it, delete the line.

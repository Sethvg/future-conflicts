# Future Conflicts — Roadmap

The ordered plan. North star: [docs/VISION.md](docs/VISION.md). Current mechanics:
[docs/DESIGN.md](docs/DESIGN.md). Code conventions: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Tracked in plain git (like the sibling `stroads` project). Newest work at the top
of "Done".

## Done

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
- **Foundation & tests.** `jvm()` host-test target; **38 passing tests**
  (`./gradlew :composeApp:jvmTest`) across combat, movement, turn flow, economy,
  and commanders. High-level docs (Vision/Design/Architecture).
- **Vertical slice (playable, on device).** KMP + Compose Multiplatform scaffold;
  pure-Kotlin tactics core (terrain, 5 unit types, AW-style damage + counters,
  Dijkstra movement, select→move→attack flow, greedy enemy AI, win-on-elimination,
  Restart); Compose Canvas renderer + tap input.

## Next — sequenced

### 1. Supply drops *(current focus)*
- Add a **seeded RNG** to game state (deterministic for replay/multiplayer).
- **Every-7-turns supply drop**: weighted boon (spawn a unit / gold windfall /
  reveal fog / heal & resupply), rolled from the seed.
- Tests: deterministic rolls from a fixed seed, boon effects, 7-turn cadence.

### 2. Art pass (gritty detailed pixel art)
- PixelLab sprites for units (per faction) + terrain tiles; **team-color mask**
  recolor at runtime.
- **Battle animation scenes** composed from layers (attacker + defender + backdrop).
- Replace rect/glyph rendering with sprite draws.

### 3. Game feel & polish
- Animate/step the enemy turn; explicit Move/Attack/Wait menu + Cancel/undo;
  damage preview before committing; attack flashes; sound.

### 4. Multiplayer
- With Actions + determinism + per-player views already in place: hot-seat first,
  then async/live online.

## Known simplifications (current)

- Enemy turn resolves instantly; greedy AI **sees through fog** (ignores vision).
- Fog is a view layer only — targeting/movement still use true positions (a hidden
  unit can be shelled/blocks a path); fine for now, revisit with polish.
- One built-in map ("Twin Ridges"); no loader/editor.
- Uniform terrain move cost across unit types (no movement classes yet).
- Rendering is colored rects + letter glyphs (art pass pending).

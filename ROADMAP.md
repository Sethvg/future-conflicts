# Future Conflicts — Roadmap

The ordered plan. North star: [docs/VISION.md](docs/VISION.md). Current mechanics:
[docs/DESIGN.md](docs/DESIGN.md). Code conventions: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Tracked in plain git (like the sibling `stroads` project). Newest work at the top
of "Done".

## Done

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

### 1. Economy & HQ purchasing *(current focus)*
- Refactor toward **Actions as data** + a **seeded RNG** in game state (see
  ARCHITECTURE.md "Target architecture") — do this here, before piling on features.
- Gold per player; **cities** produce income; **city upgrades** (gold → +income/level);
  **razing a city resets it to level 1**.
- **HQ production menu:** buy Basic units, Elite/unique units, and the Commander,
  priced by power. Spawn on/adjacent to HQ.
- **Capture** mechanic (infantry captures cities/HQ) + **capture-the-HQ** win
  condition.
- Tests: income accrual, upgrade/raze, purchase affordability & spawning, capture,
  HQ-capture victory.

### 2. Commanders & factions
- Commander data model: themed roster + **3 army-wide passive traits** (data-driven
  stat pipeline). Elite unit variants per faction.
- Recolorable skins (team-color mask) — art wiring comes with the art pass.
- Tests: passive stacking onto effective stats; per-faction rosters.

### 3. Fog of war + supply drops
- Per-unit **vision**, per-player view state, hidden enemies.
- **Every-7-turns supply drop**: seeded weighted boon (spawn / gold / reveal / heal).
- Tests: vision reveal, fog correctness per player, deterministic drop rolls.

### 4. Art pass (gritty detailed pixel art)
- PixelLab sprites for units (per faction) + terrain tiles; **team-color mask**
  recolor at runtime.
- **Battle animation scenes** composed from layers (attacker + defender + backdrop).
- Replace rect/glyph rendering with sprite draws.

### 5. Game feel & polish
- Animate/step the enemy turn; explicit Move/Attack/Wait menu + Cancel/undo;
  damage preview before committing; attack flashes; sound.

### 6. Multiplayer
- With Actions + determinism + per-player views already in place: hot-seat first,
  then async/live online.

## Known simplifications (current)

- Armies pre-placed; no economy/production yet.
- Enemy turn resolves instantly; greedy AI.
- One built-in map ("Twin Ridges"); no loader/editor.
- Uniform terrain move cost across unit types (no movement classes yet).
- Rendering is colored rects + letter glyphs (art pass pending).

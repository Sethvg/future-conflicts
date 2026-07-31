# Future Conflicts — Architecture

How the code is laid out and the conventions that keep it **scalable and
manageable** as the [VISION.md](VISION.md) features land. Read alongside
[DESIGN.md](DESIGN.md) (current mechanics).

## Modules

- **`composeApp/`** — Kotlin Multiplatform shared code.
  - `commonMain/.../game/` — the **pure game core**: no Compose, no Android, no
    platform APIs. Just Kotlin stdlib. This is the rulebook.
  - `commonMain/.../ui/` — Compose rendering + input (the only gameplay code that
    touches Compose).
  - `iosMain/` — iOS entry point (`MainViewController`).
  - `commonTest/.../game/` — host unit tests for the core.
- **`androidApp/`** — thin Android host (`MainActivity` → `App()`).
- **`iosApp/`** — Xcode project embedding the `ComposeApp` framework (build on a Mac).

**Targets:** `androidLibrary` + `iosX64/iosArm64/iosSimulatorArm64` ship the app;
a **`jvm()`** target exists *only* to run the core's tests fast on any host
(`./gradlew :composeApp:jvmTest`) — no device or emulator needed.

### Planned: extract a compose-free `core` module

Today the pure core lives in `composeApp/.../game/`. As it grows, extract it into
its own `:core` KMP module with **no Compose dependency** (only stdlib). Benefits:
enforces the "core stays pure" rule at the build level, speeds up core tests
(no Compose-desktop on the classpath), and gives a clean seam for a future
headless server/AI. Do this during the economy refactor, when the core gains
enough surface to justify it. Until then, the package boundary is the contract.

### Core file layout — `game/` (split by concern, not one monolith)

Cohesive files so features can be worked on in parallel and tested in isolation:

- **Model / Terrain / Units / GameMap** — value types; the unit roster (with move class,
  fuel, `hitsAir`); terrain; maps & scenarios.
- **Combat / Movement / Vision** — damage table + targeting, Dijkstra reach, fog view.
- **Economy / Commander / Supply / Action** — buildings & income, factions + passives,
  seeded supply drops, the serializable command set.
- **`Battle`** holds the **state + public API + the `apply(Action)` funnel**. Its
  implementation is split into **`internal` extension files on `Battle`** (state is
  `internal` so they can reach it): **`BattleStats`** (derived stats & reach),
  **`BattleActions`** (action executors, combat resolution, victory), **`BattleTurn`**
  (turn lifecycle & fuel), **`BattleAI`** (enemy planner), **`BattleInteraction`** (tap
  handling). One class, cohesive files.

**Rule of thumb:** when a `Battle*` (or any core) file outgrows its concern, split it —
keep files small and single-purpose. New slices add a new `Battle<Concern>.kt` rather
than growing `Battle.kt`.

## The one-way dependency rule

```
platform (androidApp / iosApp)  →  ui (Compose)  →  game (pure Kotlin)
```

`game/` never imports `ui/`, Compose, or Android. The renderer reads game state
and sends input back through a tiny API. Keeping this strict is what makes the
rules testable on the host and portable to iOS.

## UI ↔ core contract

The renderer ([ui/GameScreen.kt](../composeApp/src/commonMain/kotlin/com/example/futureconflicts/ui/GameScreen.kt))
drives the core with a minimal surface and otherwise only **reads** state:

- **Input:** `Battle.onTap(Pos)` and `Battle.endPlayerTurn()` (plus `restart()`).
- **Read:** `turn`, `day`, `winner`, `message`, `selected`, `reachable`, `targets`,
  `units`, `map`.
- **Redraw:** the Canvas depends on a `version` counter that the screen bumps after
  each interaction; `Battle` mutates in place. **If you add mutating state the UI
  must react to, bump `version` after the mutation.**

## Target architecture for scale (the important part)

The current `Battle.onTap(...)` interprets raw taps directly. That's fine for the
slice, but the vision (economy, production, fog, multiplayer) needs a firmer spine.
Refactor toward this as we add the economy:

1. **Actions as data.** Model every player intent as a serializable command:
   `Move(unit, path)`, `Attack(attacker, target)`, `BuildUnit(type, at)`,
   `UpgradeCity(pos)`, `EndTurn`, … The core exposes `apply(action): Result`.
   - The UI's job becomes: interpret taps → produce an `Action` → `apply` it.
   - The AI produces the *same* `Action`s.
   - Multiplayer becomes: serialize `Action`s over the wire and `apply` them on
     each peer. One funnel, three drivers.
2. **Deterministic simulation.** No wall-clock, no `Math.random()` in the core.
   All randomness (supply drops, any variance) draws from a **seeded RNG** stored
   in game state, so replays and networked peers stay in sync.
3. **Authoritative world vs per-player view.** The world holds ground truth; each
   player has a derived **fog/vision view**. Rendering uses the local player's
   view; the sim uses the world.
4. **Data-driven content.** Unit stats, terrain tables, commanders + their 3
   passives, city-upgrade curves, and supply-drop weights live as **data**
   (registries/definitions), separate from logic. Adding a commander or unit =
   adding data, not branching code. Keep raw PixelLab prompts/params next to the
   generated art so assets are reproducible.
5. **Stat pipeline.** Effective stat = base (unit type) + commander passives +
   terrain/veterancy modifiers, composed in one place so buffs stack predictably.

## Rendering & art pipeline (planned)

- **Sprites over primitives.** The slice draws colored rects + letter glyphs.
  Real art replaces `drawUnits`/`drawBoard` with sprite draws.
- **Runtime recolor:** each sprite carries a **team-color mask**; a shader/tint
  step recolors base art to the player's color. Author once per faction.
- **Battle scenes compose layers:** attacker anim + defender anim + terrain
  backdrop, drawn independently — additive content cost, not per-pair.
- Load assets per platform via `expect/actual` if needed; keep the *scene
  description* (who, where, which animation) in the pure core.

## Testing

- `commonTest` runs on the `jvm()` target: `./gradlew :composeApp:jvmTest`.
- Cover the core: combat math, movement/reachability, turn flow, victory, and
  (as they land) economy, fog, and action application.
- Prefer small hand-built `Scenario`s (see `TestSupport.kt`) over the full map so
  tests are precise and fast.

## Build gotchas

See [../CLAUDE.md](../CLAUDE.md) — bleeding-edge AGP 9.3.1 / Kotlin 2.2.10 toolchain
requires the two-module split and Compose-by-coordinate setup, and gradle output
must not be piped through `tail` when you need its exit code.

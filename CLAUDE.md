# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

**Future Conflicts** is a turn-based tactics game (an **Advance Wars** spinoff):
grid battles between the Blue Army and the Red Army. It's built with **Kotlin
Multiplatform + Compose Multiplatform** so the same code targets **Android now and
iOS later**. Sibling project to `stroads` (same toolchain and module structure).

Read [docs/VISION.md](docs/VISION.md) for the north-star design (Commanders,
economy, fog, art direction, multiplayer plan), [docs/DESIGN.md](docs/DESIGN.md)
for current mechanics, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for code
conventions, and [ROADMAP.md](ROADMAP.md) for the ordered feature plan.

**Backlog:** deferred polish, tech debt, and balance live in
[docs/BACKLOG.md](docs/BACKLOG.md). **When you cut a corner to ship a feature, add
a line there; when you fix one, delete it.** Keep it current — it's how we avoid
losing the "we'll clean this up later" items.

## Architecture

Two Gradle modules (this split is required by AGP 9 — a single-module KMP app does
not build; see "Build gotchas"):

- **`composeApp/`** — shared KMP library.
  - `commonMain/.../game/` — the game as **pure Kotlin**: `Battle` (turn flow,
    combat, AI, victory), `Movement` (Dijkstra range), `Combat` (damage), `Units`,
    `Terrain`, `GameMap`/`Scenarios`, `Model`. **No Compose or platform imports
    here** — keep it host-portable and testable.
  - `commonMain/.../ui/GameScreen.kt` — the *only* gameplay file that uses Compose:
    a `Canvas` renderer + `detectTapGestures` input + HUD/buttons.
  - `iosMain/` — `MainViewController` entry point for the iOS framework.
- **`androidApp/`** — thin Android host: `MainActivity` calls `App()`.
- **`iosApp/`** — Xcode project embedding the `ComposeApp` framework (build on a Mac).

**UI ↔ core contract:** the renderer drives `Battle` with just two verbs —
`onTap(Pos)` and `endPlayerTurn()` — and otherwise only *reads* its state
(`turn`, `day`, `winner`, `message`, `selected`, `reachable`, `targets`, `units`).
Recomposition is triggered by bumping a `version` counter after each interaction;
`Battle` is mutated in place. If you add state the renderer should react to, make
sure a `version++` follows the mutation.

## Build & run

Gradle needs a JDK on `PATH`; `java` isn't globally installed. Use the Android
Studio bundled JBR:

```bash
export JAVA_HOME="/home/kalieki/Downloads/android-studio-quail3-linux/android-studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
```

```bash
./gradlew :androidApp:assembleDebug   # build debug APK
./gradlew :androidApp:installDebug    # build + install on a device/emulator
./gradlew :composeApp:jvmTest         # host unit tests for game/ (no device needed)
```

**Tests** live in `composeApp/src/commonTest/.../game/` and run on the `jvm()`
target — pure-Kotlin, fast, device-free. The `jvm()` target exists *only* for
testing; the app ships from `androidLibrary` + iOS. Keep the core covered as it
grows (`TestSupport.kt` has helpers for building small deterministic scenarios).

**Gotcha:** don't pipe gradle into `tail`/`head` when you need the result — the
shell reports the pager's exit code, not gradle's. Redirect to a file and check
`$?`, or read `BUILD SUCCESSFUL/FAILED` from the log.

## Build gotchas (bleeding-edge toolchain)

AGP **9.3.1**, Kotlin **2.2.10**, Gradle **9.5**, only SDK platform **37**. The
config mirrors `stroads` and encodes three non-obvious fixes:

- **Two modules, not one.** `com.android.application` + `kotlin.multiplatform` in a
  single module fails on AGP 9. `androidApp` is the app; `composeApp` is the shared
  lib using the `kotlin { androidLibrary { ... } }` DSL (not `androidTarget()`).
- **No `org.jetbrains.compose` Gradle plugin** — it lags AGP 9.3.1's androidLibrary
  variant API. Apply only `org.jetbrains.kotlin.plugin.compose` and pull Compose in
  **by coordinate** (`org.jetbrains.compose.*:1.8.2`). No compose-resources /
  `@Preview` accessor as a result.
- Versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml); reference
  as `libs.*`.

## Conventions

- Keep `game/` free of Compose/Android imports (portability + host tests).
- Colors, glyphs, and layout live in `GameScreen.kt`'s `Palette`; game balance
  (stats, damage table, map) lives in `game/`.
- Sprite art: use the **PixelLab MCP** (`mcp__pixellab__*`) when adding real art.

## Pixel Forge agents

This repo has a **`.pixelforge.json`** descriptor at its root that lets a set of reusable,
user-level game-dev agents adapt to it automatically. Roster + how the system works:
`~/.claude/pixelforge/README.md`.

- **Agents** (Agent tool): `game-test-smith` (grow the `game/` tests), `balance-simulator`
  (headless AI/scenario sweeps over the stat/economy/commander constants), `content-forge`
  (new maps/commanders, validated via `Movement.reachable`), `doc-sync` (reconcile docs↔code —
  e.g. the stale "38 tests"), `sprite-wiring` (wire the staged `art/` sprites into `GameScreen.kt`),
  `pixel-sprite-smith` (art generation).
- **Commands:** `/brainstorm` (design partner over VISION + open questions), `/groom` (shape a
  ROADMAP item), `/build-run` (host tests / build with the JBR handled).
- Agents read `.pixelforge.json` first, so keep it honest — run `doc-sync` when code drifts from it.
- Batch-1 sprites are staged in `art/` but **not yet wired** (renderer still draws primitives/glyphs).
  The PixelLab MCP is currently configured only for the Rogueshapes repo — add its server block here
  before generating more art.

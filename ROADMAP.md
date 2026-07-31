# Future Conflicts — Roadmap

Tracked in plain git (like the sibling `stroads` project). Newest work at the top
of "Done".

## Done — vertical slice (playable)

- KMP + Compose Multiplatform scaffold on the AGP 9.3.1 / Kotlin 2.2.10 toolchain
  (two-module split: `androidApp` + shared `composeApp`; iOS targets wired).
- Pure-Kotlin tactics core (`game/`), no Compose/platform imports:
  - Terrain with move cost + defense; hand-authored "Twin Ridges" map.
  - 5 unit types (Infantry, Mech, Recon, Tank, Artillery) with stats + a full
    damage matchup table; Advance-Wars-style damage & counterattack math.
  - Dijkstra movement range; select → move → attack/wait turn flow.
  - Greedy enemy AI; win/lose on army elimination; Restart.
- Compose `Canvas` renderer + tap input, End Turn / Restart buttons, HUD.
- Builds a debug APK (`./gradlew :androidApp:assembleDebug`).

## Next

1. **Host unit tests** for the core (`commonTest` + kotlin.test): damage math,
   movement blocking, counterattack rules, victory detection.
2. **Enemy-turn feedback** — animate/step enemy actions instead of resolving them
   instantly; brief attack flashes.
3. **Bases & economy** — capturable Cities/HQ, funds per turn, unit production;
   **capture the HQ** as an alternate win condition.
4. **Post-move Wait/Attack menu** (explicit) + a **Cancel** to undo a move before
   committing an action.
5. **Fog of war** and vision per unit.
6. **Content** — more maps, a map format/loader, and PixelLab sprite art for units
   and terrain tiles (see the PixelLab MCP; used on `stroads`).
7. **Commanders (COs)** with passive buffs and a power meter.

## Known simplifications (slice)

- Enemy turn resolves instantly (no animation).
- No production/economy yet; armies are pre-placed.
- One built-in map; no loader.
- Terrain move cost is uniform across unit types (no treads-vs-tires movement
  tables yet).

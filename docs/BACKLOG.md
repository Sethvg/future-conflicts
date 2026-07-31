# Future Conflicts — Backlog (polish, tech debt, balance)

Deferred work we consciously punted while building features, kept here so nothing
gets lost. This is the catch-all for **polish, known simplifications, tech debt,
and balance** — the ordered *feature* plan lives in [../ROADMAP.md](../ROADMAP.md).

Convention: when you cut a corner to ship a feature, add it here (one line, with
enough context to act on later). When you fix one, delete it (git remembers).
Tags: `[ai]` `[ux]` `[fog]` `[econ]` `[arch]` `[art]` `[balance]` `[test]`.

## Enemy AI
- `[ai]` AI **sees through fog** — it reads true unit positions, ignoring vision.
  Give it a fog-limited view (or an explicit "cheating" difficulty toggle).
- `[ai]` AI has **no economy**: it never builds from HQ, upgrades cities, or fields
  a Commander/Elite. It only moves, attacks, and captures reachable buildings.
- `[ai]` Greedy & per-unit: no target prioritization beyond damage/kill, no
  retreat/regroup, no coordination between units.

## Game feel / UX
- `[ux]` **Enemy turn resolves instantly** — no animation or step-through; hard to
  follow what the Red Army did. Animate moves/attacks (or a per-action log).
- `[ux]` **Fuel-crash / air-loss messages get clobbered** by the day / supply-drop /
  "Red Army finished its turn" message, so the player may never see aircraft they lost
  to fuel exhaustion. Accumulate turn events instead of overwriting `message`.
- `[ux]` **No damage preview** before committing an attack (show predicted dmg +
  counter when a target is highlighted).
- `[ux]` **No undo of a committed move** — Cancel only works before choosing the
  action; once Attack/Capture/Wait applies there's no take-back.
- `[ux]` No attack flashes / SFX / haptics; no proper end-of-game screen (just a
  message + Restart).
- `[ux]` Commander-select has no "change commander" entry mid-game (only via
  Restart, which also keeps the same commanders now).

## Fog of war
- `[fog]` Fog is **view-only**: targeting and movement use true positions, so an
  unseen forest unit can be shelled by artillery and invisible enemies still block
  a path (minor info leak). Consider gating targets/path on visibility.
- `[fog]` No **ambush**: moving into/next to a hidden unit doesn't interrupt the
  move.
- `[fog]` No "explored vs currently-visible" memory layer (we show terrain under
  fog but don't distinguish remembered terrain from live sight).

## Economy / capture
- `[econ]` **Partial capture persists** even if the capturing unit leaves or dies
  (AW resets it). Track the capturer and reset `captureLeft` on interruption.
- `[econ]` No save/serialize of game state; needed for persistence + net sync.

## Supply drops
- `[ux]` The **enemy's** supply-drop message is immediately overwritten by "Red Army
  finished its turn." — the player never sees when the AI got a drop.
- `[ux]` No supply-drop toast / animation / sound; only the HUD message + `lastSupplyKind`.
- `[fog]` A REVEAL boon stays active until that side's *next* turn (~1.5 turns of map
  reveal) rather than expiring at end of the current turn.
- `[ai]` The AI doesn't factor its own supply cadence into planning (e.g. timing a push
  to line up with a reinforcement drop).
- `[arch]` The supply RNG **state** isn't serialized — only the seed. Replay relies on
  re-applying the recorded `Action.SupplyDrop`s in order (fine until live netcode).

## Architecture
- `[arch]` **`internal` state is module-wide.** Splitting `Battle` into extension files
  widened its state from `private` to `internal`, and the renderer lives in the *same*
  module — so `GameScreen` could now mutate state directly and bypass the `apply(Action)`
  funnel + `version++`. Enforced by convention only until the `:core` module lands
  (below), which makes `internal` genuinely core-private. Raised by `code-auditor`.
- `[arch]` **Extract a compose-free `:core` module** for the game logic (currently
  in `composeApp/.../game/`). Enforces the "core stays pure" rule at build level and
  speeds core tests. See [ARCHITECTURE.md](ARCHITECTURE.md) "Planned".
- `[arch]` Actions reference units by tile `Pos`. Fine for local/hot-seat; revisit
  (stable unit ids) if simultaneous-turn netcode needs it.
- `[arch]` No `Action`/state **serialization** yet (needed for replay, save,
  online). Seeded RNG lands with supply drops.

## Content / art
- `[art]` **Sprite loading is base64-embedded** in `SpriteData.kt` (no compose-resources
  plugin on this toolchain) — fine for Batch 1, but migrate to a real resource pipeline
  before the animation/faction batches bloat the source.
- `[art]` **Unit team-tint reads too dark** — `ColorFilter.tint(teamColor, Modulate)` on
  neutral-gray sprites murks them; tune the blend (lighter team colors) or add a
  team-colored base disc so blue-vs-red pops.
- `[art]` Terrain + unit tokens are wired, but **buildings/HQ/city still draw as
  border+label over the terrain tile** — no dedicated building sprites yet.
- `[art]` Rendering was **colored rects + letter glyphs** — now sprites for units +
  terrain; still pending: **team-color polish**, per-faction skins, **composed
  battle-animation scenes**, portraits, UI icons.
- `[art]` Per-faction **unique unit skins**; Elite units currently differ only by
  stats + a gold outline (no unique art).
- `[art]` Only one hand-authored map ("Twin Ridges") — no map format / loader /
  editor; no multiple scenarios.
- `[art]` No `VISION` commander-passive kind yet (possible future faction trait).

## Balance (all provisional, untuned)
- `[balance]` Economy numbers: starting gold 5000, HQ/city income 100/level, upgrade
  1000, city max L3 — pulled from thin air, needs a real pass.
- `[balance]` Commander passive magnitudes, rebuy multipliers, Elite cost/bonuses,
  unit costs, and the damage matchup table are all first-guess values.
- `[balance]` Supply-drop tuning is provisional: interval 7, gold windfall 3000, heal
  +3 HP, reinforce = Infantry, draw weights 4/3/2/1.
- `[balance]` Movement is uniform across unit types (no treads/tires/foot/air
  movement classes or per-terrain cost tables); no air/sea units.

## Testing
- `[test]` Core is well-covered; **no UI/renderer tests** and no instrumented
  (device) or iOS tests wired up.

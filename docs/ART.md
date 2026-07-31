# Future Conflicts — Art Production (pixel work tracker)

The master list of **pixel art remaining**, the **locked style guide** every asset
must follow, and the **ordered batch plan** we kick off (via PixelLab-driven
agents). North star art direction lives in [VISION.md](VISION.md); this doc is the
production tracker. **Convention:** when a batch completes, update its rows in
*Status* to `done` with the file paths; when we decide new art is needed, add a row.

Generated assets are **staged** in the repo under `art/` (source of truth). Wiring
them into the renderer is a separate step — see *Integration* below.

---

## Style guide (LOCKED — every asset follows this)

- **Direction:** gritty, detailed, *readable* military pixel art. Weathered, used
  hardware — not clean/cute. Silhouette reads at a glance on a small grid tile.
- **Palette:** muted, desaturated — olive drab, gunmetal grey, rust, ash, dark
  steel. Team color is applied *later* (see recolor pipeline); base assets are
  **neutral/faction-agnostic** so we can judge form first.
- **Light:** single light source, top-left. Consistent across every asset.
- **Grid:** logical tile is square; art authored at **64×64** (units and tiles),
  scaled by the renderer.

### Production pipeline

**Units & animated sprites go through the `pixel-sprite-smith` agent**
(`~/.claude/agents/pixel-sprite-smith.md`) — one agent per subject. It uses the
**high-quality** PixelLab paths (`create_1_direction_object` → pick best candidate
→ optional `animate_object`; or `create_character` v3 for rotatable), downloads +
verifies frames, and returns a wiring manifest. **Colorizable units are authored
neutral desaturated gray** so the render layer can HSV-tint each into blue/red +
faction color (per the standing PixelLab quality preference — Tier 3, favor quality).
Terrain tiles are not the smith's domain and use `create_image_pixflux` directly.

### PixelLab presets (per asset class)

| Asset class      | Path / Tool                          | Size   | view           | bg      | notes |
|------------------|--------------------------------------|--------|----------------|---------|-------|
| Unit map token   | `pixel-sprite-smith` (`create_1_direction_object`) | 80 | `low top-down` | transp. | **neutral gray, colorizable**; base sprite this pass, animate later |
| Terrain tile     | `create_image_pixflux`               | 64×64  | `high top-down`| opaque  | `highly detailed`, `detailed shading`, `selective outline` |
| Commander portrait | `create_portrait_character`        | —      | `side`         | —       | Batch 2 |
| Battle-scene sprite | `pixel-sprite-smith` (`create_character` v3) | 96–128 | `side` | transp. | Batch 4; attacker/defender poses |

- Set `no_background=true` for tokens/sprites, `false` for terrain tiles.
- **Fix a `seed` per asset** and record it in the batch manifest so regens are
  reproducible (matches the project's determinism ethos).
- Prompt skeleton: `"gritty weathered military <thing>, <specifics>, muted
  desaturated olive-and-gunmetal palette, detailed pixel art, top-left light"`.

### Output & naming

```
art/
  units/     infantry.png mech.png recon.png tank.png artillery.png commander.png
  terrain/   plains.png road.png forest.png mountain.png city.png hq.png sea.png
  portraits/ vale.png krause.png okonkwo.png
  battle/    <unit>_attack.png <unit>_hit.png  + backdrops/<terrain>.png
  ui/        crest_storm.png crest_iron.png crest_siege.png icon_gold.png ...
  <batch>/manifest.md   # asset -> job_id, seed, prompt, download URL
```

---

## Asset inventory (pixel work remaining)

Full eventual need. `[ ]` = not started, `[~]` = generating, `[x]` = staged.

**Unit map tokens (base, neutral)** — `[ ]` infantry `[ ]` mech `[ ]` recon
`[ ]` tank `[ ]` artillery `[ ]` commander

**Terrain tiles** — `[ ]` plains `[ ]` road `[ ]` forest `[ ]` mountain
`[ ]` city `[ ]` hq `[ ]` sea

**Faction unit skins** (unique per Storm / Iron / Siege, ×6 units) — `[ ]`
*(depends on recolor pipeline; see Batch 3)*

**Team recolor** — blue (player) / red (enemy) via team-color mask — `[ ]`

**Commander portraits** — `[ ]` Vale (Storm) `[ ]` Krause (Iron) `[ ]` Okonkwo (Siege)

**Battle-scene layers** (composed attacker + defender + backdrop, additive N+M) —
`[ ]` per-unit attack pose `[ ]` per-unit hit/recoil pose `[ ]` per-terrain backdrop
`[ ]` muzzle/impact FX

**UI / FX** — `[ ]` faction crests ×3 `[ ]` gold/capture/HP icons `[ ]` selection
& move-range highlight `[ ]` fog texture `[ ]` explosion / capture-progress FX

**Terrain polish** — `[ ]` seamless Wang tilesets (`create_topdown_tileset`)
`[ ]` city level 1–3 variants `[ ]` owner tint on city/HQ `[ ]` road connectors

---

## Batch plan (ordered — kick off with agents)

Each batch is a self-contained agent job: generate with the locked presets, poll
to completion, `curl` the download URL into `art/…`, write a `manifest.md`.

- **Batch 1 — Style-defining first pass** *(in progress)*: 6 base unit tokens +
  7 terrain tiles, neutral palette. Goal: lock the look before scaling up.
- **Batch 2 — Identity**: 3 commander portraits + 3 faction crests + core UI icons.
- **Batch 3 — Recolor pipeline**: decide mask-vs-per-color; produce blue/red team
  variants + per-faction unit skins.
- **Batch 4 — Battle scenes**: attacker/defender sprites + terrain backdrops for the
  composed attack animation.
- **Batch 5 — Terrain polish & FX**: seamless Wang tilesets, city levels, owner
  tints, explosion/capture FX.

---

## Integration (separate task — not part of generation)

The build has **no compose-resources plugin** (see [../CLAUDE.md](../CLAUDE.md)), so
`Res.drawable` / `painterResource` are unavailable. Wiring staged PNGs into the
`Canvas` renderer will need a small platform bridge: an `expect/actual`
`loadImageBitmap(name)` (Android decodes via `BitmapFactory`; iOS/skiko via
`org.jetbrains.skia.Image`), with PNGs shipped as platform resources. Tracked as a
ROADMAP art sub-item; `GameScreen` keeps drawing rects/glyphs until that lands.

---

## Status

| Batch | Assets | State | Notes |
|-------|--------|-------|-------|
| 1 (preview) | 6 unit tokens + 7 terrain tiles | staged in `art/` | quick `pixflux` pass — **colored, not colorizable**; use to lock style |
| 1 (production) | 6 unit tokens via `pixel-sprite-smith` | pending | neutral-gray colorizable; fixes the 3 flags below |
| 2–5 | see plan | not started | |

### Batch 1 review notes
- **Style direction approved-in-principle**: gritty weathered olive/gunmetal reads well; infantry & tank land instantly.
- **commander** — reads as a re-skinned tank; needs a distinct silhouette (command APC / different chassis).
- **artillery** — has a ground-shadow ellipse baked into the sprite (not transparent) + reads tank-ish.
- **mountain** tile — too dark & blue-tinted (reads like water); re-roll lighter grey.
- Preview units are **fully colored**, so they can't be team-tinted — production run must be **neutral gray** (colorizable) via the smith.

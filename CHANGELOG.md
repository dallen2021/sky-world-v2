# Changelog

All notable changes to Sky World follow this file. Format roughly follows
[Keep a Changelog](https://keepachangelog.com/); versions before 1.0.0 were
development-only and not released.

## [Unreleased]

## [1.4.0] — 2026-07-31

### Island silhouette revision
- Reduce common continental footprints to roughly 1,100–1,600 blocks across,
  with spawn continents constrained to the smaller end of that range.
- Replace each circular ellipse with a deterministic union of connected,
  rotated lobes and stronger harmonic coastline variation.
- Move the underside shoulder from Y=112 to Y=160 and replace uniform radial
  shrinking with several staggered keel fields per component, producing
  offset inverted peaks and shallower edge shelves without detached terrain.
- Add public per-archetype lobe-count, aspect-ratio, and keel-depth controls to
  `sky_islands.json`; older overrides decode with compatible defaults.

### Structure safety
- Validate structure-piece bases against the mapped island envelope before a
  start is installed in the chunk, rejecting mineshafts, villages, and modded
  starts whose representative pieces would hang in the void.
- Keep validation bounded to at most 1,080 density samples even for extremely
  large modded starts. Preserve weighted fallback for biome-invalid entries,
  but stop immediately at envelope-invalid locations instead of constructing
  every alternative structure in the same void chunk.
- Cap IDAS-only `/locate` searches to eight placement rings in Sky World so a
  rare structure reports that none is nearby instead of tripping the server
  watchdog. Other structure namespaces and mixed searches retain their normal
  radius.

## [1.3.0] — 2026-07-31

### Continental Sky Islands v2
- Replace scalable End base noise with deterministic, seed-dependent
  continental, medium, archipelago, and sparse island groups.
- Intersect shifted active Overworld terrain with a connected island envelope
  in both initial and final density, preserving Terralith, OTBWG, structures,
  ores, surfaces, and biome-owned carvers inside the islands.
- Add genuine inter-group void, continuously tapered undersides, and hard
  vertical bounds without independent lower shelves.
- Add a bounded thread-safe cell cache and interpolate the decoded envelope.

### Caves and lakes
- Expose cave-biome depth at approximately 2% of exterior-boundary samples,
  with wet climates receiving at least 60% of accepted samples.
- Preserve existing Sky World glow lichen and hanging-vine decoration without
  globally stamping lush or dripstone features onto island undersides.
- Add deterministic lakes to continental and medium groups with complete
  shoreline, 32-block safety-margin, and 48-block depth validation.

### Compatibility and customization
- Keep Sky World as an explicit preset and leave `Default` untouched.
- Add optional generator field `surface_shift` with a backward-compatible
  default of 96.
- Publish island shape, spacing, taper, and archetype settings in
  `sky_islands.json`; document that fresh worlds are required for testing.
- Retain Integrated Stronghold surface projection and the structure-locate
  compatibility behavior introduced in 1.2.0.

## [1.2.0] — 2026-07-31

### Biome-pack compatibility
- Add a Sky World chunk-generator codec that merges the active
  `minecraft:overworld` settings with Sky World's floating-island terrain.
- Inherit Terralith's or another biome pack's climate fields, surface rules,
  spawn targets, stone type, and ore-noise settings without replacing the
  island density, air fluid, sea level, or aquifer behavior.
- Derive the biome-source depth signal from the signed island density so
  surface biomes remain on exposed island faces while cave biomes are selected
  inside sufficiently thick islands.
- Preserve TerraBlender's OTBWG region and surface-rule injection because the
  compatibility generator remains a `NoiseBasedChunkGenerator`.

### Validation
- Add NeoForge-loaded JUnit coverage for generator registration, settings
  merging, Sky World identity, and island-relative cave depth.
- Validate a fresh AetherCraft-profile world with Terralith, OTBWG,
  TerraBlender, Lithostitched, Integrated Stronghold, Integrated Villages, and
  IDAS.

## [1.1.0] — 2026-07-30

### World preset
- Add the selectable `Sky World` preset (`sky_world:sky_world`).
- Stop overriding `minecraft:overworld`; the `Default` preset now keeps normal
  terrain.
- Add a namespaced `sky_world:overworld` noise setting and scalable,
  Exosphere-inspired island density.

### Terrain and decoration
- Use the active `minecraft:overworld` multi-noise biome source so Terralith
  biomes and biome features can populate the islands.
- Keep native cave carvers and biome-owned underground decoration.
- Add moderate glow lichen to Sky World only.
- Add hanging glow-berry vines only to lush, wet, jungle, and swamp biome
  groups.
- Add a generator-aware placement modifier so these decorations are no-ops in
  normal Overworlds.

### Compatibility
- Remove the global Isekai worldshape, biome modifier, structure modifier, and
  dimension-wide Lithostitched density wrapper.
- Retain the targeted Integrated Stronghold surface projection.
- Validate fresh Sky World and Default worlds with Terralith, Lithostitched,
  Integrated Villages, Integrated Stronghold, IDAS, and Sky Villages.

## [1.0.0] — 2026-05-28

First public release. Datapack-only Aether-style floating-island overworld
built on Isekai API.

### World transformation
- `data/minecraft/worldgen/noise_settings/overworld.json` — overlay wrapping
  vanilla `final_density` in Isekai's `mask_y_range` (Y=120..220 inside =
  vanilla terrain, outside = `constant -1.0` = void).
- `data/sky_world/neoforge/biome_modifier/apply_sky.json` — Isekai
  `apply_worldshape` biome modifier:
  - `playable_range` Y=120..220
  - `ore_strategy: linear` — every ore's Y band linearly remaps into the
    island volume
  - `default_structure_predicate` requires `y_in_range(120, 220)` AND
    `solid_floor(clearance=2)` so structures only spawn on viable platforms
  - per-structure `never` predicates for `ocean_monument`, all `shipwreck`
    variants, `ocean_ruin_*`, `buried_treasure`, `ancient_city`
  - 35 surface land biomes in `applies_to` (oceans / caves / void excluded)
  - all four overworld carvers excluded (no caves inside islands)
  - atmosphere tint: sky color #8FCFC7, fog color #CCB0D9
- `data/sky_world/neoforge/structure_modifier/apply_sky.json` — Isekai
  `apply_worldshape_structures` modifier clears biome filters for the
  ocean / ancient-city structures so they never attempt placement.

### Java surface
- Single `SkyWorld.java` `@Mod` entry. Smoke-tests the Isekai facade is
  reachable; no other Java code.

### Known limitations
- New worlds only — already-generated chunks keep their vanilla terrain.
- Conflicts with any mod that also overlays
  `data/minecraft/worldgen/noise_settings/overworld.json` (Terralith, BYG,
  etc.). Run one or the other.
- Spawn position is computed by vanilla and may land inside an island block;
  the player typically respawns or you teleport out.
- Strongholds may still attempt placement near void edges; the spatial
  predicate gates them but doesn't always succeed at finding a valid spot.

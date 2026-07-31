# Sky World

Sky World adds a selectable NeoForge 1.21.1 world preset made of large
floating islands over the void. It keeps the normal `Default` preset intact
and merges the active Overworld climate and surface rules into the island
generator, so Terralith, OTBWG, and biome-owned cave features can populate the
islands in the appropriate places.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![NeoForge 1.21.1](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net)
[![Depends on Isekai API](https://img.shields.io/badge/Depends-Isekai%20API-9333ea)](https://github.com/KURONAMI333/isekai-api)

## Creating a world

1. Install Sky World and Isekai API.
2. Select **Sky World** in the World Type control when creating a world.
3. Create a new world. Worldgen changes only affect newly generated chunks.

Create a new Sky World after upgrading from 1.1.0. Worlds first created with
1.1.0 serialized Minecraft's normal noise-generator type and do not
automatically switch to the new compatibility generator.

On a dedicated server, use:

```properties
level-type=sky_world\:sky_world
```

Leaving the world type on `Default` produces normal terrain.

## Terrain and caves

The Sky World generator divides the world into seed-dependent island groups
and intersects each connected floating-island envelope with the active
Overworld terrain. It reads the active `minecraft:overworld` settings at world
creation and combines them with `sky_world:overworld`. That means:

- Terralith's climate fields and surface rules remain active when Terralith is
  loaded.
- OTBWG's TerraBlender regions and surface rules are applied after the merge.
- Active Overworld terrain is shifted upward by 96 blocks, then clipped in
  both initial and final density so heightmaps, structures, and terrain agree.
- Continental groups dominate, with medium groups, close archipelagos, and
  sparse small-island groups providing variation. Spawn continents are
  typically about 1,050–1,500 blocks across, while ordinary crossings target
  roughly 200–800 blocks of true void.
- Continents use connected, rotated lobes and harmonic coastline warping
  instead of one circular ellipse. Their underside taper begins at Y=160 and
  follows several staggered keel fields down toward the hard bottom at Y=-56;
  this produces offset inverted peaks without detached lower shelves. The
  hard top remains Y=304.
- Approximately 2% of exterior-boundary samples expose cave-biome depth, with
  a strong wet-climate bias so lush caves are noticeable without dominating.
- Cave carving and biome-specific underground decoration stay owned by the
  selected biome pack.
- Lush vegetation and dripstone are not stamped under every island.
- Glow lichen is added throughout Sky World, while hanging glow-berry vines
  are limited to lush, wet, jungle, and swamp biome groups.
- The extra lighting and vines are generator-gated and never run in a
  `Default` world.

Sky World intentionally keeps its own final density, so it retains floating
islands rather than reproducing Terralith's continental terrain shapes.
Continental and medium groups may also receive contained lakes. Every lake
validates its full shoreline, a 32-block safety ring, and 48 blocks of island
depth before carving; failed checks leave terrain untouched instead of making
waterfalls into the void. Rivers are not generated in 1.4.0.

## Customization

The main tuning resources are:

- `data/sky_world/worldgen/density_function/sky_islands.json`
  - `cell_size` and `center_jitter`: macro-cell spacing and seed-dependent
    group-center variation.
  - `shoulder_y`, `bottom_y`, and `top_y`: where underside tapering begins and
    the hard island limits.
  - `edge_warp` and `underside_variation`: boundary and underside roughness.
  - `normalization_scale`: signed-distance scaling used by the terrain and
    cave-boundary calculations.
  - `continental`, `medium`, `archipelago`, and `small`: weights, component
    counts, radii, internal gaps, and total group-radius ranges.
  - Each archetype's `min_lobes` / `max_lobes` and `min_aspect` / `max_aspect`:
    connected footprint complexity and elongation.
  - Each archetype's `min_keel_depth` / `max_keel_depth`: the range of
    staggered underside peak depths.
- `data/sky_world/worldgen/world_preset/sky_world.json`
  - `surface_shift`: upward shift applied to the active Overworld terrain;
    defaults to `96` when absent so older serialized generators still decode.
- `data/sky_world/worldgen/placed_feature/hanging_glow_lichen.json`
  - `count.min_inclusive` and `count.max_inclusive`: glow-lichen frequency.
- `data/sky_world/worldgen/placed_feature/hanging_cave_vines.json`
  - count range and `environment_scan.max_steps`: hanging-vine frequency and
    maximum search distance below an island ceiling.
- `data/sky_world/tags/worldgen/biome/supports_hanging_vines.json`
  - biome groups that receive glow-berry vines.

These resources can be overridden by a higher-priority datapack. Existing
chunks never change, and newly generated 1.4.0 chunks will not blend cleanly
into the old End-noise shape, so use fresh worlds for terrain validation.

## Compatibility

The compatibility test stack currently covers:

- Terralith 2.6.2
- Oh The Biomes We've Gone 2.6.0
- TerraBlender 4.1.0.8
- Lithostitched 1.7.13
- Integrated Villages 1.3.3
- Integrated Stronghold 1.1.4
- Integrated Dungeons and Structures 1.13.7
- Sky Villages 1.0.6

Sky World no longer installs a dimension-wide Isekai structure predicate.
This avoids the unbounded structure-surface search that previously caused
`/locate` to stall for Integrated structures. The Integrated Stronghold
resource still projects its start to `WORLD_SURFACE_WG`.

Sky World 1.4.0 additionally validates a bounded sample of every generated
structure start against the island envelope. A start with representative
pieces hanging in the void is rejected before pieces and terrain-adjustment
blobs generate. The validator has a hard density-sample budget and never uses
an unbounded surface search. Mods that intentionally place free-floating sky
structures may need a future opt-out tag.

## Building

```powershell
.\gradlew.bat build
```

The built mod is `build/libs/sky_world-1.4.0.jar`.

## License

[MIT License](LICENSE)

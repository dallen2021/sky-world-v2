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

The Sky World generator uses an Exosphere-inspired floating-island silhouette
with horizontally scalable End base noise. It reads the active
`minecraft:overworld` settings at world creation and combines them with
`sky_world:overworld`. That means:

- Terralith's climate fields and surface rules remain active when Terralith is
  loaded.
- OTBWG's TerraBlender regions and surface rules are applied after the merge.
- Biome depth follows the signed island boundary: exposed surfaces receive
  surface biomes, while lush and dripstone cave biomes are reserved for island
  interiors.
- Cave carving and biome-specific underground decoration stay owned by the
  selected biome pack.
- Lush vegetation and dripstone are not stamped under every island.
- Glow lichen is added throughout Sky World, while hanging glow-berry vines
  are limited to lush, wet, jungle, and swamp biome groups.
- The extra lighting and vines are generator-gated and never run in a
  `Default` world.

Sky World intentionally keeps its own final density, so it retains floating
islands rather than reproducing Terralith's continental terrain shapes.

## Customization

The main tuning resources are:

- `data/sky_world/worldgen/density_function/sky_islands.json`
  - `sx` and `sz`: horizontal island size. Larger values make broader islands.
  - the `-0.234375` density offset: island coverage. Less negative values
    produce more land; more negative values produce more void.
  - the two `y_clamped_gradient` ranges: bottom and top island taper.
- `data/sky_world/worldgen/placed_feature/hanging_glow_lichen.json`
  - `count.min_inclusive` and `count.max_inclusive`: glow-lichen frequency.
- `data/sky_world/worldgen/placed_feature/hanging_cave_vines.json`
  - count range and `environment_scan.max_steps`: hanging-vine frequency and
    maximum search distance below an island ceiling.
- `data/sky_world/tags/worldgen/biome/supports_hanging_vines.json`
  - biome groups that receive glow-berry vines.

These resources can be overridden by a higher-priority datapack. Test major
density changes in a new world.

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

Structure placement can still look wrong when another mod hard-codes an
absolute Y coordinate or assumes a continuous ground plane. Those cases need
targeted structure compatibility rather than a global remap.

## Building

```powershell
.\gradlew.bat build
```

The built mod is `build/libs/sky_world-1.2.0.jar`.

## License

[MIT License](LICENSE)

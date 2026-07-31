# Sky World

Sky World adds a selectable NeoForge 1.21.1 world preset made of large
floating islands over the void. It keeps the normal `Default` preset intact
and uses the active Overworld biome source, so Terralith biomes and
biome-owned cave features can populate the islands.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![NeoForge 1.21.1](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net)
[![Depends on Isekai API](https://img.shields.io/badge/Depends-Isekai%20API-9333ea)](https://github.com/KURONAMI333/isekai-api)

## Creating a world

1. Install Sky World and Isekai API.
2. Select **Sky World** in the World Type control when creating a world.
3. Create a new world. Worldgen changes only affect newly generated chunks.

On a dedicated server, use:

```properties
level-type=sky_world\:sky_world
```

Leaving the world type on `Default` produces normal terrain.

## Terrain and caves

`sky_world:overworld` uses an Exosphere-inspired floating-island silhouette
with horizontally scalable End base noise. It deliberately does not replace
biome JSON, cave carvers, or the global `minecraft:overworld` noise settings.
That means:

- Terralith's Overworld biome source remains active when Terralith is loaded.
- Cave carving and biome-specific underground decoration stay owned by the
  selected biome pack.
- Lush vegetation and dripstone are not stamped under every island.
- Glow lichen is added throughout Sky World, while hanging glow-berry vines
  are limited to lush, wet, jungle, and swamp biome groups.
- The extra lighting and vines are generator-gated and never run in a
  `Default` world.

Terralith biomes and features are retained. Sky World currently owns the
noise-settings surface-rule tree, so this is not a byte-for-byte reproduction
of every Terralith terrain shape or surface-rule detail.

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

The built mod is `build/libs/sky_world-1.1.0.jar`.

## License

[MIT License](LICENSE)

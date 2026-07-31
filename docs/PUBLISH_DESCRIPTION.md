# Sky World — Modrinth / CurseForge publish description

> Categories: **worldgen** (primary) + **adventure** (secondary).
> Loader: **NeoForge**. Game version: **1.21.1**. Side: **both**.
> **Requires Isekai API.**

---

**The overworld becomes a sea of continental floating islands.** Explore
broad, biome-rich surfaces and staggered mountain-like undersides separated by
true void.

Sky World adds an explicit **Sky World** world preset. It clips the active
Overworld terrain inside connected island envelopes, preserving vanilla,
Terralith, and OTBWG surfaces and cave decoration while leaving `Default`
worlds untouched.

## Features

- **Irregular continents** — connected multi-lobe coastlines instead of
  circular discs, with typical spawn continents around 1,050–1,500 blocks.
- **Mountain-like undersides** — several staggered inverted keel peaks taper
  from Y=160 toward a hard floor at Y=-56.
- **Real travel gaps** — ordinary island-group crossings target about
  200–800 blocks of void; archipelago members remain much closer.
- **Biome-pack compatibility** — active climate, surfaces, ores, carvers, and
  biome features remain available inside the islands.
- **Bounded structure safety** — detached structure starts are rejected
  without unbounded surface searches; IDAS-only locate commands use a safe
  nearby-search cap rather than risking a watchdog stall.
- **Contained lakes and cave exposure** — occasional safe lakes plus sparse,
  wet-biased exposed cave biomes, glow lichen, and hanging vines.

## Requirements

| Mod | Required? |
|---|---|
| **Isekai API** | **Required** — supplies the density-function translation used by the compatibility generator |

## Compatibility

- Designed for NeoForge 1.21.1 with Terralith, OTBWG, TerraBlender,
  Lithostitched, Integrated Strongholds, Integrated Villages, and IDAS.
- Select **Sky World** when creating a fresh world. Leaving the preset on
  **Default** keeps normal generation.
- Existing chunks do not change, and 1.4.0 terrain will not blend cleanly into
  older Sky World terrain. Use a fresh world when updating.

## License and links

MIT. Source and issues: https://github.com/KURONAMI333/sky-world

Built on [Isekai API](https://modrinth.com/mod/isekai-api).

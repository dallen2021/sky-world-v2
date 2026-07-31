package com.kuronami.skyworld.worldgen;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

final class SkyWorldNoiseSettings {
    private SkyWorldNoiseSettings() {
    }

    static NoiseGeneratorSettings merge(
            NoiseGeneratorSettings activeOverworld,
            NoiseGeneratorSettings skyTerrain
    ) {
        NoiseRouter activeRouter = activeOverworld.noiseRouter();
        NoiseRouter skyRouter = skyTerrain.noiseRouter();
        NoiseRouter mergedRouter = new NoiseRouter(
                activeRouter.barrierNoise(),
                activeRouter.fluidLevelFloodednessNoise(),
                activeRouter.fluidLevelSpreadNoise(),
                activeRouter.lavaNoise(),
                activeRouter.temperature(),
                activeRouter.vegetation(),
                activeRouter.continents(),
                activeRouter.erosion(),
                skyRouter.finalDensity().clamp(-0.005, 1.0),
                activeRouter.ridges(),
                skyRouter.initialDensityWithoutJaggedness(),
                skyRouter.finalDensity(),
                activeRouter.veinToggle(),
                activeRouter.veinRidged(),
                activeRouter.veinGap()
        );

        return new NoiseGeneratorSettings(
                skyTerrain.noiseSettings(),
                activeOverworld.defaultBlock(),
                skyTerrain.defaultFluid(),
                mergedRouter,
                activeOverworld.surfaceRule(),
                activeOverworld.spawnTarget(),
                skyTerrain.seaLevel(),
                skyTerrain.disableMobGeneration(),
                skyTerrain.isAquifersEnabled(),
                activeOverworld.oreVeinsEnabled(),
                activeOverworld.useLegacyRandomSource()
        );
    }
}

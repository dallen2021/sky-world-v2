package com.kuronami.skyworld.worldgen;

import com.kuronami.isekaiapi.densityfunction.TranslateDF;
import com.kuronami.skyworld.worldgen.density.CaveBiomeDepthDensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

final class SkyWorldNoiseSettings {
    private SkyWorldNoiseSettings() {
    }

    static NoiseGeneratorSettings merge(
            NoiseGeneratorSettings activeOverworld,
            NoiseGeneratorSettings skyTerrain,
            int surfaceShift
    ) {
        NoiseRouter activeRouter = activeOverworld.noiseRouter();
        NoiseRouter skyRouter = skyTerrain.noiseRouter();
        DensityFunction islandEnvelope = skyRouter.finalDensity();
        DensityFunction shiftedInitialDensity = new TranslateDF(
                activeRouter.initialDensityWithoutJaggedness(),
                0.0,
                surfaceShift,
                0.0
        );
        DensityFunction shiftedFinalDensity = new TranslateDF(
                activeRouter.finalDensity(),
                0.0,
                surfaceShift,
                0.0
        );
        DensityFunction combinedInitialDensity = DensityFunctions.min(
                shiftedInitialDensity,
                islandEnvelope
        );
        DensityFunction combinedFinalDensity = DensityFunctions.min(
                shiftedFinalDensity,
                islandEnvelope
        );
        DensityFunction biomeDepth = new CaveBiomeDepthDensityFunction(
                combinedFinalDensity,
                islandEnvelope,
                skyRouter.depth(),
                activeRouter.vegetation(),
                24.0 / 64.0,
                0.45,
                0.032,
                0.008
        );
        NoiseRouter mergedRouter = new NoiseRouter(
                activeRouter.barrierNoise(),
                activeRouter.fluidLevelFloodednessNoise(),
                activeRouter.fluidLevelSpreadNoise(),
                activeRouter.lavaNoise(),
                activeRouter.temperature(),
                activeRouter.vegetation(),
                activeRouter.continents(),
                activeRouter.erosion(),
                biomeDepth,
                activeRouter.ridges(),
                combinedInitialDensity,
                combinedFinalDensity,
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

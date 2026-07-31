package com.kuronami.skyworld.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class SkyWorldNoiseSettingsTest {
    @Test
    void compatibilityGeneratorCodecIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "sky_world",
                "noise"
        );

        assertTrue(BuiltInRegistries.CHUNK_GENERATOR.containsKey(id));
        assertSame(
                SkyWorldChunkGenerator.CODEC,
                BuiltInRegistries.CHUNK_GENERATOR.get(id)
        );
    }

    @Test
    void inheritsActiveOverworldClimateAndSurfaceWhileKeepingSkyGeometry() throws Exception {
        NoiseRouter activeRouter = routerStartingAt(1.0);
        NoiseRouter skyRouter = routerStartingAt(101.0);
        SurfaceRules.RuleSource activeSurface =
                SurfaceRules.state(Blocks.SAND.defaultBlockState());
        SurfaceRules.RuleSource skySurface =
                SurfaceRules.state(Blocks.GRAVEL.defaultBlockState());

        NoiseGeneratorSettings activeOverworld = new NoiseGeneratorSettings(
                NoiseSettings.create(-64, 384, 1, 2),
                Blocks.STONE.defaultBlockState(),
                Blocks.WATER.defaultBlockState(),
                activeRouter,
                activeSurface,
                new OverworldBiomeBuilder().spawnTarget(),
                63,
                false,
                true,
                true,
                true
        );
        NoiseGeneratorSettings skyTerrain = new NoiseGeneratorSettings(
                NoiseSettings.create(-64, 192, 1, 2),
                Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                skyRouter,
                skySurface,
                List.of(),
                -64,
                true,
                false,
                false,
                false
        );

        NoiseGeneratorSettings merged = invokeMerge(activeOverworld, skyTerrain);
        NoiseRouter mergedRouter = merged.noiseRouter();

        assertSame(skyTerrain.noiseSettings(), merged.noiseSettings());
        assertSame(activeOverworld.defaultBlock(), merged.defaultBlock());
        assertSame(skyTerrain.defaultFluid(), merged.defaultFluid());
        assertSame(activeSurface, merged.surfaceRule());
        assertSame(activeOverworld.spawnTarget(), merged.spawnTarget());
        assertEquals(skyTerrain.seaLevel(), merged.seaLevel());
        assertEquals(skyTerrain.disableMobGeneration(), merged.disableMobGeneration());
        assertEquals(skyTerrain.isAquifersEnabled(), merged.isAquifersEnabled());
        assertEquals(activeOverworld.oreVeinsEnabled(), merged.oreVeinsEnabled());
        assertEquals(activeOverworld.useLegacyRandomSource(), merged.useLegacyRandomSource());

        assertSame(activeRouter.barrierNoise(), mergedRouter.barrierNoise());
        assertSame(activeRouter.fluidLevelFloodednessNoise(),
                mergedRouter.fluidLevelFloodednessNoise());
        assertSame(activeRouter.fluidLevelSpreadNoise(), mergedRouter.fluidLevelSpreadNoise());
        assertSame(activeRouter.lavaNoise(), mergedRouter.lavaNoise());
        assertSame(activeRouter.temperature(), mergedRouter.temperature());
        assertSame(activeRouter.vegetation(), mergedRouter.vegetation());
        assertSame(activeRouter.continents(), mergedRouter.continents());
        assertSame(activeRouter.erosion(), mergedRouter.erosion());
        assertEquals(-0.005, mergedRouter.depth().minValue());
        assertEquals(1.0, mergedRouter.depth().maxValue());
        assertSame(activeRouter.ridges(), mergedRouter.ridges());
        assertSame(skyRouter.initialDensityWithoutJaggedness(),
                mergedRouter.initialDensityWithoutJaggedness());
        assertSame(skyRouter.finalDensity(), mergedRouter.finalDensity());
        assertSame(activeRouter.veinToggle(), mergedRouter.veinToggle());
        assertSame(activeRouter.veinRidged(), mergedRouter.veinRidged());
        assertSame(activeRouter.veinGap(), mergedRouter.veinGap());
        assertFalse(merged.isAquifersEnabled());
    }

    @Test
    void islandBiomeDepthClampsTheSkyDensityAtAirSurfaceAndInterior() throws Exception {
        NoiseGeneratorSettings activeOverworld = settingsStartingAt(1.0, false);
        DensityFunction.FunctionContext point =
                new DensityFunction.SinglePointContext(0, 128, 0);

        NoiseGeneratorSettings air = invokeMerge(
                activeOverworld,
                withFinalDensity(settingsStartingAt(101.0, true), -0.25)
        );
        NoiseGeneratorSettings interior = invokeMerge(
                activeOverworld,
                withFinalDensity(settingsStartingAt(101.0, true), 0.4)
        );
        NoiseGeneratorSettings saturatedInterior = invokeMerge(
                activeOverworld,
                withFinalDensity(settingsStartingAt(101.0, true), 2.0)
        );

        assertEquals(-0.005, air.noiseRouter().depth().compute(point));
        assertEquals(0.4, interior.noiseRouter().depth().compute(point));
        assertEquals(1.0, saturatedInterior.noiseRouter().depth().compute(point));
    }

    @Test
    void compatibilityGeneratorKeepsItsSkyWorldIdentityAfterMergingSettings() throws Exception {
        NoiseGeneratorSettings activeOverworld = settingsStartingAt(1.0, false);
        NoiseGeneratorSettings skyTerrain = settingsStartingAt(101.0, true);
        Class<?> generatorType;
        try {
            generatorType = Class.forName(
                    "com.kuronami.skyworld.worldgen.SkyWorldChunkGenerator"
            );
        } catch (ClassNotFoundException exception) {
            fail("Sky World compatibility chunk generator has not been implemented");
            return;
        }

        assertTrue(NoiseBasedChunkGenerator.class.isAssignableFrom(generatorType));
        assertNotNull(generatorType.getField("CODEC").get(null));

        Constructor<?> constructor = generatorType.getDeclaredConstructor(
                BiomeSource.class,
                Holder.class,
                Holder.class
        );
        constructor.setAccessible(true);
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) constructor.newInstance(
                emptyBiomeSource(),
                Holder.direct(activeOverworld),
                Holder.direct(skyTerrain)
        );
        ResourceKey<NoiseGeneratorSettings> skySettingsKey = ResourceKey.create(
                Registries.NOISE_SETTINGS,
                ResourceLocation.fromNamespaceAndPath("sky_world", "overworld")
        );

        assertTrue(generator.stable(skySettingsKey));
        assertSame(
                activeOverworld.noiseRouter().temperature(),
                generator.generatorSettings().value().noiseRouter().temperature()
        );
        assertSame(
                skyTerrain.noiseRouter().finalDensity(),
                generator.generatorSettings().value().noiseRouter().finalDensity()
        );
    }

    private static NoiseGeneratorSettings invokeMerge(
            NoiseGeneratorSettings activeOverworld,
            NoiseGeneratorSettings skyTerrain
    ) throws Exception {
        Class<?> merger;
        try {
            merger = Class.forName(
                    "com.kuronami.skyworld.worldgen.SkyWorldNoiseSettings"
            );
        } catch (ClassNotFoundException exception) {
            fail("Sky World noise settings merger has not been implemented");
            return null;
        }

        Method merge = merger.getDeclaredMethod(
                "merge",
                NoiseGeneratorSettings.class,
                NoiseGeneratorSettings.class
        );
        merge.setAccessible(true);
        try {
            return (NoiseGeneratorSettings) merge.invoke(null, activeOverworld, skyTerrain);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Sky World settings merge failed", exception.getCause());
        }
    }

    private static NoiseRouter routerStartingAt(double firstValue) {
        DensityFunction[] functions = new DensityFunction[15];
        for (int index = 0; index < functions.length; index++) {
            functions[index] = DensityFunctions.constant(firstValue + index);
        }
        return new NoiseRouter(
                functions[0],
                functions[1],
                functions[2],
                functions[3],
                functions[4],
                functions[5],
                functions[6],
                functions[7],
                functions[8],
                functions[9],
                functions[10],
                functions[11],
                functions[12],
                functions[13],
                functions[14]
        );
    }

    private static NoiseGeneratorSettings settingsStartingAt(
            double firstRouterValue,
            boolean skyTerrain
    ) {
        return new NoiseGeneratorSettings(
                NoiseSettings.create(-64, skyTerrain ? 192 : 384, 1, 2),
                skyTerrain
                        ? Blocks.DEEPSLATE.defaultBlockState()
                        : Blocks.STONE.defaultBlockState(),
                skyTerrain ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(),
                routerStartingAt(firstRouterValue),
                SurfaceRules.state(
                        skyTerrain
                                ? Blocks.GRAVEL.defaultBlockState()
                                : Blocks.SAND.defaultBlockState()
                ),
                skyTerrain ? List.of() : new OverworldBiomeBuilder().spawnTarget(),
                skyTerrain ? -64 : 63,
                skyTerrain,
                !skyTerrain,
                !skyTerrain,
                !skyTerrain
        );
    }

    private static NoiseGeneratorSettings withFinalDensity(
            NoiseGeneratorSettings settings,
            double finalDensity
    ) {
        NoiseRouter router = settings.noiseRouter();
        NoiseRouter replacedRouter = new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                router.initialDensityWithoutJaggedness(),
                DensityFunctions.constant(finalDensity),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap()
        );
        return new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                replacedRouter,
                settings.surfaceRule(),
                settings.spawnTarget(),
                settings.seaLevel(),
                settings.disableMobGeneration(),
                settings.isAquifersEnabled(),
                settings.oreVeinsEnabled(),
                settings.useLegacyRandomSource()
        );
    }

    private static BiomeSource emptyBiomeSource() {
        return new BiomeSource() {
            @Override
            protected MapCodec<? extends BiomeSource> codec() {
                return FixedBiomeSource.CODEC;
            }

            @Override
            protected Stream<Holder<Biome>> collectPossibleBiomes() {
                return Stream.empty();
            }

            @Override
            public Holder<Biome> getNoiseBiome(
                    int x,
                    int y,
                    int z,
                    Climate.Sampler sampler
            ) {
                return null;
            }
        };
    }
}

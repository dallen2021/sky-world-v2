package com.kuronami.skyworld.worldgen;

import com.kuronami.skyworld.SkyWorld;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public final class SkyWorldChunkGenerator extends NoiseBasedChunkGenerator {
    private static final ResourceKey<NoiseGeneratorSettings> SKY_WORLD_SETTINGS =
            ResourceKey.create(
                    Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(SkyWorld.MODID, "overworld")
            );

    public static final MapCodec<SkyWorldChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(ChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings")
                            .forGetter(generator -> generator.activeOverworldSettings),
                    NoiseGeneratorSettings.CODEC.fieldOf("sky_settings")
                            .forGetter(generator -> generator.skyTerrainSettings)
            ).apply(instance, instance.stable(SkyWorldChunkGenerator::new)));

    private final Holder<NoiseGeneratorSettings> activeOverworldSettings;
    private final Holder<NoiseGeneratorSettings> skyTerrainSettings;

    SkyWorldChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> activeOverworldSettings,
            Holder<NoiseGeneratorSettings> skyTerrainSettings
    ) {
        super(
                biomeSource,
                Holder.direct(SkyWorldNoiseSettings.merge(
                        activeOverworldSettings.value(),
                        skyTerrainSettings.value()
                ))
        );
        this.activeOverworldSettings = activeOverworldSettings;
        this.skyTerrainSettings = skyTerrainSettings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public boolean stable(ResourceKey<NoiseGeneratorSettings> settings) {
        return settings == SKY_WORLD_SETTINGS || super.stable(settings);
    }
}

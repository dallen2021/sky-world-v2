package com.kuronami.skyworld.worldgen.placement;

import com.kuronami.skyworld.SkyWorld;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

public final class SkyWorldOnlyPlacement extends PlacementModifier {
    private static final SkyWorldOnlyPlacement INSTANCE = new SkyWorldOnlyPlacement();
    public static final MapCodec<SkyWorldOnlyPlacement> CODEC = MapCodec.unit(() -> INSTANCE);

    private static final ResourceKey<NoiseGeneratorSettings> SKY_WORLD_SETTINGS =
            ResourceKey.create(
                    Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(SkyWorld.MODID, "overworld")
            );

    private SkyWorldOnlyPlacement() {
    }

    @Override
    public Stream<BlockPos> getPositions(
            PlacementContext context,
            RandomSource random,
            BlockPos pos
    ) {
        if (context.generator() instanceof NoiseBasedChunkGenerator generator
                && generator.stable(SKY_WORLD_SETTINGS)) {
            return Stream.of(pos);
        }
        return Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkyWorldPlacementModifiers.SKY_WORLD_ONLY.get();
    }
}

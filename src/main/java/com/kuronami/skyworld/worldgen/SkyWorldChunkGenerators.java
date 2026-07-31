package com.kuronami.skyworld.worldgen;

import com.kuronami.skyworld.SkyWorld;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyWorldChunkGenerators {
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> TYPES =
            DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR, SkyWorld.MODID);

    static {
        TYPES.register("noise", () -> SkyWorldChunkGenerator.CODEC);
    }

    private SkyWorldChunkGenerators() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}

package com.kuronami.skyworld.worldgen.density;

import com.kuronami.skyworld.SkyWorld;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyWorldDensityFunctions {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(BuiltInRegistries.DENSITY_FUNCTION_TYPE, SkyWorld.MODID);

    static {
        TYPES.register("island_envelope", () -> IslandEnvelopeDensityFunction.CODEC);
    }

    private SkyWorldDensityFunctions() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}

package com.kuronami.skyworld.worldgen.placement;

import com.kuronami.skyworld.SkyWorld;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyWorldPlacementModifiers {
    private static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, SkyWorld.MODID);

    public static final DeferredHolder<
            PlacementModifierType<?>,
            PlacementModifierType<SkyWorldOnlyPlacement>
            > SKY_WORLD_ONLY = TYPES.register(
            "sky_world_only",
            () -> () -> SkyWorldOnlyPlacement.CODEC
    );

    private SkyWorldPlacementModifiers() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}

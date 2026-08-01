package com.kuronami.skyworld.worldgen.density;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

record ExosphereHybridSettings(
        double horizontalScale,
        double verticalScale,
        double densityThreshold,
        int cellSpacing,
        int centerJitter,
        double minGroupRadius,
        double maxGroupRadius,
        double groupTransition,
        double edgeWarp,
        double voidStrength
) {
    static final Codec<ExosphereHybridSettings> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.doubleRange(0.25, 16.0).optionalFieldOf("horizontal_scale", 3.0)
                            .forGetter(ExosphereHybridSettings::horizontalScale),
                    Codec.doubleRange(0.25, 8.0).optionalFieldOf("vertical_scale", 1.0)
                            .forGetter(ExosphereHybridSettings::verticalScale),
                    Codec.doubleRange(-2.0, 2.0).optionalFieldOf("density_threshold", 0.0)
                            .forGetter(ExosphereHybridSettings::densityThreshold),
                    Codec.intRange(512, 8192).optionalFieldOf("cell_spacing", 5120)
                            .forGetter(ExosphereHybridSettings::cellSpacing),
                    Codec.intRange(0, 512).optionalFieldOf("center_jitter", 384)
                            .forGetter(ExosphereHybridSettings::centerJitter),
                    Codec.doubleRange(64.0, 4096.0).optionalFieldOf("min_group_radius", 700.0)
                            .forGetter(ExosphereHybridSettings::minGroupRadius),
                    Codec.doubleRange(64.0, 4096.0).optionalFieldOf("max_group_radius", 850.0)
                            .forGetter(ExosphereHybridSettings::maxGroupRadius),
                    Codec.doubleRange(16.0, 1024.0).optionalFieldOf("group_transition", 320.0)
                            .forGetter(ExosphereHybridSettings::groupTransition),
                    Codec.doubleRange(0.0, 512.0).optionalFieldOf("edge_warp", 192.0)
                            .forGetter(ExosphereHybridSettings::edgeWarp),
                    Codec.doubleRange(0.05, 4.0).optionalFieldOf("void_strength", 0.85)
                            .forGetter(ExosphereHybridSettings::voidStrength)
            ).apply(instance, ExosphereHybridSettings::new)
    );

    ExosphereHybridSettings {
        if (minGroupRadius > maxGroupRadius) {
            throw new IllegalArgumentException(
                    "min_group_radius must not exceed max_group_radius"
            );
        }
        if (centerJitter * 2 >= cellSpacing) {
            throw new IllegalArgumentException(
                    "center_jitter must be smaller than half of cell_spacing"
            );
        }
    }

    static ExosphereHybridSettings defaults() {
        return new ExosphereHybridSettings(
                3.0,
                1.0,
                0.0,
                5120,
                384,
                700.0,
                850.0,
                320.0,
                192.0,
                0.85
        );
    }
}

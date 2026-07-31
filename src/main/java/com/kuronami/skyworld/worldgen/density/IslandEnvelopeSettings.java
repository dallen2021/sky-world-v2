package com.kuronami.skyworld.worldgen.density;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

record ArchetypeSettings(
        double weight,
        int minCount,
        int maxCount,
        double minRadius,
        double maxRadius,
        double minGap,
        double maxGap,
        double minGroupRadius,
        double maxGroupRadius,
        int minLobes,
        int maxLobes,
        double minAspect,
        double maxAspect,
        double minKeelDepth,
        double maxKeelDepth
) {
    static final Codec<ArchetypeSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.0, 1.0).fieldOf("weight")
                    .forGetter(ArchetypeSettings::weight),
            Codec.intRange(1, 32).fieldOf("min_count")
                    .forGetter(ArchetypeSettings::minCount),
            Codec.intRange(1, 32).fieldOf("max_count")
                    .forGetter(ArchetypeSettings::maxCount),
            Codec.doubleRange(16.0, 2048.0).fieldOf("min_radius")
                    .forGetter(ArchetypeSettings::minRadius),
            Codec.doubleRange(16.0, 2048.0).fieldOf("max_radius")
                    .forGetter(ArchetypeSettings::maxRadius),
            Codec.doubleRange(0.0, 1024.0).fieldOf("min_gap")
                    .forGetter(ArchetypeSettings::minGap),
            Codec.doubleRange(0.0, 1024.0).fieldOf("max_gap")
                    .forGetter(ArchetypeSettings::maxGap),
            Codec.doubleRange(64.0, 2048.0).fieldOf("min_group_radius")
                    .forGetter(ArchetypeSettings::minGroupRadius),
            Codec.doubleRange(64.0, 2048.0).fieldOf("max_group_radius")
                    .forGetter(ArchetypeSettings::maxGroupRadius),
            Codec.intRange(1, 16).optionalFieldOf("min_lobes", 1)
                    .forGetter(ArchetypeSettings::minLobes),
            Codec.intRange(1, 16).optionalFieldOf("max_lobes", 1)
                    .forGetter(ArchetypeSettings::maxLobes),
            Codec.doubleRange(0.4, 2.0).optionalFieldOf("min_aspect", 0.9)
                    .forGetter(ArchetypeSettings::minAspect),
            Codec.doubleRange(0.4, 2.0).optionalFieldOf("max_aspect", 1.1)
                    .forGetter(ArchetypeSettings::maxAspect),
            Codec.doubleRange(8.0, 512.0).optionalFieldOf("min_keel_depth", 80.0)
                    .forGetter(ArchetypeSettings::minKeelDepth),
            Codec.doubleRange(8.0, 512.0).optionalFieldOf("max_keel_depth", 168.0)
                    .forGetter(ArchetypeSettings::maxKeelDepth)
    ).apply(instance, ArchetypeSettings::new));

    ArchetypeSettings {
        if (minCount > maxCount || minRadius > maxRadius || minGap > maxGap
                || minGroupRadius > maxGroupRadius || minLobes > maxLobes
                || minAspect > maxAspect || minKeelDepth > maxKeelDepth) {
            throw new IllegalArgumentException("Island archetype minimums must not exceed maximums");
        }
    }
}

record IslandEnvelopeSettings(
        int cellSize,
        int centerJitter,
        int shoulderY,
        int bottomY,
        int topY,
        double edgeWarp,
        double undersideVariation,
        double normalizationScale,
        ArchetypeSettings continental,
        ArchetypeSettings medium,
        ArchetypeSettings archipelago,
        ArchetypeSettings small
) {
    static final Codec<IslandEnvelopeSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(512, 8192).fieldOf("cell_size")
                    .forGetter(IslandEnvelopeSettings::cellSize),
            Codec.intRange(0, 512).fieldOf("center_jitter")
                    .forGetter(IslandEnvelopeSettings::centerJitter),
            Codec.intRange(-64, 320).fieldOf("shoulder_y")
                    .forGetter(IslandEnvelopeSettings::shoulderY),
            Codec.intRange(-128, 256).fieldOf("bottom_y")
                    .forGetter(IslandEnvelopeSettings::bottomY),
            Codec.intRange(-64, 384).fieldOf("top_y")
                    .forGetter(IslandEnvelopeSettings::topY),
            Codec.doubleRange(0.0, 256.0).fieldOf("edge_warp")
                    .forGetter(IslandEnvelopeSettings::edgeWarp),
            Codec.doubleRange(0.0, 64.0).fieldOf("underside_variation")
                    .forGetter(IslandEnvelopeSettings::undersideVariation),
            Codec.doubleRange(1.0, 512.0).fieldOf("normalization_scale")
                    .forGetter(IslandEnvelopeSettings::normalizationScale),
            ArchetypeSettings.CODEC.fieldOf("continental")
                    .forGetter(IslandEnvelopeSettings::continental),
            ArchetypeSettings.CODEC.fieldOf("medium")
                    .forGetter(IslandEnvelopeSettings::medium),
            ArchetypeSettings.CODEC.fieldOf("archipelago")
                    .forGetter(IslandEnvelopeSettings::archipelago),
            ArchetypeSettings.CODEC.fieldOf("small")
                    .forGetter(IslandEnvelopeSettings::small)
    ).apply(instance, IslandEnvelopeSettings::new));

    IslandEnvelopeSettings {
        if (bottomY >= shoulderY || shoulderY >= topY) {
            throw new IllegalArgumentException("Expected bottom_y < shoulder_y < top_y");
        }
        double totalWeight = continental.weight() + medium.weight()
                + archipelago.weight() + small.weight();
        if (totalWeight <= 0.0) {
            throw new IllegalArgumentException("At least one island archetype needs positive weight");
        }
    }

    static IslandEnvelopeSettings defaults() {
        return new IslandEnvelopeSettings(
                2048,
                96,
                160,
                -56,
                304,
                128.0,
                18.0,
                64.0,
                new ArchetypeSettings(0.55, 1, 1, 660.0, 800.0, 0.0, 0.0,
                        660.0, 800.0, 4, 7, 0.58, 1.45, 112.0, 214.0),
                new ArchetypeSettings(0.20, 2, 3, 260.0, 500.0, 160.0, 420.0,
                        750.0, 900.0, 3, 6, 0.65, 1.35, 88.0, 204.0),
                new ArchetypeSettings(0.20, 7, 16, 80.0, 240.0, 40.0, 160.0,
                        550.0, 750.0, 1, 3, 0.75, 1.30, 64.0, 176.0),
                new ArchetypeSettings(0.05, 4, 8, 70.0, 180.0, 180.0, 420.0,
                        700.0, 850.0, 1, 2, 0.75, 1.30, 56.0, 152.0)
        );
    }

    ArchetypeSettings settingsFor(IslandArchetype archetype) {
        return switch (archetype) {
            case CONTINENTAL -> continental;
            case MEDIUM -> medium;
            case ARCHIPELAGO -> archipelago;
            case SMALL -> small;
        };
    }
}

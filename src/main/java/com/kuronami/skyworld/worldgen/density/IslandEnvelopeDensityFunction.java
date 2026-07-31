package com.kuronami.skyworld.worldgen.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class IslandEnvelopeDensityFunction implements DensityFunction {
    public static final int MAX_CACHED_CELLS = 4096;

    public static final MapCodec<IslandEnvelopeDensityFunction> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.NoiseHolder.CODEC.fieldOf("layout_noise")
                            .forGetter(IslandEnvelopeDensityFunction::layoutNoise),
                    DensityFunction.NoiseHolder.CODEC.fieldOf("detail_noise")
                            .forGetter(IslandEnvelopeDensityFunction::detailNoise),
                    IslandEnvelopeSettings.CODEC.fieldOf("settings")
                            .forGetter(IslandEnvelopeDensityFunction::settings)
            ).apply(instance, IslandEnvelopeDensityFunction::new));

    private static final KeyDispatchDataCodec<IslandEnvelopeDensityFunction> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);
    private static final long CELL_X_SALT = 0x9E3779B97F4A7C15L;
    private static final long CELL_Z_SALT = 0xD1B54A32D192ED03L;

    private final DensityFunction.NoiseHolder layoutNoise;
    private final DensityFunction.NoiseHolder detailNoise;
    private final IslandEnvelopeSettings settings;
    private final ConcurrentHashMap<Long, IslandCellDescriptor> cellCache =
            new ConcurrentHashMap<>();

    public IslandEnvelopeDensityFunction(
            DensityFunction.NoiseHolder layoutNoise,
            DensityFunction.NoiseHolder detailNoise,
            IslandEnvelopeSettings settings
    ) {
        this.layoutNoise = layoutNoise;
        this.detailNoise = detailNoise;
        this.settings = settings;
    }

    DensityFunction.NoiseHolder layoutNoise() {
        return layoutNoise;
    }

    DensityFunction.NoiseHolder detailNoise() {
        return detailNoise;
    }

    IslandEnvelopeSettings settings() {
        return settings;
    }

    @Override
    public double compute(FunctionContext context) {
        int y = context.blockY();
        if (y <= settings.bottomY() || y >= settings.topY()) {
            return -1.0;
        }

        int baseCellX = Math.floorDiv(context.blockX(), settings.cellSize());
        int baseCellZ = Math.floorDiv(context.blockZ(), settings.cellSize());
        double strongest = -Double.MAX_VALUE;

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                IslandCellDescriptor cell = cellDescriptor(
                        baseCellX + offsetX,
                        baseCellZ + offsetZ
                );
                for (IslandComponent component : cell.components()) {
                    strongest = Math.max(strongest, componentDensity(context, component));
                }
            }
        }

        return Mth.clamp(strongest / settings.normalizationScale(), -1.0, 1.0);
    }

    private double componentDensity(FunctionContext context, IslandComponent component) {
        double adjustedY = context.blockY();
        if (adjustedY < settings.shoulderY()) {
            double undersideNoise = detailNoise.getValue(
                    (context.blockX() + 911.0) / 128.0,
                    adjustedY / 96.0,
                    (context.blockZ() - 353.0) / 128.0
            );
            adjustedY += undersideNoise * settings.undersideVariation();
        }

        double radiusScale = verticalRadiusScale(adjustedY);
        if (radiusScale <= 0.0) {
            return -settings.normalizationScale();
        }

        double radiusX = Math.max(1.0, component.radiusX() * radiusScale);
        double radiusZ = Math.max(1.0, component.radiusZ() * radiusScale);
        double dx = context.blockX() - component.centerX();
        double dz = context.blockZ() - component.centerZ();
        double normalizedRadius = Math.sqrt(dx * dx / (radiusX * radiusX)
                + dz * dz / (radiusZ * radiusZ));
        double signedDistance = (1.0 - normalizedRadius) * Math.min(radiusX, radiusZ);
        double boundaryWarp = detailNoise.getValue(
                context.blockX() / 384.0,
                0.0,
                context.blockZ() / 384.0
        ) * settings.edgeWarp();
        return signedDistance + boundaryWarp;
    }

    private double verticalRadiusScale(double y) {
        if (y >= settings.shoulderY()) {
            return 1.0;
        }
        if (y <= settings.bottomY()) {
            return 0.0;
        }
        double progress = (y - settings.bottomY())
                / (settings.shoulderY() - settings.bottomY());
        double smooth = progress * progress * (3.0 - 2.0 * progress);
        return Math.pow(smooth, 0.72);
    }

    IslandCellDescriptor cellDescriptor(int cellX, int cellZ) {
        long key = cellKey(cellX, cellZ);
        IslandCellDescriptor existing = cellCache.get(key);
        if (existing != null) {
            return existing;
        }
        if (cellCache.size() >= MAX_CACHED_CELLS) {
            synchronized (cellCache) {
                if (cellCache.size() >= MAX_CACHED_CELLS) {
                    cellCache.clear();
                }
            }
        }
        return cellCache.computeIfAbsent(key, ignored -> createCellDescriptor(cellX, cellZ));
    }

    int cachedCellCount() {
        return cellCache.size();
    }

    private IslandCellDescriptor createCellDescriptor(int cellX, int cellZ) {
        int cellSize = settings.cellSize();
        double sampleX = (cellX + 0.5) * cellSize;
        double sampleZ = (cellZ + 0.5) * cellSize;
        double noiseValue = layoutNoise.getValue(sampleX / 2048.0, 0.0, sampleZ / 2048.0);
        long entropy = mix64(Double.doubleToLongBits(noiseValue)
                ^ (cellX * CELL_X_SALT)
                ^ (cellZ * CELL_Z_SALT));
        SplittableRandom random = new SplittableRandom(entropy);

        double centerX = sampleX + random.nextDouble(
                -settings.centerJitter(),
                settings.centerJitter() + Math.ulp((double)settings.centerJitter())
        );
        double centerZ = sampleZ + random.nextDouble(
                -settings.centerJitter(),
                settings.centerJitter() + Math.ulp((double)settings.centerJitter())
        );
        IslandArchetype archetype = selectArchetype(random.nextDouble());
        ArchetypeSettings archetypeSettings = settings.settingsFor(archetype);
        List<IslandComponent> components = createComponents(
                random,
                archetype,
                archetypeSettings,
                centerX,
                centerZ
        );
        return new IslandCellDescriptor(cellX, cellZ, archetype, centerX, centerZ, components);
    }

    private List<IslandComponent> createComponents(
            SplittableRandom random,
            IslandArchetype archetype,
            ArchetypeSettings config,
            double groupCenterX,
            double groupCenterZ
    ) {
        int desiredCount = random.nextInt(config.minCount(), config.maxCount() + 1);
        double groupRadius = random.nextDouble(config.minGroupRadius(), config.maxGroupRadius());
        List<IslandComponent> components = new ArrayList<>(desiredCount);

        if (desiredCount == 1) {
            double radius = chooseRadius(random, config, archetype, config.maxRadius());
            double aspect = random.nextDouble(0.90, 1.10);
            double axisScale = radius / Math.max(aspect, 1.0 / aspect);
            return List.of(new IslandComponent(
                    groupCenterX,
                    groupCenterZ,
                    axisScale * aspect,
                    axisScale / aspect
            ));
        }

        double sine = Math.sin(Math.PI / desiredCount);
        double targetGap = random.nextDouble(
                config.minGap(),
                config.maxGap() + Math.ulp(config.maxGap())
        );
        double radius = (2.0 * groupRadius * sine - targetGap)
                / (2.0 * (sine + 1.0));
        radius = Mth.clamp(radius, config.minRadius(), config.maxRadius());
        double ringRadius = Math.max(0.0, groupRadius - radius);
        double rotation = random.nextDouble(0.0, Math.PI * 2.0);

        for (int index = 0; index < desiredCount; index++) {
            double angle = rotation + index * (Math.PI * 2.0 / desiredCount);
            double aspect = random.nextDouble(0.90, 1.10);
            double axisScale = radius / Math.max(aspect, 1.0 / aspect);
            components.add(new IslandComponent(
                    groupCenterX + Math.cos(angle) * ringRadius,
                    groupCenterZ + Math.sin(angle) * ringRadius,
                    axisScale * aspect,
                    axisScale / aspect
            ));
        }

        return List.copyOf(components);
    }

    private static double chooseRadius(
            SplittableRandom random,
            ArchetypeSettings config,
            IslandArchetype archetype,
            double upperBound
    ) {
        double unit = random.nextDouble();
        if (archetype == IslandArchetype.ARCHIPELAGO
                || archetype == IslandArchetype.SMALL) {
            unit *= unit;
        }
        return Mth.lerp(unit, config.minRadius(), Math.max(config.minRadius(), upperBound));
    }

    private IslandArchetype selectArchetype(double unit) {
        double total = settings.continental().weight()
                + settings.medium().weight()
                + settings.archipelago().weight()
                + settings.small().weight();
        double cursor = unit * total;
        cursor -= settings.continental().weight();
        if (cursor < 0.0) {
            return IslandArchetype.CONTINENTAL;
        }
        cursor -= settings.medium().weight();
        if (cursor < 0.0) {
            return IslandArchetype.MEDIUM;
        }
        cursor -= settings.archipelago().weight();
        return cursor < 0.0 ? IslandArchetype.ARCHIPELAGO : IslandArchetype.SMALL;
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long)cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    @Override
    public void fillArray(double[] output, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new IslandEnvelopeDensityFunction(
                visitor.visitNoise(layoutNoise),
                visitor.visitNoise(detailNoise),
                settings
        ));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}

enum IslandArchetype {
    CONTINENTAL,
    MEDIUM,
    ARCHIPELAGO,
    SMALL
}

record IslandComponent(
        double centerX,
        double centerZ,
        double radiusX,
        double radiusZ
) {
    double maxRadius() {
        return Math.max(radiusX, radiusZ);
    }
}

record IslandCellDescriptor(
        int cellX,
        int cellZ,
        IslandArchetype archetype,
        double centerX,
        double centerZ,
        List<IslandComponent> components
) {
}

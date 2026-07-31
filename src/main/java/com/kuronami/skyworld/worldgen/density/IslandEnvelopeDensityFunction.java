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

        int baseCellX = nearestCell(context.blockX());
        int baseCellZ = nearestCell(context.blockZ());
        double strongest = -Double.MAX_VALUE;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                IslandCellDescriptor cell = cellDescriptor(
                        baseCellX + offsetX,
                        baseCellZ + offsetZ
                );
                double maximumReach = cell.boundRadius() + settings.edgeWarp();
                if (Math.abs(context.blockX() - cell.centerX()) > maximumReach
                        || Math.abs(context.blockZ() - cell.centerZ()) > maximumReach) {
                    continue;
                }
                for (IslandComponent component : cell.components()) {
                    strongest = Math.max(
                            strongest,
                            componentDensity(context, component)
                    );
                }
            }
        }

        return Mth.clamp(strongest / settings.normalizationScale(), -1.0, 1.0);
    }

    private double componentDensity(
            FunctionContext context,
            IslandComponent component
    ) {
        double horizontalDensity = horizontalDensity(context, component);
        if (context.blockY() >= settings.shoulderY() || horizontalDensity <= 0.0) {
            return horizontalDensity;
        }

        double maximumDepth = settings.shoulderY() - settings.bottomY() - 1.0;
        double localDepth = 8.0;
        for (IslandKeel keel : component.keels()) {
            double dx = context.blockX() - keel.centerX();
            double dz = context.blockZ() - keel.centerZ();
            double localX = dx * keel.cosRotation() + dz * keel.sinRotation();
            double localZ = -dx * keel.sinRotation() + dz * keel.cosRotation();
            double normalizedRadius = Math.sqrt(
                    localX * localX / (keel.radiusX() * keel.radiusX())
                            + localZ * localZ / (keel.radiusZ() * keel.radiusZ())
            );
            if (normalizedRadius < 1.0) {
                double influence = Math.pow(1.0 - normalizedRadius, keel.exponent());
                localDepth = Math.max(localDepth, keel.depth() * influence);
            }
        }

        double roughness = Mth.clamp(detailNoise.getValue(
                        (context.blockX() + 911.0) / 112.0,
                        0.0,
                        (context.blockZ() - 353.0) / 112.0
                ), -1.0, 1.0)
                * settings.undersideVariation()
                * Math.sqrt(localDepth / maximumDepth);
        localDepth = Mth.clamp(localDepth + roughness, 4.0, maximumDepth);
        double localBottom = settings.shoulderY() - localDepth;
        double verticalDensity = context.blockY() - localBottom;
        return Math.min(horizontalDensity, verticalDensity);
    }

    private double horizontalDensity(
            FunctionContext context,
            IslandComponent component
    ) {
        double strongest = -Double.MAX_VALUE;
        for (IslandLobe lobe : component.lobes()) {
            double radiusX = lobe.radiusX();
            double radiusZ = lobe.radiusZ();
            double dx = context.blockX() - lobe.centerX();
            double dz = context.blockZ() - lobe.centerZ();
            double localX = dx * lobe.cosRotation() + dz * lobe.sinRotation();
            double localZ = -dx * lobe.sinRotation() + dz * lobe.cosRotation();
            double normalizedRadius = Math.sqrt(localX * localX / (radiusX * radiusX)
                    + localZ * localZ / (radiusZ * radiusZ));
            double angle = Math.atan2(localZ / radiusZ, localX / radiusX);
            double harmonic = 0.50 * Math.sin(angle * 2.0 + lobe.phase2())
                    + 0.30 * Math.sin(angle * 3.0 + lobe.phase3())
                    + 0.20 * Math.sin(angle * 5.0 + lobe.phase5());
            double maximumWarp = Math.min(
                    settings.edgeWarp() * lobe.warpScale(),
                    Math.min(radiusX, radiusZ) * 0.25
            );
            double signedDistance = (1.0 - normalizedRadius)
                    * Math.min(radiusX, radiusZ)
                    + harmonic * maximumWarp;
            strongest = Math.max(strongest, signedDistance);
        }
        return strongest;
    }

    private int nearestCell(int coordinate) {
        return Math.floorDiv(coordinate + settings.cellSize() / 2, settings.cellSize());
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
        double sampleX = (double)cellX * cellSize;
        double sampleZ = (double)cellZ * cellSize;
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
        IslandArchetype archetype = cellX == 0 && cellZ == 0
                ? IslandArchetype.CONTINENTAL
                : selectArchetype(random.nextDouble());
        ArchetypeSettings archetypeSettings = settings.settingsFor(archetype);
        List<IslandComponent> components = createComponents(
                random,
                archetype,
                archetypeSettings,
                centerX,
                centerZ
        );
        double boundRadius = components.stream()
                .mapToDouble(component -> Math.hypot(
                        component.centerX() - centerX,
                        component.centerZ() - centerZ
                ) + component.maxRadius())
                .max()
                .orElse(0.0);
        return new IslandCellDescriptor(
                cellX,
                cellZ,
                archetype,
                centerX,
                centerZ,
                components,
                boundRadius
        );
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
            double upperBound = archetype == IslandArchetype.CONTINENTAL
                    && Math.abs(groupCenterX) < settings.cellSize() / 2.0
                    && Math.abs(groupCenterZ) < settings.cellSize() / 2.0
                    ? Math.min(config.maxRadius(), config.minRadius() + 75.0)
                    : config.maxRadius();
            double radius = chooseRadius(random, config, archetype, upperBound);
            return List.of(createComponent(
                    random,
                    groupCenterX,
                    groupCenterZ,
                    radius,
                    config
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
            components.add(createComponent(
                    random,
                    groupCenterX + Math.cos(angle) * ringRadius,
                    groupCenterZ + Math.sin(angle) * ringRadius,
                    radius,
                    config
            ));
        }

        return List.copyOf(components);
    }

    private IslandComponent createComponent(
            SplittableRandom random,
            double centerX,
            double centerZ,
            double targetRadius,
            ArchetypeSettings config
    ) {
        double componentAspect = chooseComponentAspect(random, config);
        double[] componentAxes = axes(targetRadius, componentAspect);
        double componentRotation = random.nextDouble(0.0, Math.PI * 2.0);
        int lobeCount = random.nextInt(config.minLobes(), config.maxLobes() + 1);
        List<IslandLobe> lobes = new ArrayList<>(lobeCount);

        double primaryScale = lobeCount <= 3
                ? random.nextDouble(0.92, 0.98)
                : random.nextDouble(0.70, 0.78);
        lobes.add(createLobe(
                random,
                centerX,
                centerZ,
                componentAxes[0] * primaryScale,
                componentAxes[1] * primaryScale,
                componentRotation,
                lobeCount <= 3 ? 0.30 : 0.56
        ));

        for (int index = 1; index < lobeCount; index++) {
            double angle = componentRotation + random.nextDouble(0.0, Math.PI * 2.0);
            double offset = targetRadius * random.nextDouble(0.35, 0.62);
            double lobeRadius = targetRadius * random.nextDouble(0.30, 0.48);
            double lobeAspect = random.nextDouble(
                    Math.max(0.65, config.minAspect()),
                    Math.min(1.45, config.maxAspect()) + Math.ulp(config.maxAspect())
            );
            double[] lobeAxes = axes(lobeRadius, lobeAspect);
            lobes.add(createLobe(
                    random,
                    centerX + Math.cos(angle) * offset,
                    centerZ + Math.sin(angle) * offset,
                    lobeAxes[0],
                    lobeAxes[1],
                    random.nextDouble(0.0, Math.PI * 2.0),
                    random.nextDouble(0.45, 0.85)
            ));
        }

        List<IslandKeel> keels = createKeels(random, lobes, config);

        return new IslandComponent(
                centerX,
                centerZ,
                componentAxes[0],
                componentAxes[1],
                List.copyOf(lobes),
                keels
        );
    }

    private List<IslandKeel> createKeels(
            SplittableRandom random,
            List<IslandLobe> lobes,
            ArchetypeSettings config
    ) {
        double maximumAllowedDepth = settings.shoulderY() - settings.bottomY() - 2.0;
        double maximumDepth = Math.min(config.maxKeelDepth(), maximumAllowedDepth);
        double minimumDepth = Math.min(config.minKeelDepth(), maximumDepth);
        List<IslandKeel> keels = new ArrayList<>(lobes.size());

        for (int index = 0; index < lobes.size(); index++) {
            IslandLobe lobe = lobes.get(index);
            double depth;
            if (index == 0) {
                depth = maximumDepth - random.nextDouble(0.0, 6.0);
            } else if (index <= 2 && lobes.size() >= 4) {
                depth = Mth.lerp(random.nextDouble(0.82, 0.99), minimumDepth, maximumDepth);
            } else {
                depth = random.nextDouble(
                        minimumDepth,
                        maximumDepth + Math.ulp(maximumDepth)
                );
            }
            double radiusScale = index == 0
                    ? random.nextDouble(0.86, 1.02)
                    : random.nextDouble(0.72, 1.08);
            keels.add(new IslandKeel(
                    lobe.centerX(),
                    lobe.centerZ(),
                    Math.max(8.0, lobe.radiusX() * radiusScale),
                    Math.max(8.0, lobe.radiusZ() * radiusScale),
                    lobe.cosRotation(),
                    lobe.sinRotation(),
                    depth,
                    random.nextDouble(0.50, 0.88)
            ));
        }

        return List.copyOf(keels);
    }

    private static double chooseComponentAspect(
            SplittableRandom random,
            ArchetypeSettings config
    ) {
        if (config.minAspect() < 0.70 && config.maxAspect() > 1.36) {
            boolean wide = random.nextBoolean();
            return wide
                    ? random.nextDouble(
                            config.minAspect(),
                            Math.min(0.70, config.maxAspect()) + Math.ulp(config.maxAspect())
                    )
                    : random.nextDouble(
                            Math.max(1.36, config.minAspect()),
                            config.maxAspect() + Math.ulp(config.maxAspect())
                    );
        }
        return random.nextDouble(
                config.minAspect(),
                config.maxAspect() + Math.ulp(config.maxAspect())
        );
    }

    private static IslandLobe createLobe(
            SplittableRandom random,
            double centerX,
            double centerZ,
            double radiusX,
            double radiusZ,
            double rotation,
            double warpScale
    ) {
        return new IslandLobe(
                centerX,
                centerZ,
                radiusX,
                radiusZ,
                Math.cos(rotation),
                Math.sin(rotation),
                warpScale,
                random.nextDouble(0.0, Math.PI * 2.0),
                random.nextDouble(0.0, Math.PI * 2.0),
                random.nextDouble(0.0, Math.PI * 2.0)
        );
    }

    private static double[] axes(double radius, double aspect) {
        return aspect >= 1.0
                ? new double[]{radius, radius / aspect}
                : new double[]{radius * aspect, radius};
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
        double radiusZ,
        List<IslandLobe> lobes,
        List<IslandKeel> keels
) {
    double maxRadius() {
        return lobes.stream()
                .mapToDouble(lobe -> Math.hypot(
                        lobe.centerX() - centerX,
                        lobe.centerZ() - centerZ
                ) + lobe.maxRadius())
                .max()
                .orElse(Math.max(radiusX, radiusZ));
    }
}

record IslandKeel(
        double centerX,
        double centerZ,
        double radiusX,
        double radiusZ,
        double cosRotation,
        double sinRotation,
        double depth,
        double exponent
) {
}

record IslandLobe(
        double centerX,
        double centerZ,
        double radiusX,
        double radiusZ,
        double cosRotation,
        double sinRotation,
        double warpScale,
        double phase2,
        double phase3,
        double phase5
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
        List<IslandComponent> components,
        double boundRadius
) {
}

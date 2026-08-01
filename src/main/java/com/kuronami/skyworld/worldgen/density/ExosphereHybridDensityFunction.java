package com.kuronami.skyworld.worldgen.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class ExosphereHybridDensityFunction implements DensityFunction {
    public static final int MAX_CACHED_GROUPS = 4096;

    public static final MapCodec<ExosphereHybridDensityFunction> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base_noise")
                            .forGetter(ExosphereHybridDensityFunction::baseNoise),
                    DensityFunction.NoiseHolder.CODEC.fieldOf("layout_noise")
                            .forGetter(ExosphereHybridDensityFunction::layoutNoise),
                    DensityFunction.NoiseHolder.CODEC.fieldOf("edge_noise")
                            .forGetter(ExosphereHybridDensityFunction::edgeNoise),
                    ExosphereHybridSettings.CODEC.fieldOf("settings")
                            .forGetter(ExosphereHybridDensityFunction::settings)
            ).apply(instance, ExosphereHybridDensityFunction::new));

    private static final KeyDispatchDataCodec<ExosphereHybridDensityFunction> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);
    private static final long CELL_X_SALT = 0x9E3779B97F4A7C15L;
    private static final long CELL_Z_SALT = 0xD1B54A32D192ED03L;
    private static final double INSIDE_STRENGTH_FACTOR = 0.02;
    private static final double SQRT_THREE = Math.sqrt(3.0);
    private static final long LAYOUT_ROTATION_SALT = 0xA24BAED4963EE407L;

    private final DensityFunction baseNoise;
    private final DensityFunction.NoiseHolder layoutNoise;
    private final DensityFunction.NoiseHolder edgeNoise;
    private final ExosphereHybridSettings settings;
    private final long layoutEntropy;
    private final double latticeCos;
    private final double latticeSin;
    private final ConcurrentHashMap<Long, ExosphereGroupDescriptor> groupCache =
            new ConcurrentHashMap<>();

    public ExosphereHybridDensityFunction(
            DensityFunction baseNoise,
            DensityFunction.NoiseHolder layoutNoise,
            DensityFunction.NoiseHolder edgeNoise,
            ExosphereHybridSettings settings
    ) {
        this.baseNoise = baseNoise;
        this.layoutNoise = layoutNoise;
        this.edgeNoise = edgeNoise;
        this.settings = settings;
        this.layoutEntropy = layoutEntropy(layoutNoise);
        SplittableRandom layoutRandom = new SplittableRandom(layoutEntropy);
        double latticeRotation = layoutRandom.nextDouble(0.0, Math.PI * 2.0);
        this.latticeCos = Math.cos(latticeRotation);
        this.latticeSin = Math.sin(latticeRotation);
    }

    DensityFunction baseNoise() {
        return baseNoise;
    }

    DensityFunction.NoiseHolder layoutNoise() {
        return layoutNoise;
    }

    DensityFunction.NoiseHolder edgeNoise() {
        return edgeNoise;
    }

    ExosphereHybridSettings settings() {
        return settings;
    }

    @Override
    public double compute(FunctionContext context) {
        int y = context.blockY();
        if (y < ExosphereDensityProfile.MIN_Y || y > ExosphereDensityProfile.MAX_Y) {
            return -1.0;
        }

        double exosphereRaw = ExosphereDensityProfile.rawDensity(
                y,
                baseNoise.compute(scaledContext(context, settings))
        );
        double raw = exosphereRaw + groupBias(context.blockX(), context.blockZ())
                - settings.densityThreshold();
        return ExosphereDensityProfile.squeeze(0.64 * raw);
    }

    static FunctionContext scaledContext(
            FunctionContext context,
            ExosphereHybridSettings settings
    ) {
        return new SinglePointContext(
                Mth.floor(context.blockX() / settings.horizontalScale()),
                Mth.floor(context.blockY() / settings.verticalScale()),
                Mth.floor(context.blockZ() / settings.horizontalScale())
        );
    }

    double groupBias(int blockX, int blockZ) {
        ExosphereLatticeCell baseCell = nearestCell(blockX, blockZ);
        double strongestDistance = -Double.MAX_VALUE;

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                ExosphereGroupDescriptor group = groupDescriptor(
                        baseCell.cellX() + offsetX,
                        baseCell.cellZ() + offsetZ
                );
                double dx = blockX - group.centerX();
                double dz = blockZ - group.centerZ();
                double distance = Math.hypot(dx, dz);
                double warp = boundaryWarp(blockX, blockZ, group);
                strongestDistance = Math.max(
                        strongestDistance,
                        group.radius() + warp - distance
                );
            }
        }

        double unit = Mth.clamp(
                strongestDistance / settings.groupTransition(),
                -1.0,
                1.0
        );
        return unit >= 0.0
                ? unit * settings.voidStrength() * INSIDE_STRENGTH_FACTOR
                : unit * settings.voidStrength();
    }

    private double boundaryWarp(
            int blockX,
            int blockZ,
            ExosphereGroupDescriptor group
    ) {
        double coarse = edgeNoise.getValue(
                (blockX + group.phaseX()) / 512.0,
                0.0,
                (blockZ + group.phaseZ()) / 512.0
        );
        double detail = edgeNoise.getValue(
                (blockX - group.phaseZ()) / 173.0,
                0.0,
                (blockZ + group.phaseX()) / 173.0
        );
        double angle = Math.atan2(blockZ - group.centerZ(), blockX - group.centerX());
        double harmonic = 0.50 * Math.sin(angle * 2.0 + group.phaseX() * 0.01)
                + 0.30 * Math.sin(angle * 3.0 + group.phaseZ() * 0.01)
                + 0.20 * Math.sin(angle * 5.0 + (group.phaseX() + group.phaseZ()) * 0.01);
        return Mth.clamp(
                        coarse * 0.25 + detail * 0.15 + harmonic * 0.60,
                        -1.0,
                        1.0
                )
                * settings.edgeWarp();
    }

    ExosphereLatticeCell nearestCell(int blockX, int blockZ) {
        double localX = blockX * latticeCos + blockZ * latticeSin;
        double localZ = -blockX * latticeSin + blockZ * latticeCos;
        double fractionalZ = localZ * 2.0 / (SQRT_THREE * settings.cellSpacing());
        double fractionalX = localX / settings.cellSpacing() - fractionalZ * 0.5;
        return roundAxial(fractionalX, fractionalZ);
    }

    ExosphereLatticePoint latticeCenter(int cellX, int cellZ) {
        double localX = settings.cellSpacing() * (cellX + cellZ * 0.5);
        double localZ = settings.cellSpacing() * (SQRT_THREE * 0.5 * cellZ);
        return new ExosphereLatticePoint(
                localX * latticeCos - localZ * latticeSin,
                localX * latticeSin + localZ * latticeCos
        );
    }

    ExosphereGroupDescriptor groupDescriptor(int cellX, int cellZ) {
        long key = cellKey(cellX, cellZ);
        ExosphereGroupDescriptor existing = groupCache.get(key);
        if (existing != null) {
            return existing;
        }
        if (groupCache.size() >= MAX_CACHED_GROUPS) {
            synchronized (groupCache) {
                if (groupCache.size() >= MAX_CACHED_GROUPS) {
                    groupCache.clear();
                }
            }
        }
        return groupCache.computeIfAbsent(
                key,
                ignored -> createGroupDescriptor(cellX, cellZ)
        );
    }

    int cachedGroupCount() {
        return groupCache.size();
    }

    private ExosphereGroupDescriptor createGroupDescriptor(int cellX, int cellZ) {
        ExosphereLatticePoint latticeCenter = latticeCenter(cellX, cellZ);
        double layoutSample = layoutNoise.getValue(
                latticeCenter.x() / settings.cellSpacing(),
                0.0,
                latticeCenter.z() / settings.cellSpacing()
        );
        long entropy = mix64(
                layoutEntropy
                        ^ Double.doubleToLongBits(layoutSample)
                        ^ cellX * CELL_X_SALT
                        ^ cellZ * CELL_Z_SALT
        );
        SplittableRandom random = new SplittableRandom(entropy);
        double jitterAngle = random.nextDouble(0.0, Math.PI * 2.0);
        double jitterDistance = Math.sqrt(random.nextDouble()) * settings.centerJitter();
        double centerX = latticeCenter.x() + Math.cos(jitterAngle) * jitterDistance;
        double centerZ = latticeCenter.z() + Math.sin(jitterAngle) * jitterDistance;
        double radius = ranged(
                random,
                settings.minGroupRadius(),
                settings.maxGroupRadius()
        );
        return new ExosphereGroupDescriptor(
                cellX,
                cellZ,
                centerX,
                centerZ,
                radius,
                random.nextDouble(-8192.0, 8192.0),
                random.nextDouble(-8192.0, 8192.0)
        );
    }

    private static double ranged(SplittableRandom random, double minimum, double maximum) {
        if (minimum == maximum) {
            return minimum;
        }
        return random.nextDouble(minimum, maximum + Math.ulp(maximum));
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long)cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    private static long layoutEntropy(DensityFunction.NoiseHolder layoutNoise) {
        double first = layoutNoise.getValue(37.25, 0.0, -91.75);
        double second = layoutNoise.getValue(-113.5, 0.0, 53.25);
        return mix64(
                Double.doubleToLongBits(first)
                        ^ Long.rotateLeft(Double.doubleToLongBits(second), 29)
                        ^ LAYOUT_ROTATION_SALT
        );
    }

    private static ExosphereLatticeCell roundAxial(double cellX, double cellZ) {
        double cubeX = cellX;
        double cubeZ = cellZ;
        double cubeY = -cubeX - cubeZ;
        long roundedX = Math.round(cubeX);
        long roundedY = Math.round(cubeY);
        long roundedZ = Math.round(cubeZ);
        double differenceX = Math.abs(roundedX - cubeX);
        double differenceY = Math.abs(roundedY - cubeY);
        double differenceZ = Math.abs(roundedZ - cubeZ);

        if (differenceX > differenceY && differenceX > differenceZ) {
            roundedX = -roundedY - roundedZ;
        } else if (differenceY > differenceZ) {
            roundedY = -roundedX - roundedZ;
        } else {
            roundedZ = -roundedX - roundedY;
        }
        return new ExosphereLatticeCell((int)roundedX, (int)roundedZ);
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
        return visitor.apply(new ExosphereHybridDensityFunction(
                baseNoise.mapAll(visitor),
                visitor.visitNoise(layoutNoise),
                visitor.visitNoise(edgeNoise),
                settings
        ));
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 11.0 / 24.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}

record ExosphereGroupDescriptor(
        int cellX,
        int cellZ,
        double centerX,
        double centerZ,
        double radius,
        double phaseX,
        double phaseZ
) {
}

record ExosphereLatticeCell(int cellX, int cellZ) {
}

record ExosphereLatticePoint(double x, double z) {
}

package com.kuronami.skyworld.worldgen.density;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class CaveBiomeDepthDensityFunction implements DensityFunction {
    public static final MapCodec<CaveBiomeDepthDensityFunction> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("terrain")
                            .forGetter(CaveBiomeDepthDensityFunction::terrain),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("envelope")
                            .forGetter(CaveBiomeDepthDensityFunction::envelope),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("selector")
                            .forGetter(CaveBiomeDepthDensityFunction::selector),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("vegetation")
                            .forGetter(CaveBiomeDepthDensityFunction::vegetation),
                    Codec.DOUBLE.fieldOf("boundary_band")
                            .forGetter(CaveBiomeDepthDensityFunction::boundaryBand),
                    Codec.DOUBLE.fieldOf("exposure_depth")
                            .forGetter(CaveBiomeDepthDensityFunction::exposureDepth),
                    Codec.DOUBLE.fieldOf("wet_chance")
                            .forGetter(CaveBiomeDepthDensityFunction::wetChance),
                    Codec.DOUBLE.fieldOf("dry_chance")
                            .forGetter(CaveBiomeDepthDensityFunction::dryChance)
            ).apply(instance, CaveBiomeDepthDensityFunction::new));

    private static final KeyDispatchDataCodec<CaveBiomeDepthDensityFunction> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);
    private static final long X_SALT = 0x9E3779B97F4A7C15L;
    private static final long Y_SALT = 0xD1B54A32D192ED03L;
    private static final long Z_SALT = 0x94D049BB133111EBL;

    private final DensityFunction terrain;
    private final DensityFunction envelope;
    private final DensityFunction selector;
    private final DensityFunction vegetation;
    private final double boundaryBand;
    private final double exposureDepth;
    private final double wetChance;
    private final double dryChance;

    public CaveBiomeDepthDensityFunction(
            DensityFunction terrain,
            DensityFunction envelope,
            DensityFunction selector,
            DensityFunction vegetation,
            double boundaryBand,
            double exposureDepth,
            double wetChance,
            double dryChance
    ) {
        this.terrain = terrain;
        this.envelope = envelope;
        this.selector = selector;
        this.vegetation = vegetation;
        this.boundaryBand = boundaryBand;
        this.exposureDepth = exposureDepth;
        this.wetChance = wetChance;
        this.dryChance = dryChance;
    }

    DensityFunction terrain() {
        return terrain;
    }

    DensityFunction envelope() {
        return envelope;
    }

    DensityFunction selector() {
        return selector;
    }

    DensityFunction vegetation() {
        return vegetation;
    }

    double boundaryBand() {
        return boundaryBand;
    }

    double exposureDepth() {
        return exposureDepth;
    }

    double wetChance() {
        return wetChance;
    }

    double dryChance() {
        return dryChance;
    }

    @Override
    public double compute(FunctionContext context) {
        double normalDepth = Mth.clamp(terrain.compute(context), -0.005, 1.0);
        if (Math.abs(envelope.compute(context)) > boundaryBand) {
            return normalDepth;
        }

        double chance = vegetation.compute(context) > 0.0 ? wetChance : dryChance;
        long entropy = Double.doubleToLongBits(selector.compute(context));
        entropy ^= context.blockX() * X_SALT;
        entropy ^= context.blockY() * Y_SALT;
        entropy ^= context.blockZ() * Z_SALT;
        double unit = (mix64(entropy) >>> 11) * 0x1.0p-53;
        return unit < chance ? Math.max(normalDepth, exposureDepth) : normalDepth;
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
        return visitor.apply(new CaveBiomeDepthDensityFunction(
                terrain.mapAll(visitor),
                envelope.mapAll(visitor),
                selector.mapAll(visitor),
                vegetation.mapAll(visitor),
                boundaryBand,
                exposureDepth,
                wetChance,
                dryChance
        ));
    }

    @Override
    public double minValue() {
        return -0.005;
    }

    @Override
    public double maxValue() {
        return Math.max(1.0, exposureDepth);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}

package com.kuronami.skyworld.worldgen.density;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExosphereHybridDensityFunctionTest {
    private static final ExosphereHybridSettings SETTINGS =
            ExosphereHybridSettings.defaults();

    @Test
    void codecIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "sky_world",
                "exosphere_hybrid"
        );

        assertTrue(BuiltInRegistries.DENSITY_FUNCTION_TYPE.containsKey(id));
        assertSame(
                ExosphereHybridDensityFunction.CODEC,
                BuiltInRegistries.DENSITY_FUNCTION_TYPE.get(id)
        );
    }

    @Test
    void scalesOnlyTheCoordinatesPassedToBaseNoise() {
        DensityFunction.FunctionContext scaled = ExosphereHybridDensityFunction.scaledContext(
                new DensityFunction.SinglePointContext(301, 96, -149),
                SETTINGS
        );

        assertEquals(100, scaled.blockX());
        assertEquals(96, scaled.blockY());
        assertEquals(-50, scaled.blockZ());
    }

    @Test
    void seededGroupDescriptorsAreDeterministicAndJittered() {
        ExosphereHybridDensityFunction first = function(112233L);
        ExosphereHybridDensityFunction same = function(112233L);
        ExosphereHybridDensityFunction different = function(998877L);

        for (int cellX = -8; cellX <= 8; cellX++) {
            for (int cellZ = -8; cellZ <= 8; cellZ++) {
                ExosphereGroupDescriptor descriptor = first.groupDescriptor(cellX, cellZ);
                assertEquals(descriptor, same.groupDescriptor(cellX, cellZ));
                assertTrue(Math.abs(descriptor.centerX() - cellX * SETTINGS.cellSpacing())
                        <= SETTINGS.centerJitter());
                assertTrue(Math.abs(descriptor.centerZ() - cellZ * SETTINGS.cellSpacing())
                        <= SETTINGS.centerJitter());
                assertTrue(descriptor.radius() >= SETTINGS.minGroupRadius());
                assertTrue(descriptor.radius() <= SETTINGS.maxGroupRadius());
            }
        }

        assertNotEquals(first.groupDescriptor(3, -4), different.groupDescriptor(3, -4));
    }

    @Test
    void groupFieldDoesNotResetAcrossCellBorders() {
        ExosphereHybridDensityFunction function = function(556677L);
        int halfCell = SETTINGS.cellSpacing() / 2;
        double largestJump = 0.0;

        for (int border = -3; border <= 3; border++) {
            int borderX = border * SETTINGS.cellSpacing() + halfCell;
            for (int z = -SETTINGS.cellSpacing(); z <= SETTINGS.cellSpacing(); z += 16) {
                double left = function.groupBias(borderX - 1, z);
                double right = function.groupBias(borderX + 1, z);
                largestJump = Math.max(largestJump, Math.abs(left - right));
            }
        }

        assertTrue(largestJump < 0.03, "group seam jump=" + largestJump);
    }

    @Test
    void farOutsideEveryGroupSaturatesToNegativeVoidBias() {
        ExosphereHybridDensityFunction function = function(778899L);
        int halfCell = SETTINGS.cellSpacing() / 2;

        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellZ = -5; cellZ <= 5; cellZ++) {
                double bias = function.groupBias(
                        cellX * SETTINGS.cellSpacing() + halfCell,
                        cellZ * SETTINGS.cellSpacing() + halfCell
                );
                assertTrue(bias <= -SETTINGS.voidStrength() * 0.80, "bias=" + bias);
            }
        }
    }

    @Test
    void descriptorCacheIsBounded() {
        ExosphereHybridDensityFunction function = function(443322L);

        for (int index = 0;
             index < ExosphereHybridDensityFunction.MAX_CACHED_GROUPS * 2;
             index++) {
            function.groupDescriptor(index, -index);
        }

        assertTrue(function.cachedGroupCount()
                <= ExosphereHybridDensityFunction.MAX_CACHED_GROUPS);
    }

    @Test
    void hardVerticalLimitsLeaveTheRemainingOverworldEmpty() {
        ExosphereHybridDensityFunction function = function(12345L);

        assertEquals(-1.0, sample(function, 0, -65, 0));
        assertEquals(-1.0, sample(function, 0, 256, 0));
    }

    @Test
    void ordinaryNeighboringGroupsKeepCreateScaleVoidCrossings() {
        ExosphereHybridDensityFunction function = function(1357911L);
        int eligible = 0;
        int inRange = 0;

        for (int cellX = -50; cellX < 50; cellX++) {
            for (int cellZ = -50; cellZ <= 50; cellZ++) {
                ExosphereGroupDescriptor left = function.groupDescriptor(cellX, cellZ);
                ExosphereGroupDescriptor right = function.groupDescriptor(cellX + 1, cellZ);
                double gap = Math.hypot(
                        left.centerX() - right.centerX(),
                        left.centerZ() - right.centerZ()
                ) - left.radius() - right.radius();
                eligible++;
                if (gap >= 200.0 && gap <= 800.0) {
                    inRange++;
                }
            }
        }

        assertTrue(inRange / (double)eligible >= 0.90,
                "travel-range crossings=" + inRange + "/" + eligible);
    }

    @Test
    void naturalBoundaryWarpProducesIrregularContinentalFootprints() {
        int irregular = 0;
        int expectedDiameter = 0;
        int samples = 64;

        for (long seed = 0; seed < samples; seed++) {
            ExosphereHybridDensityFunction function = function(seed);
            ExosphereGroupDescriptor group = function.groupDescriptor(0, 0);
            int[] radii = new int[48];
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;
            for (int index = 0; index < radii.length; index++) {
                double angle = index * Math.PI * 2.0 / radii.length;
                radii[index] = radialExtent(function, group, angle);
                minimum = Math.min(minimum, radii[index]);
                maximum = Math.max(maximum, radii[index]);
            }

            int diameter = 0;
            for (int index = 0; index < radii.length / 2; index++) {
                diameter = Math.max(diameter, radii[index] + radii[index + radii.length / 2]);
            }
            if (maximum - minimum >= 144) {
                irregular++;
            }
            if (diameter >= 1_000 && diameter <= 1_500) {
                expectedDiameter++;
            }
        }

        assertTrue(irregular / (double)samples >= 0.85,
                "irregular footprints=" + irregular + "/" + samples);
        assertTrue(expectedDiameter / (double)samples >= 0.75,
                "expected-size footprints=" + expectedDiameter + "/" + samples);
    }

    private static ExosphereHybridDensityFunction function(long seed) {
        return new ExosphereHybridDensityFunction(
                DensityFunctions.constant(0.0),
                noise(seed, -7),
                noise(seed ^ 0x9E3779B97F4A7C15L, -5),
                SETTINGS
        );
    }

    private static DensityFunction.NoiseHolder noise(long seed, int firstOctave) {
        NormalNoise.NoiseParameters parameters =
                new NormalNoise.NoiseParameters(firstOctave, 1.0, 0.5, 0.25);
        return new DensityFunction.NoiseHolder(
                Holder.direct(parameters),
                NormalNoise.create(RandomSource.create(seed), parameters)
        );
    }

    private static double sample(
            ExosphereHybridDensityFunction function,
            int x,
            int y,
            int z
    ) {
        return function.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    private static int radialExtent(
            ExosphereHybridDensityFunction function,
            ExosphereGroupDescriptor group,
            double angle
    ) {
        int furthest = 0;
        int maximum = (int)Math.ceil(
                group.radius() + SETTINGS.edgeWarp() + SETTINGS.groupTransition()
        );
        for (int radius = 0; radius <= maximum; radius += 4) {
            int x = (int)Math.round(group.centerX() + Math.cos(angle) * radius);
            int z = (int)Math.round(group.centerZ() + Math.sin(angle) * radius);
            if (function.groupBias(x, z) > 0.062475) {
                furthest = radius;
            }
        }
        return furthest;
    }
}

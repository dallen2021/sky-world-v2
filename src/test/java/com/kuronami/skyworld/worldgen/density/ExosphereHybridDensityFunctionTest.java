package com.kuronami.skyworld.worldgen.density;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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
                ExosphereLatticePoint latticeCenter = first.latticeCenter(cellX, cellZ);
                assertEquals(descriptor, same.groupDescriptor(cellX, cellZ));
                assertTrue(Math.hypot(
                        descriptor.centerX() - latticeCenter.x(),
                        descriptor.centerZ() - latticeCenter.z()
                ) <= SETTINGS.centerJitter());
                assertTrue(descriptor.radius() >= SETTINGS.minGroupRadius());
                assertTrue(descriptor.radius() <= SETTINGS.maxGroupRadius());
            }
        }

        assertNotEquals(first.groupDescriptor(3, -4), different.groupDescriptor(3, -4));
    }

    @Test
    void groupFieldStaysContinuousAcrossTriangularNeighborMidpoints() {
        ExosphereHybridDensityFunction function = function(556677L);
        double largestJump = 0.0;
        int[][] neighbors = {
                {1, 0}, {0, 1}, {-1, 1},
                {-1, 0}, {0, -1}, {1, -1}
        };

        for (int cellX = -3; cellX <= 3; cellX++) {
            for (int cellZ = -3; cellZ <= 3; cellZ++) {
                ExosphereGroupDescriptor group = function.groupDescriptor(cellX, cellZ);
                for (int[] offset : neighbors) {
                    ExosphereGroupDescriptor neighbor = function.groupDescriptor(
                            cellX + offset[0],
                            cellZ + offset[1]
                    );
                    double midpointX = (group.centerX() + neighbor.centerX()) * 0.5;
                    double midpointZ = (group.centerZ() + neighbor.centerZ()) * 0.5;
                    double distance = Math.hypot(
                            neighbor.centerX() - group.centerX(),
                            neighbor.centerZ() - group.centerZ()
                    );
                    double normalX = (neighbor.centerX() - group.centerX()) / distance;
                    double normalZ = (neighbor.centerZ() - group.centerZ()) / distance;
                    double first = function.groupBias(
                            (int)Math.round(midpointX - normalX),
                            (int)Math.round(midpointZ - normalZ)
                    );
                    double second = function.groupBias(
                            (int)Math.round(midpointX + normalX),
                            (int)Math.round(midpointZ + normalZ)
                    );
                    largestJump = Math.max(largestJump, Math.abs(first - second));
                }
            }
        }

        assertTrue(largestJump < 0.03, "group seam jump=" + largestJump);
    }

    @Test
    void farOutsideEveryGroupSaturatesToNegativeVoidBias() {
        ExosphereHybridDensityFunction function = function(778899L);

        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellZ = -5; cellZ <= 5; cellZ++) {
                ExosphereLatticePoint first = function.latticeCenter(cellX, cellZ);
                ExosphereLatticePoint second = function.latticeCenter(cellX + 1, cellZ);
                ExosphereLatticePoint third = function.latticeCenter(cellX, cellZ + 1);
                double bias = function.groupBias(
                        (int)Math.round((first.x() + second.x() + third.x()) / 3.0),
                        (int)Math.round((first.z() + second.z() + third.z()) / 3.0)
                );
                assertTrue(bias <= -SETTINGS.voidStrength() * 0.80, "bias=" + bias);
            }
        }
    }

    @Test
    void groupInteriorKeepsTheOriginalExosphereVerticalVariation() {
        ExosphereHybridDensityFunction function = function(778899L);

        for (int cellX = -5; cellX <= 5; cellX++) {
            for (int cellZ = -5; cellZ <= 5; cellZ++) {
                ExosphereGroupDescriptor group = function.groupDescriptor(cellX, cellZ);
                double bias = function.groupBias(
                        (int)Math.round(group.centerX()),
                        (int)Math.round(group.centerZ())
                );
                assertTrue(bias > 0.0, "interior bias=" + bias);
                assertTrue(bias <= 0.02, "interior bias=" + bias);
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
    void neighboringGroupsReserveAtLeastFifteenHundredBlocksOfFullVoidInfluence() {
        int[][] forwardNeighbors = {
                {1, 0},
                {0, 1},
                {-1, 1}
        };
        double minimumGap = Double.MAX_VALUE;
        double guaranteedLowerBound = SETTINGS.cellSpacing()
                - SETTINGS.centerJitter() * 2.0
                - (SETTINGS.maxGroupRadius()
                + SETTINGS.edgeWarp()
                + SETTINGS.groupTransition()) * 2.0;

        assertTrue(guaranteedLowerBound >= 1_500.0,
                "configured full-influence lower bound=" + guaranteedLowerBound);

        for (long seed = 0; seed < 16; seed++) {
            ExosphereHybridDensityFunction function = function(seed);
            for (int cellX = -8; cellX <= 8; cellX++) {
                for (int cellZ = -8; cellZ <= 8; cellZ++) {
                    ExosphereGroupDescriptor group = function.groupDescriptor(cellX, cellZ);
                    for (int[] offset : forwardNeighbors) {
                        ExosphereGroupDescriptor neighbor = function.groupDescriptor(
                                cellX + offset[0],
                                cellZ + offset[1]
                        );
                        double gap = Math.hypot(
                                group.centerX() - neighbor.centerX(),
                                group.centerZ() - neighbor.centerZ()
                        ) - group.radius() - neighbor.radius()
                                - SETTINGS.edgeWarp() * 2.0
                                - SETTINGS.groupTransition() * 2.0;
                        minimumGap = Math.min(minimumGap, gap);
                    }
                }
            }
        }

        assertTrue(minimumGap >= 1_500.0, "minimum full-influence gap=" + minimumGap);
    }

    @Test
    void unitBaseNoiseCannotLeakTerrainIntoReservedVoidCorridors() {
        int[][] forwardNeighbors = {
                {1, 0},
                {0, 1},
                {-1, 1}
        };

        for (long seed = 0; seed < 8; seed++) {
            ExosphereHybridDensityFunction function = function(seed, 1.0);
            ExosphereGroupDescriptor group = function.groupDescriptor(0, 0);
            for (int[] offset : forwardNeighbors) {
                ExosphereGroupDescriptor neighbor = function.groupDescriptor(
                        offset[0],
                        offset[1]
                );
                int midpointX = (int)Math.round((group.centerX() + neighbor.centerX()) * 0.5);
                int midpointZ = (int)Math.round((group.centerZ() + neighbor.centerZ()) * 0.5);
                for (int y = ExosphereDensityProfile.MIN_Y;
                     y <= ExosphereDensityProfile.MAX_Y;
                     y += 8) {
                    assertTrue(sample(function, midpointX, y, midpointZ) < 0.0,
                            "solid corridor sample at " + midpointX + "," + y + "," + midpointZ);
                }
            }
        }
    }

    @Test
    void sixNearestGroupsHaveTriangularRatherThanSquareGridSpacing() {
        int matchingSeeds = 0;

        for (long seed = 0; seed < 32; seed++) {
            ExosphereHybridDensityFunction function = function(seed);
            ExosphereGroupDescriptor origin = function.groupDescriptor(0, 0);
            double[] distances = new double[24];
            int index = 0;
            for (int cellX = -2; cellX <= 2; cellX++) {
                for (int cellZ = -2; cellZ <= 2; cellZ++) {
                    if (cellX == 0 && cellZ == 0) {
                        continue;
                    }
                    ExosphereGroupDescriptor group = function.groupDescriptor(cellX, cellZ);
                    distances[index++] = Math.hypot(
                            origin.centerX() - group.centerX(),
                            origin.centerZ() - group.centerZ()
                    );
                }
            }
            Arrays.sort(distances);
            if (distances[5] / distances[0] < 1.25) {
                matchingSeeds++;
            }
        }

        assertTrue(matchingSeeds >= 30, "triangular nearest rings=" + matchingSeeds + "/32");
    }

    @Test
    void nearestGroupBearingIsNotLockedToCardinalAxes() {
        int nonCardinalSeeds = 0;

        for (long seed = 0; seed < 64; seed++) {
            ExosphereHybridDensityFunction function = function(seed);
            ExosphereGroupDescriptor origin = function.groupDescriptor(0, 0);
            double nearestDistance = Double.MAX_VALUE;
            double nearestBearing = 0.0;

            for (int cellX = -2; cellX <= 2; cellX++) {
                for (int cellZ = -2; cellZ <= 2; cellZ++) {
                    if (cellX == 0 && cellZ == 0) {
                        continue;
                    }
                    ExosphereGroupDescriptor group = function.groupDescriptor(cellX, cellZ);
                    double dx = group.centerX() - origin.centerX();
                    double dz = group.centerZ() - origin.centerZ();
                    double distance = Math.hypot(dx, dz);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestBearing = Math.toDegrees(Math.atan2(dz, dx));
                    }
                }
            }

            double normalized = Math.floorMod((int)Math.round(nearestBearing), 90);
            double cardinalOffset = Math.min(normalized, 90.0 - normalized);
            if (cardinalOffset > 10.0) {
                nonCardinalSeeds++;
            }
        }

        assertTrue(nonCardinalSeeds >= 40,
                "non-cardinal nearest bearings=" + nonCardinalSeeds + "/64");
    }

    @Test
    void naturalBoundaryWarpProducesIrregularContinentalFootprints() {
        int irregular = 0;
        int expectedDiameter = 0;
        int samples = 64;

        for (long seed = 0; seed < samples; seed++) {
            ExosphereHybridDensityFunction function = function(seed);
            ExosphereGroupDescriptor group = function.groupDescriptor(0, 0);
            double coreThreshold = function.groupBias(
                    (int)Math.round(group.centerX()),
                    (int)Math.round(group.centerZ())
            ) * 0.65;
            int[] radii = new int[48];
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;
            for (int index = 0; index < radii.length; index++) {
                double angle = index * Math.PI * 2.0 / radii.length;
                radii[index] = radialExtent(function, group, angle, coreThreshold);
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
        return function(seed, 0.0);
    }

    private static ExosphereHybridDensityFunction function(long seed, double baseDensity) {
        return new ExosphereHybridDensityFunction(
                DensityFunctions.constant(baseDensity),
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
            double angle,
            double coreThreshold
    ) {
        int furthest = 0;
        int maximum = (int)Math.ceil(
                group.radius() + SETTINGS.edgeWarp() + SETTINGS.groupTransition()
        );
        for (int radius = 0; radius <= maximum; radius += 4) {
            int x = (int)Math.round(group.centerX() + Math.cos(angle) * radius);
            int z = (int)Math.round(group.centerZ() + Math.sin(angle) * radius);
            if (function.groupBias(x, z) > coreThreshold) {
                furthest = radius;
            }
        }
        return furthest;
    }
}

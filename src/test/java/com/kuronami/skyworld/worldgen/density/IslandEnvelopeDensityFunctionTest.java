package com.kuronami.skyworld.worldgen.density;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IslandEnvelopeDensityFunctionTest {
    private static final IslandEnvelopeSettings SETTINGS = IslandEnvelopeSettings.defaults();

    @Test
    void densityFunctionCodecIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "sky_world",
                "island_envelope"
        );

        assertTrue(BuiltInRegistries.DENSITY_FUNCTION_TYPE.containsKey(id));
        assertEquals(
                IslandEnvelopeDensityFunction.CODEC,
                BuiltInRegistries.DENSITY_FUNCTION_TYPE.get(id)
        );
    }

    @Test
    void layoutIsDeterministicForASeedAndChangesAcrossSeeds() {
        IslandEnvelopeDensityFunction first = function(1234L);
        IslandEnvelopeDensityFunction same = function(1234L);
        IslandEnvelopeDensityFunction different = function(9876L);

        for (int cellX = -8; cellX <= 8; cellX++) {
            for (int cellZ = -8; cellZ <= 8; cellZ++) {
                assertEquals(
                        first.cellDescriptor(cellX, cellZ),
                        same.cellDescriptor(cellX, cellZ)
                );
            }
        }

        assertNotEquals(first.cellDescriptor(3, -4), different.cellDescriptor(3, -4));
    }

    @Test
    void originCellAlwaysProvidesAContinentalSpawnIsland() {
        for (long seed = 0; seed < 100; seed++) {
            IslandEnvelopeDensityFunction function = function(seed);
            assertEquals(
                    IslandArchetype.CONTINENTAL,
                    function.cellDescriptor(0, 0).archetype()
            );
            assertTrue(sample(function, 0, SETTINGS.shoulderY(), 0) > 0.0);
        }
    }

    @Test
    void archetypeDistributionMatchesContinentalDefaults() {
        IslandEnvelopeDensityFunction function = function(44332211L);
        Map<IslandArchetype, Integer> counts = new EnumMap<>(IslandArchetype.class);
        int samples = 10_000;

        for (int index = 0; index < samples; index++) {
            int cellX = index % 100;
            int cellZ = index / 100;
            counts.merge(function.cellDescriptor(cellX, cellZ).archetype(), 1, Integer::sum);
        }

        assertFraction(counts, IslandArchetype.CONTINENTAL, samples, 0.55, 0.03);
        assertFraction(counts, IslandArchetype.MEDIUM, samples, 0.20, 0.03);
        assertFraction(counts, IslandArchetype.ARCHIPELAGO, samples, 0.20, 0.03);
        assertFraction(counts, IslandArchetype.SMALL, samples, 0.05, 0.02);
    }

    @Test
    void envelopeHasHardVerticalBoundsAndTapersBelowTheShoulder() {
        IslandEnvelopeDensityFunction function = function(24680L);
        IslandCellDescriptor cell = function.cellDescriptor(0, 0);
        IslandComponent component = cell.components().getFirst();
        int x = (int)Math.round(component.centerX());
        int z = (int)Math.round(component.centerZ());

        int shoulder = positiveExtent(function, x, SETTINGS.shoulderY(), z);
        int middle = positiveExtent(function, x, 24, z);
        int nearBottom = positiveExtent(function, x, SETTINGS.bottomY() + 1, z);

        assertTrue(shoulder > middle, "the island radius must shrink below the shoulder");
        assertTrue(middle > nearBottom, "the taper must continue toward the bottom tip");
        assertTrue(sample(function, x, SETTINGS.bottomY() - 1, z) < 0.0);
        assertTrue(sample(function, x, SETTINGS.topY(), z) < 0.0);
    }

    @Test
    void densityFieldDoesNotResetAcrossMacroCellBorders() {
        IslandEnvelopeDensityFunction function = function(112233L);
        int cellSize = SETTINGS.cellSize();
        double largestJump = 0.0;

        for (int border = -3; border <= 3; border++) {
            int borderX = border * cellSize + cellSize / 2;
            for (int z = -cellSize * 2; z <= cellSize * 2; z += 16) {
                double left = sample(function, borderX - 1, SETTINGS.shoulderY(), z);
                double right = sample(function, borderX + 1, SETTINGS.shoulderY(), z);
                largestJump = Math.max(largestJump, Math.abs(left - right));
            }
        }

        assertTrue(largestJump < 0.1,
                "the field must remain continuous when the base macro cell changes");
    }

    @Test
    void cellDescriptorCacheIsBounded() {
        IslandEnvelopeDensityFunction function = function(998877L);

        for (int index = 0; index < IslandEnvelopeDensityFunction.MAX_CACHED_CELLS * 2; index++) {
            function.cellDescriptor(index, -index);
        }

        assertTrue(function.cachedCellCount() <= IslandEnvelopeDensityFunction.MAX_CACHED_CELLS);
    }

    @Test
    void generatedGroupsKeepTheirConfiguredComponentCounts() {
        IslandEnvelopeDensityFunction function = function(55667788L);

        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                IslandCellDescriptor cell = function.cellDescriptor(cellX, cellZ);
                ArchetypeSettings config = SETTINGS.settingsFor(cell.archetype());
                assertTrue(cell.components().size() >= config.minCount(), cell.toString());
                assertTrue(cell.components().size() <= config.maxCount(), cell.toString());
            }
        }
    }

    @Test
    void ordinaryInterGroupGapsUsuallyFallInsideTravelRange() {
        IslandEnvelopeDensityFunction function = function(1357911L);
        int eligible = 0;
        int inRange = 0;
        int tooClose = 0;
        int tooFar = 0;

        int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int cellX = -30; cellX <= 30; cellX++) {
            for (int cellZ = -30; cellZ <= 30; cellZ++) {
                IslandCellDescriptor cell = function.cellDescriptor(cellX, cellZ);
                if (cell.archetype() == IslandArchetype.ARCHIPELAGO) {
                    continue;
                }
                double nearestGap = Double.POSITIVE_INFINITY;
                for (int[] offset : neighbors) {
                    IslandCellDescriptor neighbor = function.cellDescriptor(
                            cellX + offset[0],
                            cellZ + offset[1]
                    );
                    if (neighbor.archetype() != IslandArchetype.ARCHIPELAGO) {
                        nearestGap = Math.min(
                                nearestGap,
                                minimumEdgeGap(cell.components(), neighbor.components())
                        );
                    }
                }
                eligible++;
                if (nearestGap >= 200.0 && nearestGap <= 800.0) {
                    inRange++;
                } else if (nearestGap < 200.0) {
                    tooClose++;
                } else {
                    tooFar++;
                }
            }
        }

        assertTrue(inRange / (double)eligible >= 0.90,
                "expected 90% travel-range gaps but found " + inRange + "/" + eligible
                        + " (too close=" + tooClose + ", too far=" + tooFar + ")");
    }

    @Test
    void archipelagoMembersStayCloserThanOrdinaryIslandGroups() {
        IslandEnvelopeDensityFunction function = function(8642097L);
        int checked = 0;

        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                IslandCellDescriptor cell = function.cellDescriptor(cellX, cellZ);
                if (cell.archetype() != IslandArchetype.ARCHIPELAGO) {
                    continue;
                }
                for (IslandComponent component : cell.components()) {
                    double nearest = minimumEdgeGap(
                            List.of(component),
                            cell.components().stream().filter(other -> other != component).toList()
                    );
                    assertTrue(nearest < 200.0, "detached archipelago member: " + nearest);
                }
                checked++;
            }
        }

        assertTrue(checked > 100);
    }

    private static IslandEnvelopeDensityFunction function(long seed) {
        return new IslandEnvelopeDensityFunction(
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
            IslandEnvelopeDensityFunction function,
            int x,
            int y,
            int z
    ) {
        return function.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    private static int positiveExtent(
            IslandEnvelopeDensityFunction function,
            int centerX,
            int y,
            int z
    ) {
        int extent = 0;
        while (extent <= 1600 && sample(function, centerX + extent, y, z) > 0.0) {
            extent += 4;
        }
        return extent;
    }

    private static void assertFraction(
            Map<IslandArchetype, Integer> counts,
            IslandArchetype archetype,
            int total,
            double expected,
            double tolerance
    ) {
        double actual = counts.getOrDefault(archetype, 0) / (double)total;
        assertTrue(
                Math.abs(actual - expected) <= tolerance,
                archetype + " expected " + expected + " ± " + tolerance + " but was " + actual
        );
    }

    private static double minimumEdgeGap(
            List<IslandComponent> first,
            List<IslandComponent> second
    ) {
        double minimum = Double.POSITIVE_INFINITY;
        for (IslandComponent left : first) {
            for (IslandComponent right : second) {
                double gap = Math.hypot(
                        left.centerX() - right.centerX(),
                        left.centerZ() - right.centerZ()
                ) - left.maxRadius() - right.maxRadius();
                minimum = Math.min(minimum, gap);
            }
        }
        return minimum;
    }
}

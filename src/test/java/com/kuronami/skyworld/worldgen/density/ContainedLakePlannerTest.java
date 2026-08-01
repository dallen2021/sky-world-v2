package com.kuronami.skyworld.worldgen.density;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContainedLakePlannerTest {
    @Test
    void candidatesAreDeterministicAndOnlyUseEligibleArchetypes() {
        IslandEnvelopeDensityFunction envelope = envelope(741852L);
        boolean sawContinentalLake = false;
        boolean sawMediumLake = false;

        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                IslandCellDescriptor cell = envelope.cellDescriptor(cellX, cellZ);
                List<ContainedLakePlanner.LakeCandidate> first =
                        ContainedLakePlanner.candidatesForCell(envelope, cellX, cellZ);
                List<ContainedLakePlanner.LakeCandidate> second =
                        ContainedLakePlanner.candidatesForCell(envelope, cellX, cellZ);
                assertEquals(first, second);

                if (cell.archetype() == IslandArchetype.CONTINENTAL) {
                    assertTrue(first.size() <= 3);
                    sawContinentalLake |= !first.isEmpty();
                } else if (cell.archetype() == IslandArchetype.MEDIUM) {
                    assertTrue(first.size() <= 1);
                    sawMediumLake |= !first.isEmpty();
                } else {
                    assertTrue(first.isEmpty());
                }

                for (ContainedLakePlanner.LakeCandidate candidate : first) {
                    assertTrue(candidate.radius() >= 12 && candidate.radius() <= 32);
                    assertTrue(candidate.depth() >= 4 && candidate.depth() <= 12);
                }
            }
        }

        assertTrue(sawContinentalLake);
        assertTrue(sawMediumLake);
    }

    @Test
    void fullShorelineAndSafetyMarginMustRemainInsideDeepEnvelope() {
        IslandEnvelopeDensityFunction envelope = envelope(963258L);
        IslandCellDescriptor continental = findCell(envelope, IslandArchetype.CONTINENTAL);
        IslandComponent component = continental.components().getFirst();
        ContainedLakePlanner.LakeCandidate centered = new ContainedLakePlanner.LakeCandidate(
                (int)Math.round(component.centerX()),
                (int)Math.round(component.centerZ()),
                32,
                12
        );
        ContainedLakePlanner.LakeCandidate nearEdge = new ContainedLakePlanner.LakeCandidate(
                (int)Math.round(component.centerX() + component.radiusX() - 10),
                (int)Math.round(component.centerZ()),
                32,
                12
        );

        assertTrue(ContainedLakePlanner.hasEnvelopeSafety(centered, 112, envelope));
        assertFalse(ContainedLakePlanner.hasEnvelopeSafety(nearEdge, 112, envelope));
    }

    @Test
    void shorelineSamplerLeavesNoLargeArcGaps() {
        List<BlockPos> shoreline = ContainedLakePlanner.ringPoints(20, -40, 32);
        assertTrue(shoreline.size() >= 190);

        for (int index = 0; index < shoreline.size(); index++) {
            BlockPos current = shoreline.get(index);
            BlockPos next = shoreline.get((index + 1) % shoreline.size());
            assertTrue(Math.abs(current.getX() - next.getX()) <= 2);
            assertTrue(Math.abs(current.getZ() - next.getZ()) <= 2);
        }
    }

    @Test
    void envelopeCanBeRecoveredThroughInterpolatedCombinedDensity() {
        IslandEnvelopeDensityFunction envelope = envelope(159357L);
        DensityFunction combined = DensityFunctions.min(
                DensityFunctions.constant(0.5),
                DensityFunctions.interpolated(envelope)
        );

        assertNotNull(ContainedLakePlanner.findEnvelope(combined));
    }

    @Test
    void hybridGroupsProduceDeterministicContainedLakeCandidates() {
        ExosphereHybridDensityFunction hybrid = hybrid(24681357L);
        ContainedLakePlanner.TerrainShape shape = ContainedLakePlanner.findShape(
                DensityFunctions.interpolated(hybrid)
        );

        assertNotNull(shape);
        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                List<ContainedLakePlanner.LakeCandidate> first =
                        ContainedLakePlanner.candidatesForCell(shape, cellX, cellZ);
                List<ContainedLakePlanner.LakeCandidate> second =
                        ContainedLakePlanner.candidatesForCell(shape, cellX, cellZ);
                assertEquals(first, second);
                assertTrue(first.size() <= 3);
                for (ContainedLakePlanner.LakeCandidate candidate : first) {
                    assertTrue(candidate.radius() >= 12 && candidate.radius() <= 32);
                    assertTrue(candidate.depth() >= 4 && candidate.depth() <= 12);
                }
            }
        }
    }

    @Test
    void hybridLakeEnumerationFindsCandidatesOnTheRotatedLattice() {
        ExosphereHybridDensityFunction hybrid = hybrid(11235813L);
        ContainedLakePlanner.TerrainShape shape = ContainedLakePlanner.findShape(hybrid);
        int candidates = 0;
        int recovered = 0;

        assertNotNull(shape);
        for (int cellX = -12; cellX <= 12; cellX++) {
            for (int cellZ = -12; cellZ <= 12; cellZ++) {
                for (ContainedLakePlanner.LakeCandidate candidate :
                        ContainedLakePlanner.candidatesForCell(shape, cellX, cellZ)) {
                    candidates++;
                    List<ContainedLakePlanner.LakeCandidate> intersecting =
                            ContainedLakePlanner.candidatesIntersecting(
                                    shape,
                                    candidate.centerX() - candidate.radius(),
                                    candidate.centerZ() - candidate.radius(),
                                    candidate.centerX() + candidate.radius(),
                                    candidate.centerZ() + candidate.radius()
                            );
                    if (intersecting.contains(candidate)) {
                        recovered++;
                    }
                }
            }
        }

        assertTrue(candidates > 100, "sampled candidates=" + candidates);
        assertEquals(candidates, recovered,
                "rotated-lattice lake candidates must remain discoverable by world bounds");
    }

    @Test
    void hybridLakeSafetyRejectsCandidatesThatApproachVoid() {
        ExosphereHybridDensityFunction hybrid = hybrid(97531864L, 0.06);
        ContainedLakePlanner.TerrainShape shape = ContainedLakePlanner.findShape(hybrid);
        ExosphereGroupDescriptor group = hybrid.groupDescriptor(0, 0);
        ContainedLakePlanner.LakeCandidate centered = new ContainedLakePlanner.LakeCandidate(
                (int)Math.round(group.centerX()),
                (int)Math.round(group.centerZ()),
                12,
                4
        );
        ContainedLakePlanner.LakeCandidate atEdge = new ContainedLakePlanner.LakeCandidate(
                (int)Math.round(group.centerX() + group.radius()),
                (int)Math.round(group.centerZ()),
                32,
                12
        );

        assertNotNull(shape);
        assertTrue(ContainedLakePlanner.hasEnvelopeSafety(centered, 128, shape));
        assertFalse(ContainedLakePlanner.hasEnvelopeSafety(atEdge, 128, shape));
    }

    private static IslandCellDescriptor findCell(
            IslandEnvelopeDensityFunction envelope,
            IslandArchetype archetype
    ) {
        for (int cellX = -20; cellX <= 20; cellX++) {
            for (int cellZ = -20; cellZ <= 20; cellZ++) {
                IslandCellDescriptor cell = envelope.cellDescriptor(cellX, cellZ);
                if (cell.archetype() == archetype) {
                    return cell;
                }
            }
        }
        throw new AssertionError("No " + archetype + " cell found");
    }

    private static IslandEnvelopeDensityFunction envelope(long seed) {
        return new IslandEnvelopeDensityFunction(
                noise(seed, -7),
                noise(seed ^ 0x9E3779B97F4A7C15L, -5),
                IslandEnvelopeSettings.defaults()
        );
    }

    private static ExosphereHybridDensityFunction hybrid(long seed) {
        return hybrid(seed, 0.0);
    }

    private static ExosphereHybridDensityFunction hybrid(long seed, double baseDensity) {
        return new ExosphereHybridDensityFunction(
                DensityFunctions.constant(baseDensity),
                noise(seed, -7),
                noise(seed ^ 0x9E3779B97F4A7C15L, -5),
                ExosphereHybridSettings.defaults()
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
}

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

    private static DensityFunction.NoiseHolder noise(long seed, int firstOctave) {
        NormalNoise.NoiseParameters parameters =
                new NormalNoise.NoiseParameters(firstOctave, 1.0, 0.5, 0.25);
        return new DensityFunction.NoiseHolder(
                Holder.direct(parameters),
                NormalNoise.create(RandomSource.create(seed), parameters)
        );
    }
}

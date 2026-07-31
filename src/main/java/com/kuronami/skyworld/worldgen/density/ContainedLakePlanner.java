package com.kuronami.skyworld.worldgen.density;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;

public final class ContainedLakePlanner {
    public static final int SAFETY_MARGIN = 32;
    public static final int REQUIRED_SOLID_DEPTH = 48;
    private static final long CELL_X_SALT = 0xA24BAED4963EE407L;
    private static final long CELL_Z_SALT = 0x9FB21C651E98DF25L;

    private ContainedLakePlanner() {
    }

    public static IslandEnvelopeDensityFunction findEnvelope(DensityFunction root) {
        AtomicReference<IslandEnvelopeDensityFunction> result = new AtomicReference<>();
        root.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                if (function instanceof IslandEnvelopeDensityFunction envelope) {
                    result.compareAndSet(null, envelope);
                }
                return function;
            }
        });
        return result.get();
    }

    public static List<LakeCandidate> candidatesIntersecting(
            IslandEnvelopeDensityFunction envelope,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        int cellSize = envelope.settings().cellSize();
        int padding = 32;
        int minCellX = Math.floorDiv(minX - padding, cellSize) - 1;
        int maxCellX = Math.floorDiv(maxX + padding, cellSize) + 1;
        int minCellZ = Math.floorDiv(minZ - padding, cellSize) - 1;
        int maxCellZ = Math.floorDiv(maxZ + padding, cellSize) + 1;
        List<LakeCandidate> result = new ArrayList<>();

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                for (LakeCandidate candidate : candidatesForCell(envelope, cellX, cellZ)) {
                    if (candidate.centerX() + candidate.radius() >= minX
                            && candidate.centerX() - candidate.radius() <= maxX
                            && candidate.centerZ() + candidate.radius() >= minZ
                            && candidate.centerZ() - candidate.radius() <= maxZ) {
                        result.add(candidate);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    static List<LakeCandidate> candidatesForCell(
            IslandEnvelopeDensityFunction envelope,
            int cellX,
            int cellZ
    ) {
        IslandCellDescriptor cell = envelope.cellDescriptor(cellX, cellZ);
        int count;
        long entropy = mix64(
                Double.doubleToLongBits(cell.centerX())
                        ^ Long.rotateLeft(Double.doubleToLongBits(cell.centerZ()), 17)
                        ^ cellX * CELL_X_SALT
                        ^ cellZ * CELL_Z_SALT
        );
        SplittableRandom random = new SplittableRandom(entropy);
        if (cell.archetype() == IslandArchetype.CONTINENTAL) {
            count = random.nextInt(4);
        } else if (cell.archetype() == IslandArchetype.MEDIUM) {
            count = random.nextInt(2);
        } else {
            return List.of();
        }

        List<LakeCandidate> candidates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            IslandComponent component = cell.components().get(
                    random.nextInt(cell.components().size())
            );
            int radius = random.nextInt(12, 33);
            int depth = random.nextInt(4, 13);
            LakeCandidate candidate = null;

            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = random.nextDouble(0.0, Math.PI * 2.0);
                double distance = Math.sqrt(random.nextDouble())
                        * Math.min(component.radiusX(), component.radiusZ())
                        * 0.45;
                LakeCandidate proposed = new LakeCandidate(
                        (int)Math.round(component.centerX() + Math.cos(angle) * distance),
                        (int)Math.round(component.centerZ() + Math.sin(angle) * distance),
                        radius,
                        depth
                );
                boolean overlaps = candidates.stream().anyMatch(existing -> Math.hypot(
                        existing.centerX() - proposed.centerX(),
                        existing.centerZ() - proposed.centerZ()
                ) < existing.radius() + proposed.radius() + 16.0);
                if (!overlaps) {
                    candidate = proposed;
                    break;
                }
            }

            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    public static boolean hasEnvelopeSafety(
            LakeCandidate candidate,
            int waterSurfaceY,
            IslandEnvelopeDensityFunction envelope
    ) {
        if (!insideAtDepth(candidate.centerX(), candidate.centerZ(), waterSurfaceY, envelope)) {
            return false;
        }
        for (int radius : new int[]{candidate.radius(), candidate.radius() + SAFETY_MARGIN}) {
            for (BlockPos point : ringPoints(candidate.centerX(), candidate.centerZ(), radius)) {
                if (!insideAtDepth(point.getX(), point.getZ(), waterSurfaceY, envelope)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean insideAtDepth(
            int x,
            int z,
            int waterSurfaceY,
            IslandEnvelopeDensityFunction envelope
    ) {
        return envelope.compute(new DensityFunction.SinglePointContext(x, waterSurfaceY, z)) > 0.0
                && envelope.compute(new DensityFunction.SinglePointContext(
                        x,
                        waterSurfaceY - REQUIRED_SOLID_DEPTH,
                        z
                )) > 0.0;
    }

    public static List<BlockPos> ringPoints(int centerX, int centerZ, int radius) {
        int samples = Math.max(16, radius * 8);
        Set<BlockPos> points = new LinkedHashSet<>(samples);
        for (int index = 0; index < samples; index++) {
            double angle = index * Math.PI * 2.0 / samples;
            points.add(new BlockPos(
                    centerX + (int)Math.round(Math.cos(angle) * radius),
                    0,
                    centerZ + (int)Math.round(Math.sin(angle) * radius)
            ));
        }
        return List.copyOf(points);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record LakeCandidate(int centerX, int centerZ, int radius, int depth) {
    }
}

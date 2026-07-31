package com.kuronami.skyworld.worldgen;

import com.kuronami.skyworld.worldgen.density.ContainedLakePlanner;
import com.kuronami.skyworld.worldgen.density.IslandEnvelopeDensityFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class ContainedLakeCarver {
    static final int MAX_CACHED_LAKES = 4096;
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();

    private ContainedLakeCarver() {
    }

    static LakeRuntime createRuntime(DensityFunction finalDensity) {
        IslandEnvelopeDensityFunction envelope =
                ContainedLakePlanner.findEnvelope(finalDensity);
        return envelope == null ? null : new LakeRuntime(envelope);
    }

    static void carveChunk(
            LakeRuntime runtime,
            WorldGenRegion level,
            RandomState randomState,
            NoiseBasedChunkGenerator generator,
            ChunkAccess chunk
    ) {
        ChunkPos chunkPos = chunk.getPos();
        for (ContainedLakePlanner.LakeCandidate candidate
                : ContainedLakePlanner.candidatesIntersecting(
                        runtime.envelope,
                        chunkPos.getMinBlockX(),
                        chunkPos.getMinBlockZ(),
                        chunkPos.getMaxBlockX(),
                        chunkPos.getMaxBlockZ()
                )) {
            Optional<ValidatedLake> validated = runtime.validationCache.get(candidate);
            if (validated == null) {
                if (runtime.validationCache.size() >= MAX_CACHED_LAKES) {
                    synchronized (runtime.validationCache) {
                        if (runtime.validationCache.size() >= MAX_CACHED_LAKES) {
                            runtime.validationCache.clear();
                        }
                    }
                }
                validated = runtime.validationCache.computeIfAbsent(
                        candidate,
                        ignored -> validate(candidate, runtime.envelope, level, randomState, generator)
                );
            }
            validated.ifPresent(lake -> carve(lake, chunk));
        }
    }

    private static Optional<ValidatedLake> validate(
            ContainedLakePlanner.LakeCandidate candidate,
            IslandEnvelopeDensityFunction envelope,
            WorldGenRegion level,
            RandomState randomState,
            NoiseBasedChunkGenerator generator
    ) {
        int surfaceHeight = generator.getBaseHeight(
                candidate.centerX(),
                candidate.centerZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                level,
                randomState
        );
        int waterSurfaceY = surfaceHeight - 3;
        if (waterSurfaceY - candidate.depth() <= level.getMinBuildHeight()
                || !ContainedLakePlanner.hasEnvelopeSafety(
                        candidate,
                        waterSurfaceY,
                        envelope
                )) {
            return Optional.empty();
        }

        for (int radius : new int[]{
                candidate.radius(),
                candidate.radius() + ContainedLakePlanner.SAFETY_MARGIN
        }) {
            for (BlockPos point : ContainedLakePlanner.ringPoints(
                    candidate.centerX(),
                    candidate.centerZ(),
                    radius
            )) {
                int localSurfaceHeight = generator.getBaseHeight(
                        point.getX(),
                        point.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level,
                        randomState
                );
                if (radius == candidate.radius()
                        && localSurfaceHeight <= waterSurfaceY + 1) {
                    return Optional.empty();
                }
                DensityFunction.FunctionContext depthPoint =
                        new DensityFunction.SinglePointContext(
                                point.getX(),
                                localSurfaceHeight - 1
                                        - ContainedLakePlanner.REQUIRED_SOLID_DEPTH,
                                point.getZ()
                        );
                if (randomState.router().finalDensity().compute(depthPoint) <= 0.0) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new ValidatedLake(candidate, waterSurfaceY));
    }

    private static void carve(ValidatedLake lake, ChunkAccess chunk) {
        ContainedLakePlanner.LakeCandidate candidate = lake.candidate();
        ChunkPos chunkPos = chunk.getPos();
        int minX = Math.max(chunkPos.getMinBlockX(), candidate.centerX() - candidate.radius());
        int maxX = Math.min(chunkPos.getMaxBlockX(), candidate.centerX() + candidate.radius());
        int minZ = Math.max(chunkPos.getMinBlockZ(), candidate.centerZ() - candidate.radius());
        int maxZ = Math.min(chunkPos.getMaxBlockZ(), candidate.centerZ() + candidate.radius());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int depth = bowlDepth(candidate, x, z);
                if (depth == 0) {
                    continue;
                }
                int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int bottomY = lake.waterSurfaceY() - depth + 1;
                for (int y = bottomY; y <= topY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = y <= lake.waterSurfaceY() ? WATER : CAVE_AIR;
                    chunk.setBlockState(cursor, state, false);
                    if (state == WATER) {
                        chunk.markPosForPostprocessing(cursor);
                    }
                }
            }
        }
    }

    static int bowlDepth(
            ContainedLakePlanner.LakeCandidate candidate,
            int x,
            int z
    ) {
        double dx = x - candidate.centerX();
        double dz = z - candidate.centerZ();
        double normalizedSquared = (dx * dx + dz * dz)
                / (candidate.radius() * (double)candidate.radius());
        if (normalizedSquared >= 1.0) {
            return 0;
        }
        return Math.max(1, (int)Math.round(candidate.depth() * (1.0 - normalizedSquared)));
    }

    static final class LakeRuntime {
        private final IslandEnvelopeDensityFunction envelope;
        private final ConcurrentHashMap<
                ContainedLakePlanner.LakeCandidate,
                Optional<ValidatedLake>
        > validationCache = new ConcurrentHashMap<>();

        private LakeRuntime(IslandEnvelopeDensityFunction envelope) {
            this.envelope = envelope;
        }
    }

    private record ValidatedLake(
            ContainedLakePlanner.LakeCandidate candidate,
            int waterSurfaceY
    ) {
    }
}

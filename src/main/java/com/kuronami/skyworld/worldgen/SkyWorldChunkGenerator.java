package com.kuronami.skyworld.worldgen;

import com.kuronami.skyworld.SkyWorld;
import com.kuronami.skyworld.worldgen.density.CaveBiomeDepthDensityFunction;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.StructureManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class SkyWorldChunkGenerator extends NoiseBasedChunkGenerator {
    static final int DEFAULT_SURFACE_SHIFT = 96;
    private static final ResourceKey<NoiseGeneratorSettings> SKY_WORLD_SETTINGS =
            ResourceKey.create(
                    Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(SkyWorld.MODID, "overworld")
            );

    public static final MapCodec<SkyWorldChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(ChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings")
                            .forGetter(generator -> generator.activeOverworldSettings),
                    NoiseGeneratorSettings.CODEC.fieldOf("sky_settings")
                            .forGetter(generator -> generator.skyTerrainSettings),
                    Codec.INT.optionalFieldOf("surface_shift", DEFAULT_SURFACE_SHIFT)
                            .forGetter(generator -> generator.surfaceShift)
            ).apply(instance, instance.stable(SkyWorldChunkGenerator::new)));

    private final Holder<NoiseGeneratorSettings> activeOverworldSettings;
    private final Holder<NoiseGeneratorSettings> skyTerrainSettings;
    private final int surfaceShift;
    private final Map<RandomState, ContainedLakeCarver.LakeRuntime> lakeRuntimes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<RandomState, StructureEnvelopeValidator> structureValidators =
            Collections.synchronizedMap(new WeakHashMap<>());

    SkyWorldChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> activeOverworldSettings,
            Holder<NoiseGeneratorSettings> skyTerrainSettings
    ) {
        this(
                biomeSource,
                activeOverworldSettings,
                skyTerrainSettings,
                DEFAULT_SURFACE_SHIFT
        );
    }

    SkyWorldChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> activeOverworldSettings,
            Holder<NoiseGeneratorSettings> skyTerrainSettings,
            int surfaceShift
    ) {
        super(
                biomeSource,
                Holder.direct(SkyWorldNoiseSettings.merge(
                        activeOverworldSettings.value(),
                        skyTerrainSettings.value(),
                        surfaceShift
                ))
        );
        this.activeOverworldSettings = activeOverworldSettings;
        this.skyTerrainSettings = skyTerrainSettings;
        this.surfaceShift = surfaceShift;
    }

    int surfaceShift() {
        return surfaceShift;
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager
    ) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);
        RandomState randomState = structureState.randomState();
        StructureEnvelopeValidator validator = structureValidator(randomState);

        structureState.possibleStructureSets().forEach(structureSet -> {
            StructurePlacement placement = structureSet.value().placement();
            List<StructureSet.StructureSelectionEntry> entries = structureSet.value().structures();

            for (StructureSet.StructureSelectionEntry entry : entries) {
                StructureStart existing = structureManager.getStartForStructure(
                        sectionPos,
                        entry.structure().value(),
                        chunk
                );
                if (existing != null && existing.isValid()) {
                    return;
                }
            }

            if (!placement.isStructureChunk(structureState, chunkPos.x, chunkPos.z)) {
                return;
            }
            if (entries.size() == 1) {
                tryGenerateSupportedStructure(
                        entries.getFirst(),
                        structureManager,
                        registryAccess,
                        randomState,
                        structureTemplateManager,
                        structureState.getLevelSeed(),
                        chunk,
                        chunkPos,
                        sectionPos,
                        validator
                );
                return;
            }

            ArrayList<StructureSet.StructureSelectionEntry> candidates =
                    new ArrayList<>(entries);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(structureState.getLevelSeed(), chunkPos.x, chunkPos.z);
            int totalWeight = candidates.stream()
                    .mapToInt(StructureSet.StructureSelectionEntry::weight)
                    .sum();

            while (!candidates.isEmpty()) {
                int selection = random.nextInt(totalWeight);
                int selectedIndex = 0;
                for (StructureSet.StructureSelectionEntry candidate : candidates) {
                    selection -= candidate.weight();
                    if (selection < 0) {
                        break;
                    }
                    selectedIndex++;
                }

                StructureSet.StructureSelectionEntry selected = candidates.get(selectedIndex);
                if (tryGenerateSupportedStructure(
                        selected,
                        structureManager,
                        registryAccess,
                        randomState,
                        structureTemplateManager,
                        structureState.getLevelSeed(),
                        chunk,
                        chunkPos,
                        sectionPos,
                        validator
                )) {
                    return;
                }
                candidates.remove(selectedIndex);
                totalWeight -= selected.weight();
            }
        });
    }

    private boolean tryGenerateSupportedStructure(
            StructureSet.StructureSelectionEntry entry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos,
            StructureEnvelopeValidator validator
    ) {
        Structure structure = entry.structure().value();
        StructureStart existing = structureManager.getStartForStructure(sectionPos, structure, chunk);
        int references = existing != null ? existing.getReferences() : 0;
        HolderSet<Biome> allowedBiomes = structure.biomes();
        Predicate<Holder<Biome>> biomePredicate = allowedBiomes::contains;
        StructureStart start = structure.generate(
                registryAccess,
                this,
                getBiomeSource(),
                randomState,
                structureTemplateManager,
                seed,
                chunkPos,
                references,
                chunk,
                biomePredicate
        );
        if (!start.isValid()) {
            return false;
        }

        List<net.minecraft.world.level.levelgen.structure.BoundingBox> pieceBoxes =
                start.getPieces().stream().map(StructurePiece::getBoundingBox).toList();
        if (!validator.isSupported(pieceBoxes)) {
            structureManager.setStartForStructure(
                    sectionPos,
                    structure,
                    StructureStart.INVALID_START,
                    chunk
            );
            return false;
        }

        structureManager.setStartForStructure(sectionPos, structure, start, chunk);
        return true;
    }

    private StructureEnvelopeValidator structureValidator(RandomState randomState) {
        synchronized (structureValidators) {
            return structureValidators.computeIfAbsent(randomState, ignored -> {
                DensityFunction envelope = randomState.router().depth()
                        instanceof CaveBiomeDepthDensityFunction caveDepth
                        ? caveDepth.envelope()
                        : randomState.router().finalDensity();
                return new StructureEnvelopeValidator((x, y, z) -> envelope.compute(
                        new DensityFunction.SinglePointContext(x, y, z)
                ));
            });
        }
    }

    @Override
    public void buildSurface(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk
    ) {
        super.buildSurface(level, structureManager, randomState, chunk);
        ContainedLakeCarver.LakeRuntime runtime;
        synchronized (lakeRuntimes) {
            runtime = lakeRuntimes.computeIfAbsent(
                    randomState,
                    ignored -> ContainedLakeCarver.createRuntime(
                            randomState.router().finalDensity()
                    )
            );
        }
        if (runtime != null) {
            ContainedLakeCarver.carveChunk(runtime, level, randomState, this, chunk);
        }
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public boolean stable(ResourceKey<NoiseGeneratorSettings> settings) {
        return settings == SKY_WORLD_SETTINGS || super.stable(settings);
    }
}

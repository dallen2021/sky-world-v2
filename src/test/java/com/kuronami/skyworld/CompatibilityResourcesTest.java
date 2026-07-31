package com.kuronami.skyworld;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompatibilityResourcesTest {
    @Test
    void loadsAfterOptionalCompatibilityTargets() throws IOException {
        String metadataTemplate = Files.readString(
                projectPath("src/main/templates/META-INF/neoforge.mods.toml"),
                StandardCharsets.UTF_8
        );

        assertTrue(metadataTemplate.contains("modId=\"terralith\""));
        assertTrue(metadataTemplate.contains("modId=\"lithostitched\""));
        assertTrue(metadataTemplate.contains("modId=\"integrated_stronghold\""));
        assertEquals(3, countOccurrences(metadataTemplate, "ordering=\"AFTER\""));
    }

    @Test
    void skyWorldIsAnExplicitPresetAndDefaultOverworldIsUntouched() throws IOException {
        JsonObject preset = readJson(
                "data/sky_world/worldgen/world_preset/sky_world.json"
        );
        JsonObject normalPresets = JsonParser.parseString(Files.readString(
                projectPath(
                        "src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json"
                ),
                StandardCharsets.UTF_8
        )).getAsJsonObject();

        JsonObject overworldGenerator = preset
                .getAsJsonObject("dimensions")
                .getAsJsonObject("minecraft:overworld")
                .getAsJsonObject("generator");
        assertEquals("sky_world:noise", overworldGenerator.get("type").getAsString());
        assertEquals(
                "minecraft:overworld",
                overworldGenerator.getAsJsonObject("biome_source").get("preset").getAsString()
        );
        assertTrue(normalPresets.getAsJsonArray("values")
                .asList()
                .stream()
                .map(JsonElement::getAsString)
                .anyMatch("sky_world:sky_world"::equals));

        assertFalse(resourceExists("data/minecraft/worldgen/noise_settings/overworld.json"));
    }

    @Test
    void skyWorldPresetMergesTheActiveOverworldSettingsWithSkyTerrain() throws IOException {
        JsonObject preset = readJson(
                "data/sky_world/worldgen/world_preset/sky_world.json"
        );
        JsonObject overworldGenerator = preset
                .getAsJsonObject("dimensions")
                .getAsJsonObject("minecraft:overworld")
                .getAsJsonObject("generator");

        assertEquals("sky_world:noise", overworldGenerator.get("type").getAsString());
        assertEquals(
                "minecraft:overworld",
                overworldGenerator.get("settings").getAsString()
        );
        assertEquals(
                "sky_world:overworld",
                overworldGenerator.get("sky_settings").getAsString()
        );
    }

    @Test
    void islandDensityUsesConfigurableContinentalEnvelope() throws IOException {
        JsonObject density = readJson(
                "data/sky_world/worldgen/density_function/sky_islands.json"
        );
        JsonObject envelope = findObjectWithType(density, "sky_world:island_envelope");

        assertNotNull(envelope, "Island density must use the continental envelope codec");
        JsonObject settings = envelope.getAsJsonObject("settings");
        assertEquals(2560, settings.get("cell_size").getAsInt());
        assertEquals(64, settings.get("center_jitter").getAsInt());
        assertEquals(112, settings.get("shoulder_y").getAsInt());
        assertEquals(-56, settings.get("bottom_y").getAsInt());
        assertEquals(304, settings.get("top_y").getAsInt());
        assertEquals(0.55, settings.getAsJsonObject("continental").get("weight").getAsDouble());
        assertEquals(0.20, settings.getAsJsonObject("medium").get("weight").getAsDouble());
        assertEquals(0.20, settings.getAsJsonObject("archipelago").get("weight").getAsDouble());
        assertEquals(0.05, settings.getAsJsonObject("small").get("weight").getAsDouble());
        assertFalse(density.toString().contains("minecraft:end/base_3d_noise"));
    }

    @Test
    void caveExposureUsesItsOwnSeededSelectorNoise()
            throws IOException {
        JsonObject settings = readJson(
                "data/sky_world/worldgen/noise_settings/overworld.json"
        );

        JsonObject depth = settings.getAsJsonObject("noise_router")
                .getAsJsonObject("depth");
        assertEquals(
                "minecraft:noise",
                depth.get("type").getAsString()
        );
        assertEquals("sky_world:cave_exposure", depth.get("noise").getAsString());
        assertTrue(resourceExists("data/sky_world/worldgen/noise/cave_exposure.json"));
    }

    @Test
    void globalWorldshapeMutatorsAreRemoved() {
        assertFalse(resourceExists("data/sky_world/isekai/worldshape/sky.json"));
        assertFalse(resourceExists(
                "data/sky_world/lithostitched/worldgen_modifier/sky_overworld_density.json"
        ));
        assertFalse(resourceExists("data/sky_world/neoforge/biome_modifier/apply_sky.json"));
        assertFalse(resourceExists("data/sky_world/neoforge/structure_modifier/apply_sky.json"));
    }

    @Test
    void undersideDecorationsAreSkyOnlyAndBiomeSensitive() throws IOException {
        JsonObject glowLichen = readJson(
                "data/sky_world/worldgen/placed_feature/hanging_glow_lichen.json"
        );
        JsonObject caveVines = readJson(
                "data/sky_world/worldgen/placed_feature/hanging_cave_vines.json"
        );
        JsonObject glowModifier = readJson(
                "data/sky_world/neoforge/biome_modifier/add_underside_glow.json"
        );
        JsonObject vineModifier = readJson(
                "data/sky_world/neoforge/biome_modifier/add_underside_vines.json"
        );
        JsonObject vineBiomes = readJson(
                "data/sky_world/tags/worldgen/biome/supports_hanging_vines.json"
        );

        assertTrue(hasPlacementType(glowLichen, "sky_world:sky_world_only"));
        assertTrue(hasPlacementType(caveVines, "sky_world:sky_world_only"));
        assertEquals("#c:is_overworld", glowModifier.get("biomes").getAsString());
        assertEquals(
                "#sky_world:supports_hanging_vines",
                vineModifier.get("biomes").getAsString()
        );
        assertTrue(vineBiomes.toString().contains("#c:is_lush"));
        assertFalse(glowModifier.toString().contains("dripstone"));
        assertFalse(vineModifier.toString().contains("dripstone"));
    }

    @Test
    void integratedStrongholdProjectsItsStartToTheSkySurface() throws IOException {
        JsonObject structure = readJson(
                "data/integrated_stronghold/worldgen/structure/stronghold.json"
        );

        assertEquals(
                "neoforge:mod_loaded",
                structure.getAsJsonArray("neoforge:conditions")
                        .get(0)
                        .getAsJsonObject()
                        .get("type")
                        .getAsString()
        );
        assertEquals(
                "integrated_stronghold",
                structure.getAsJsonArray("neoforge:conditions")
                        .get(0)
                        .getAsJsonObject()
                        .get("modid")
                        .getAsString()
        );
        assertEquals(
                "WORLD_SURFACE_WG",
                structure.get("project_start_to_heightmap").getAsString()
        );
        assertEquals(
                0,
                structure.getAsJsonObject("start_height")
                        .getAsJsonObject("min_inclusive")
                        .get("absolute")
                        .getAsInt()
        );
    }

    private static JsonObject readJson(String path) throws IOException {
        try (InputStream stream = CompatibilityResourcesTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(stream, "Missing compatibility resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static boolean resourceExists(String path) {
        return Files.isRegularFile(projectPath("src/main/resources/" + path));
    }

    private static Path projectPath(String relativePath) {
        String projectDirectory = System.getProperty("skyWorld.projectDir");
        assertNotNull(projectDirectory, "Missing Sky World project directory");
        return Path.of(projectDirectory).resolve(relativePath);
    }

    private static JsonObject findObjectWithType(JsonElement element, String type) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && type.equals(object.get("type").getAsString())) {
                return object;
            }
            for (JsonElement value : object.asMap().values()) {
                JsonObject match = findObjectWithType(value, type);
                if (match != null) {
                    return match;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                JsonObject match = findObjectWithType(value, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static boolean hasPlacementType(JsonObject feature, String type) {
        JsonArray placement = feature.getAsJsonArray("placement");
        return placement.asList()
                .stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .anyMatch(object -> object.has("type")
                        && type.equals(object.get("type").getAsString()));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}

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
                Path.of("src/main/templates/META-INF/neoforge.mods.toml"),
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
        JsonObject normalPresets = readJson(
                "data/minecraft/tags/worldgen/world_preset/normal.json"
        );

        JsonObject overworldGenerator = preset
                .getAsJsonObject("dimensions")
                .getAsJsonObject("minecraft:overworld")
                .getAsJsonObject("generator");
        assertEquals("minecraft:noise", overworldGenerator.get("type").getAsString());
        assertEquals("sky_world:overworld", overworldGenerator.get("settings").getAsString());
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
    void islandDensityUsesScalableEndNoise() throws IOException {
        JsonObject density = readJson(
                "data/sky_world/worldgen/density_function/sky_islands.json"
        );
        JsonObject coordinateScale = findObjectWithType(density, "isekai_api:scale_coord");

        assertNotNull(coordinateScale, "Island density must expose horizontal coordinate scaling");
        assertEquals("minecraft:end/base_3d_noise", coordinateScale.get("f").getAsString());
        assertTrue(coordinateScale.get("sx").getAsDouble() > 1.0);
        assertEquals(1.0, coordinateScale.get("sy").getAsDouble());
        assertTrue(coordinateScale.get("sz").getAsDouble() > 1.0);
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
        return CompatibilityResourcesTest.class.getClassLoader().getResource(path) != null;
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

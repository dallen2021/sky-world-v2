package com.kuronami.skyworld;

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
    void lithostitchedAndOverworldShareOneCustomizableSkyDensity() throws IOException {
        JsonObject modifier = readJson(
                "data/sky_world/lithostitched/worldgen_modifier/sky_overworld_density.json"
        );
        JsonObject overworld = readJson(
                "data/minecraft/worldgen/noise_settings/overworld.json"
        );
        JsonObject density = readJson(
                "data/sky_world/worldgen/density_function/sky_islands.json"
        );

        assertEquals("lithostitched:wrap_noise_router", modifier.get("type").getAsString());
        assertEquals("minecraft:overworld", modifier.get("dimension").getAsString());
        assertEquals("final_density", modifier.get("target").getAsString());
        assertTrue(modifier.get("wrapper_function").isJsonPrimitive());
        assertEquals("sky_world:sky_islands", modifier.get("wrapper_function").getAsString());
        assertEquals(
                "sky_world:sky_islands",
                overworld.getAsJsonObject("noise_router").get("final_density").getAsString()
        );
        assertEquals("isekai_api:squeeze", density.get("type").getAsString());
    }

    @Test
    void integratedStrongholdProjectsItsStartToTheSkySurface() throws IOException {
        JsonObject structure = readJson(
                "data/integrated_stronghold/worldgen/structure/stronghold.json"
        );
        JsonObject worldshape = readJson(
                "data/sky_world/isekai/worldshape/sky.json"
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
        assertEquals(
                "isekai:always",
                worldshape.getAsJsonObject("structure_predicates")
                        .getAsJsonObject("integrated_stronghold:stronghold")
                        .get("type")
                        .getAsString()
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

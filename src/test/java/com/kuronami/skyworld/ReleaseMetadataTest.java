package com.kuronami.skyworld;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseMetadataTest {
    @Test
    void releaseMetadataAgreesOnVersionOneFiveTwo() throws IOException {
        String properties = read("gradle.properties");
        String pack = read("src/main/resources/pack.mcmeta");
        String readme = read("README.md");
        String changelog = read("CHANGELOG.md");

        assertTrue(properties.contains("mod_version=1.5.2"));
        assertTrue(pack.contains("Sky World 1.5.2"));
        assertTrue(readme.contains("sky_world-1.5.2.jar"));
        assertTrue(changelog.contains("## [1.5.2]"));
        assertTrue(SkyWorld.VERSION.equals("1.5.2"));
    }

    private static String read(String path) throws IOException {
        String projectDirectory = System.getProperty("skyWorld.projectDir");
        return Files.readString(
                Path.of(projectDirectory).resolve(path),
                StandardCharsets.UTF_8
        );
    }
}

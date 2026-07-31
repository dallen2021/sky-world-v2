package com.kuronami.skyworld.worldgen;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainModeTest {
    @Test
    void serializedNamesRemainStable() {
        assertEquals(
                "continental_envelope",
                TerrainMode.CODEC.encodeStart(
                        JsonOps.INSTANCE,
                        TerrainMode.CONTINENTAL_ENVELOPE
                ).result().orElseThrow().getAsString()
        );
        assertEquals(
                TerrainMode.EXOSPHERE_HYBRID,
                TerrainMode.CODEC.parse(
                        JsonOps.INSTANCE,
                        new JsonPrimitive("exosphere_hybrid")
                ).result().orElseThrow()
        );
    }

    @Test
    void unknownModesFailInsteadOfChangingWorldGeneration() {
        assertTrue(TerrainMode.CODEC.parse(
                JsonOps.INSTANCE,
                new JsonPrimitive("future_mode")
        ).error().isPresent());
    }
}

package com.kuronami.skyworld.worldgen;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StructureLocateLimiterTest {
    @Test
    void capsIdasOnlySearchesBeforeTheyCanTripTheServerWatchdog() {
        assertEquals(8, StructureLocateLimiter.limitRadius(100, List.of(
                ResourceLocation.fromNamespaceAndPath("idas", "castle")
        )));
    }

    @Test
    void leavesOtherAndMixedStructureSearchesUnchanged() {
        assertEquals(100, StructureLocateLimiter.limitRadius(100, List.of(
                ResourceLocation.fromNamespaceAndPath("integrated_stronghold", "stronghold")
        )));
        assertEquals(100, StructureLocateLimiter.limitRadius(100, List.of(
                ResourceLocation.fromNamespaceAndPath("idas", "castle"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "village_plains")
        )));
    }

    @Test
    void preservesAlreadySmallerCallerLimits() {
        assertEquals(4, StructureLocateLimiter.limitRadius(4, List.of(
                ResourceLocation.fromNamespaceAndPath("idas", "castle")
        )));
    }
}

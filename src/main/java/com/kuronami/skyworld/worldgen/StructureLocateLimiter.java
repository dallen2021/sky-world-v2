package com.kuronami.skyworld.worldgen;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class StructureLocateLimiter {
    static final int MAX_IDAS_SEARCH_RADIUS = 8;

    private StructureLocateLimiter() {
    }

    static int limitRadius(int requestedRadius, List<ResourceLocation> structures) {
        boolean idasOnly = !structures.isEmpty()
                && structures.stream().allMatch(id -> "idas".equals(id.getNamespace()));
        return idasOnly
                ? Math.min(requestedRadius, MAX_IDAS_SEARCH_RADIUS)
                : requestedRadius;
    }
}

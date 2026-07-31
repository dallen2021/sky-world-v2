package com.kuronami.skyworld.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

enum TerrainMode implements StringRepresentable {
    CONTINENTAL_ENVELOPE("continental_envelope"),
    EXOSPHERE_HYBRID("exosphere_hybrid");

    static final Codec<TerrainMode> CODEC = StringRepresentable.fromEnum(TerrainMode::values);

    private final String serializedName;

    TerrainMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}

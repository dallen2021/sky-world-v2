package com.kuronami.skyworld.worldgen.density;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaveBiomeDepthDensityFunctionTest {
    @Test
    void codecIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "sky_world",
                "cave_biome_depth"
        );

        assertTrue(BuiltInRegistries.DENSITY_FUNCTION_TYPE.containsKey(id));
        assertSame(
                CaveBiomeDepthDensityFunction.CODEC,
                BuiltInRegistries.DENSITY_FUNCTION_TYPE.get(id)
        );
    }

    @Test
    void exposureCoversTwoPercentOfBoundaryWithWetClimateBias() {
        CaveBiomeDepthDensityFunction function = new CaveBiomeDepthDensityFunction(
                DensityFunctions.constant(0.10),
                DensityFunctions.constant(0.0),
                DensityFunctions.yClampedGradient(0, 512, -1.0, 1.0),
                DensityFunctions.yClampedGradient(0, 1, -1.0, 1.0),
                0.375,
                0.45,
                0.032,
                0.008
        );
        int exposed = 0;
        int wetExposed = 0;
        int samples = 0;

        for (int seedBand = 0; seedBand < 4; seedBand++) {
            for (int x = 0; x < 256; x++) {
                for (int z = 0; z < 256; z++) {
                    int y = (x + z + seedBand) & 1;
                    DensityFunction.FunctionContext point =
                            new DensityFunction.SinglePointContext(
                                    x + seedBand * 8192,
                                    y,
                                    z - seedBand * 4096
                            );
                    if (function.compute(point) == 0.45) {
                        exposed++;
                        if (y == 1) {
                            wetExposed++;
                        }
                    }
                    samples++;
                }
            }
        }

        double coverage = exposed / (double)samples;
        double wetFraction = wetExposed / (double)exposed;
        assertTrue(coverage >= 0.015 && coverage <= 0.025, "coverage=" + coverage);
        assertTrue(wetFraction >= 0.60, "wetFraction=" + wetFraction);
    }

    @Test
    void onlyPromotesPointsInsideTheExteriorBoundaryBand() {
        CaveBiomeDepthDensityFunction function = new CaveBiomeDepthDensityFunction(
                DensityFunctions.constant(0.10),
                DensityFunctions.constant(0.50),
                DensityFunctions.constant(0.0),
                DensityFunctions.constant(1.0),
                0.375,
                0.45,
                1.0,
                1.0
        );

        assertEquals(
                0.10,
                function.compute(new DensityFunction.SinglePointContext(0, 112, 0))
        );
    }
}

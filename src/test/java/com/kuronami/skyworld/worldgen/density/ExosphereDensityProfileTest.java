package com.kuronami.skyworld.worldgen.density;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExosphereDensityProfileTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void reproducesTheExosphereLowerAndUpperGradients() {
        assertEquals(-0.234375, ExosphereDensityProfile.rawDensity(-64.0, 0.0), EPSILON);
        assertEquals(-0.148425, ExosphereDensityProfile.rawDensity(-40.0, 0.0), EPSILON);
        assertEquals(-0.062475, ExosphereDensityProfile.rawDensity(-16.0, 0.0), EPSILON);
        assertEquals(-0.062475, ExosphereDensityProfile.rawDensity(128.0, 0.0), EPSILON);
        assertEquals(-2.499975, ExosphereDensityProfile.rawDensity(255.0, 0.0), EPSILON);
    }

    @Test
    void appliesTheOriginalScaleAndSqueeze() {
        assertEquals(-0.074859375, ExosphereDensityProfile.finalDensity(-64.0, 0.0), EPSILON);
        assertEquals(-0.0199893365320535,
                ExosphereDensityProfile.finalDensity(-16.0, 0.0), EPSILON);
        assertEquals(0.0567468907091113,
                ExosphereDensityProfile.finalDensity(128.0, 0.3), EPSILON);
        assertEquals(-11.0 / 24.0,
                ExosphereDensityProfile.finalDensity(255.0, 0.0), EPSILON);
    }

    @Test
    void returnsEmptyDensityOutsideTheOriginalNoiseHeight() {
        assertEquals(-1.0, ExosphereDensityProfile.finalDensity(-65.0, 1.0), EPSILON);
        assertEquals(-1.0, ExosphereDensityProfile.finalDensity(256.0, 1.0), EPSILON);
    }
}

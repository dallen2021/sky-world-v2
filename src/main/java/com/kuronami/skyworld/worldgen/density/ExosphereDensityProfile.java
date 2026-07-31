package com.kuronami.skyworld.worldgen.density;

final class ExosphereDensityProfile {
    static final int MIN_Y = -64;
    static final int MAX_Y = 255;

    private static final double BASE_OFFSET = -0.234375;
    private static final double INNER_OFFSET = 0.234375;
    private static final double HIGH_OFFSET = -23.457;
    private static final double NOISE_OFFSET = 23.4375;
    private static final double DENSITY_SCALE = 0.64;

    private ExosphereDensityProfile() {
    }

    static double rawDensity(double y, double baseNoise) {
        double lowerGradient = clampedMap(y, -64.0, -16.0, 0.0, 0.8);
        double upperGradient = clampedMap(y, 128.0, 255.0, 1.0, 0.87);
        return BASE_OFFSET + lowerGradient * (
                INNER_OFFSET + HIGH_OFFSET
                        + upperGradient * (NOISE_OFFSET + baseNoise)
        );
    }

    static double finalDensity(double y, double baseNoise) {
        if (y < MIN_Y || y > MAX_Y) {
            return -1.0;
        }
        return squeeze(DENSITY_SCALE * rawDensity(y, baseNoise));
    }

    static double squeeze(double value) {
        double clamped = Math.max(-1.0, Math.min(1.0, value));
        return clamped / 2.0 - clamped * clamped * clamped / 24.0;
    }

    private static double clampedMap(
            double value,
            double from,
            double to,
            double fromValue,
            double toValue
    ) {
        double unit = Math.max(0.0, Math.min(1.0, (value - from) / (to - from)));
        return fromValue + unit * (toValue - fromValue);
    }
}

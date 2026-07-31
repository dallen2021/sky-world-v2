package com.kuronami.skyworld.worldgen;

import com.kuronami.skyworld.worldgen.density.ContainedLakePlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContainedLakeCarverTest {
    @Test
    void bowlIsDeepestAtCenterAndClosesAtShoreline() {
        ContainedLakePlanner.LakeCandidate lake =
                new ContainedLakePlanner.LakeCandidate(100, -200, 24, 10);

        assertEquals(10, ContainedLakeCarver.bowlDepth(lake, 100, -200));
        assertTrue(ContainedLakeCarver.bowlDepth(lake, 112, -200) < 10);
        assertEquals(0, ContainedLakeCarver.bowlDepth(lake, 124, -200));
        assertEquals(0, ContainedLakeCarver.bowlDepth(lake, 125, -200));
    }
}

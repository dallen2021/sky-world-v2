package com.kuronami.skyworld.worldgen;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructureEnvelopeValidatorTest {
    private static final StructureEnvelopeValidator.EnvelopeSampler ISLAND =
            (x, y, z) -> Math.abs(x) <= 64 && Math.abs(z) <= 64
                    && y > -56 && y < 304 ? 1.0 : -1.0;

    @Test
    void acceptsPiecesWhoseBasesAreInsideTheIslandEnvelope() {
        StructureEnvelopeValidator validator = new StructureEnvelopeValidator(ISLAND);

        assertTrue(validator.isSupported(List.of(
                new BoundingBox(-24, 140, -24, 24, 176, 24),
                new BoundingBox(28, 132, -12, 52, 164, 12)
        )));
    }

    @Test
    void rejectsDetachedPiecesInTheVoid() {
        StructureEnvelopeValidator validator = new StructureEnvelopeValidator(ISLAND);

        assertFalse(validator.isSupported(List.of(
                new BoundingBox(180, -40, 180, 240, -20, 240)
        )));
    }

    @Test
    void rejectsAStartWhenAnyRepresentativePieceHangsOutsideTheIsland() {
        StructureEnvelopeValidator validator = new StructureEnvelopeValidator(ISLAND);

        assertFalse(validator.isSupported(List.of(
                new BoundingBox(-24, 120, -24, 24, 160, 24),
                new BoundingBox(160, -32, 160, 224, -12, 224)
        )));
    }

    @Test
    void permitsElevatedPiecesOnlyWithinABoundedSupportSearch() {
        StructureEnvelopeValidator validator = new StructureEnvelopeValidator(ISLAND);

        assertTrue(validator.isSupported(List.of(
                new BoundingBox(-20, 328, -20, 20, 348, 20)
        )));
        assertFalse(validator.isSupported(List.of(
                new BoundingBox(-20, 400, -20, 20, 420, 20)
        )));
    }

    @Test
    void validationWorkIsBoundedForVeryLargeModdedStarts() {
        AtomicInteger samples = new AtomicInteger();
        StructureEnvelopeValidator validator = new StructureEnvelopeValidator((x, y, z) -> {
            samples.incrementAndGet();
            return 1.0;
        });
        List<BoundingBox> boxes = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            boxes.add(new BoundingBox(index, 100, index, index + 15, 130, index + 15));
        }

        assertTrue(validator.isSupported(boxes));
        assertTrue(samples.get() <= StructureEnvelopeValidator.MAX_DENSITY_SAMPLES,
                "structure validation exceeded its hard sample budget: " + samples.get());
    }
}

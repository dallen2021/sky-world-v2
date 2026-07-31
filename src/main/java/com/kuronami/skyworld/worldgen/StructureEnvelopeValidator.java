package com.kuronami.skyworld.worldgen;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

final class StructureEnvelopeValidator {
    static final int MAX_REPRESENTATIVE_BOXES = 24;
    static final int MAX_DENSITY_SAMPLES = MAX_REPRESENTATIVE_BOXES * 5 * 9;
    private static final int MAX_SUPPORT_SEARCH = 64;
    private static final int VERTICAL_STEP = 8;

    private final EnvelopeSampler envelope;

    StructureEnvelopeValidator(EnvelopeSampler envelope) {
        this.envelope = envelope;
    }

    boolean isSupported(List<BoundingBox> pieceBoxes) {
        if (pieceBoxes.isEmpty()) {
            return false;
        }

        for (BoundingBox box : representativeBoxes(pieceBoxes)) {
            if (!isBoxSupported(box)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBoxSupported(BoundingBox box) {
        int minX = box.minX();
        int maxX = box.maxX();
        int minZ = box.minZ();
        int maxZ = box.maxZ();
        int centerX = (minX + maxX) >> 1;
        int centerZ = (minZ + maxZ) >> 1;
        int insetMinX = Math.min(maxX, minX + 1);
        int insetMaxX = Math.max(minX, maxX - 1);
        int insetMinZ = Math.min(maxZ, minZ + 1);
        int insetMaxZ = Math.max(minZ, maxZ - 1);
        int[][] columns = {
                {centerX, centerZ},
                {insetMinX, insetMinZ},
                {insetMinX, insetMaxZ},
                {insetMaxX, insetMinZ},
                {insetMaxX, insetMaxZ}
        };
        int supportedColumns = 0;

        for (int[] column : columns) {
            if (hasSupport(column[0], box.minY(), column[1])) {
                supportedColumns++;
            }
        }
        return supportedColumns >= 3;
    }

    private boolean hasSupport(int x, int baseY, int z) {
        for (int offset = 0; offset <= MAX_SUPPORT_SEARCH; offset += VERTICAL_STEP) {
            if (envelope.sample(x, baseY - offset, z) > 0.0) {
                return true;
            }
        }
        return false;
    }

    private static List<BoundingBox> representativeBoxes(List<BoundingBox> boxes) {
        if (boxes.size() <= MAX_REPRESENTATIVE_BOXES) {
            return boxes;
        }

        List<BoundingBox> representatives = new ArrayList<>(MAX_REPRESENTATIVE_BOXES);
        for (int index = 0; index < MAX_REPRESENTATIVE_BOXES; index++) {
            int sourceIndex = (int)Math.round(
                    index * (boxes.size() - 1.0) / (MAX_REPRESENTATIVE_BOXES - 1.0)
            );
            representatives.add(boxes.get(sourceIndex));
        }
        return List.copyOf(representatives);
    }

    @FunctionalInterface
    interface EnvelopeSampler {
        double sample(int x, int y, int z);
    }
}

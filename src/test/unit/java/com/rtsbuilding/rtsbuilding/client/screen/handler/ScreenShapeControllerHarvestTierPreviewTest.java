package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenShapeControllerHarvestTierPreviewTest {

    @Test
    void shouldKeepAcceptedTargetsAndRemoveOnlyHarvestTierSkippedBlocks() {
        BlockPos accepted = new BlockPos(1, 2, 3);
        BlockPos skipped = new BlockPos(4, 5, 6);
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(accepted, skipped),
                true,
                true,
                List.of(),
                false,
                true);

        ShapeDataRecords.GhostPreview filtered =
                ScreenShapeController.pruneConfirmedDestroyPreview(preview, List.of(skipped));

        assertEquals(List.of(accepted), filtered.blocks());
        assertTrue(filtered.destructive());
        assertTrue(filtered.confirmedWorkArea());
    }

    @Test
    void shouldClearConfirmedPreviewWhenEveryTargetWasSkipped() {
        BlockPos skipped = new BlockPos(4, 5, 6);
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(skipped),
                true,
                true,
                List.of(),
                false,
                true);

        ShapeDataRecords.GhostPreview filtered =
                ScreenShapeController.pruneConfirmedDestroyPreview(preview, List.of(skipped));

        assertSame(ShapeDataRecords.GhostPreview.EMPTY, filtered);
    }
}

package com.rtsbuilding.rtsbuilding.blueprint.client;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintCaptureControllerTest {
    @Test
    void twoWorldCornersCreateInclusiveSelectionWhileKeepingSaveCoordinatesCompatible() {
        BlueprintCaptureController controller = new BlueprintCaptureController();
        RecordingStatus status = new RecordingStatus();

        controller.start(status::set);
        controller.acceptPoint(new BlockPos(10, 64, 10), status::set);
        controller.acceptPoint(new BlockPos(12, 66, 14), status::set);

        assertTrue(controller.isSelectionComplete());
        assertEquals(new BlockPos(10, 63, 10), controller.pointA());
        assertEquals(new BlockPos(12, 66, 14), controller.pointB());
        assertEquals(new BlockPos(10, 64, 10), controller.displayPointA());
        assertEquals(new BlockPos(12, 66, 14), controller.displayPointB());
        assertEquals("3x3x5", controller.sizeText());

        RtsCullingBox box = controller.selectionBox();
        assertEquals(new BlockPos(10, 64, 10), box.min());
        assertEquals(new BlockPos(12, 66, 14), box.max());
    }

    @Test
    void singlePointCanBeConfirmedAsOneBlockSelection() {
        BlueprintCaptureController controller = new BlueprintCaptureController();
        RecordingStatus status = new RecordingStatus();

        controller.start(status::set);
        controller.acceptPoint(new BlockPos(5, 70, 6), status::set);

        assertFalse(controller.isSelectionComplete());

        assertTrue(controller.confirmSingleBlockSelection(status::set));

        assertTrue(controller.isSelectionComplete());
        assertEquals(new BlockPos(5, 69, 6), controller.pointA());
        assertEquals(new BlockPos(5, 70, 6), controller.pointB());
        assertEquals(new BlockPos(5, 70, 6), controller.displayPointA());
        assertEquals(new BlockPos(5, 70, 6), controller.displayPointB());
        assertEquals("1x1x1", controller.sizeText());
    }

    @Test
    void directionHandlesResizeOnlyTheirOwnFace() {
        BlueprintCaptureController controller = selectedBox(
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 12));
        RecordingStatus status = new RecordingStatus();

        controller.adjustSelectionFromHandle(Direction.WEST, 2, status::set);

        assertEquals(new BlockPos(8, 64, 10), controller.selectionBox().min());
        assertEquals(new BlockPos(12, 66, 12), controller.selectionBox().max());
        assertEquals("5x3x3", controller.sizeText());

        controller.adjustSelectionFromHandle(Direction.WEST, -99, status::set);

        assertEquals(new BlockPos(12, 64, 10), controller.selectionBox().min());
        assertEquals(new BlockPos(12, 66, 12), controller.selectionBox().max());
        assertEquals("1x3x3", controller.sizeText());

        controller.adjustSelectionFromHandle(Direction.WEST, -99, status::set);

        assertEquals(new BlockPos(12, 64, 10), controller.selectionBox().min());
        assertEquals(new BlockPos(12, 66, 12), controller.selectionBox().max());
    }

    @Test
    void activeHandleScrollUsesSelectedFaceUntilReleased() {
        BlueprintCaptureController controller = selectedBox(
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 12));
        RecordingStatus status = new RecordingStatus();

        assertTrue(controller.handleWorldAction(
                null,
                new Vec3(15.0D, 65.5D, 11.5D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                status::set));
        assertEquals(Direction.EAST, controller.activeHandleDirection());

        assertTrue(controller.handleWorldAction(
                null,
                new Vec3(15.0D, 65.5D, 11.5D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                status::set));
        assertNull(controller.activeHandleDirection());
        assertFalse(controller.handleScroll(1.0D, false, status::set));

        assertTrue(controller.handleWorldAction(
                null,
                new Vec3(15.0D, 65.5D, 11.5D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                status::set));
        assertEquals(Direction.EAST, controller.activeHandleDirection());
        assertTrue(controller.handleScroll(1.0D, false, status::set));
        assertEquals(new BlockPos(10, 64, 10), controller.selectionBox().min());
        assertEquals(new BlockPos(13, 66, 12), controller.selectionBox().max());

        assertTrue(controller.releaseActiveHandle());
        assertFalse(controller.handleScroll(1.0D, false, status::set));
    }

    @Test
    void activeHandleDragUsesSelectedFaceUntilReleased() {
        BlueprintCaptureController controller = selectedBox(
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 12));
        RecordingStatus status = new RecordingStatus();

        assertTrue(controller.handleWorldAction(
                null,
                new Vec3(15.0D, 65.5D, 11.5D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                status::set));
        assertEquals(Direction.EAST, controller.activeHandleDirection());

        assertTrue(controller.handleDrag(18.0D, 0.0D, 1.0D, 0.0D, status::set));
        assertEquals(new BlockPos(10, 64, 10), controller.selectionBox().min());
        assertEquals(new BlockPos(13, 66, 12), controller.selectionBox().max());

        assertTrue(controller.handleDrag(-18.0D, 0.0D, 1.0D, 0.0D, status::set));
        assertEquals(new BlockPos(10, 64, 10), controller.selectionBox().min());
        assertEquals(new BlockPos(12, 66, 12), controller.selectionBox().max());

        assertTrue(controller.releaseActiveHandle());
        assertFalse(controller.handleDrag(18.0D, 0.0D, 1.0D, 0.0D, status::set));
    }

    @Test
    void excludedBlocksOutsideResizedSelectionAreDropped() {
        BlueprintCaptureController controller = selectedBox(
                new BlockPos(10, 64, 10),
                new BlockPos(12, 66, 12));
        RecordingStatus status = new RecordingStatus();
        BlockPos excluded = new BlockPos(12, 64, 12);

        assertTrue(controller.toggleBlockExclusion(excluded, status::set));
        assertEquals(List.of(excluded), controller.excludedBlocksForRender(10));

        controller.adjustSelectionFromHandle(Direction.EAST, -2, status::set);

        assertEquals(List.of(), controller.excludedBlocksForRender(10));
    }

    private static BlueprintCaptureController selectedBox(BlockPos first, BlockPos second) {
        BlueprintCaptureController controller = new BlueprintCaptureController();
        RecordingStatus status = new RecordingStatus();
        controller.start(status::set);
        controller.acceptPoint(first, status::set);
        controller.acceptPoint(second, status::set);
        return controller;
    }

    private static final class RecordingStatus {
        byte status;
        String messageKey = "";
        String detail = "";

        void set(byte status, String messageKey, String detail) {
            this.status = status;
            this.messageKey = messageKey;
            this.detail = detail;
        }
    }
}

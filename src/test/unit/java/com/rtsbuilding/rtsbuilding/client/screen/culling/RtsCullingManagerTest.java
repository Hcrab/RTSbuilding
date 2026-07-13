package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingManagerTest {
    @Test
    void closingManagementModeCommitsCompleteDraftForWorldCulling() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        clickBlock(manager, new BlockPos(12, 64, 12));
        manager.handleScroll(1.0D, false);

        manager.closeManagementMode();

        assertFalse(manager.isManagementMode());
        assertEquals(1, manager.boxes().size());
        assertTrue(manager.shouldCullWorldBlock(new BlockPos(11, 65, 11)));
    }

    @Test
    void closingManagementModeCancelsIncompleteDraft() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));

        manager.closeManagementMode();

        assertFalse(manager.isManagementMode());
        assertTrue(manager.boxes().isEmpty());
        assertFalse(manager.shouldCullWorldBlock(new BlockPos(10, 64, 10)));
    }

    @Test
    void enterAfterOnlyFirstPointCreatesSingleBlockBox() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        manager.confirmDraft();

        assertEquals(1, manager.boxes().size());
        RtsCullingBox box = manager.boxes().get(0);
        assertEquals(new BlockPos(10, 64, 10), box.min());
        assertEquals(new BlockPos(10, 64, 10), box.max());
        assertTrue(manager.shouldCullWorldBlock(new BlockPos(10, 64, 10)));
    }

    @Test
    void confirmedBoxCullsWorldEvenWhileManagementPanelIsOpen() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        clickBlock(manager, new BlockPos(12, 64, 12));
        manager.confirmDraft();

        assertTrue(manager.isManagementMode());
        assertEquals(1, manager.boxes().size());
        assertTrue(manager.shouldCullWorldBlock(new BlockPos(11, 64, 11)));
    }

    @Test
    void completeDraftCullsWorldBeforeItIsConfirmed() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        clickBlock(manager, new BlockPos(12, 64, 12));

        assertTrue(manager.boxes().isEmpty(), "a two-point draft should remain adjustable before confirmation");
        assertTrue(manager.shouldCullWorldBlock(new BlockPos(11, 64, 11)),
                "a complete draft should immediately participate in world culling");
    }

    @Test
    void revealedBlockInsideBoxStaysVisibleForNewPlayerPlacement() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        clickBlock(manager, new BlockPos(12, 64, 12));
        manager.confirmDraft();
        BlockPos placedChest = new BlockPos(11, 64, 11);

        assertTrue(manager.shouldCullWorldBlock(placedChest));

        manager.revealWorldBlock(placedChest);

        assertFalse(manager.shouldCullWorldBlock(placedChest),
                "new player placement inside a culling box should stay visible");
        assertTrue(manager.shouldCullWorldBlock(new BlockPos(12, 64, 12)),
                "revealing one player-edited block must not disable the whole culling box");
    }

    @Test
    void clickedAxisHandleLocksWheelResizeUntilCancelled() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        manager.confirmDraft();

        Vec3 origin = new Vec3(10.5D, 67.0D, 10.5D);
        Vec3 direction = new Vec3(0.0D, -1.0D, 0.0D);
        manager.updateHover(origin, direction);
        assertEquals(Direction.UP, manager.hoveredHandleDirection());
        assertFalse(manager.handleScroll(-1.0D, false), "hover alone must not steal the wheel");

        manager.handleWorldAction(null, origin, direction);
        assertEquals(Direction.UP, manager.activeHandleDirection());

        manager.handleWorldAction(null, origin, direction);
        assertNull(manager.activeHandleDirection());
        assertFalse(manager.handleScroll(1.0D, false), "clicking the gold handle again should release the wheel");

        manager.handleWorldAction(null, origin, direction);
        assertEquals(Direction.UP, manager.activeHandleDirection());
        assertTrue(manager.handleScroll(1.0D, false));

        RtsCullingBox box = manager.boxes().get(0);
        assertEquals(64, box.min().getY());
        assertEquals(65, box.max().getY());
        assertEquals(2, box.height());

        manager.updateHover(null, null);
        assertTrue(manager.handleScroll(-1.0D, false), "locked direction should keep the wheel even after hover is gone");
        box = manager.boxes().get(0);
        assertEquals(64, box.min().getY());
        assertEquals(64, box.max().getY());
        assertEquals(1, box.height());
    }

    @Test
    void negativeAxisHandleExpandsOnlyNegativeFace() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        manager.confirmDraft();

        Vec3 origin = new Vec3(10.5D, 62.0D, 10.5D);
        Vec3 direction = new Vec3(0.0D, 1.0D, 0.0D);
        manager.updateHover(origin, direction);
        assertEquals(Direction.DOWN, manager.hoveredHandleDirection());

        manager.handleWorldAction(null, origin, direction);
        assertEquals(Direction.DOWN, manager.activeHandleDirection());
        assertTrue(manager.handleScroll(1.0D, false));

        RtsCullingBox box = manager.boxes().get(0);
        assertEquals(63, box.min().getY());
        assertEquals(64, box.max().getY());
        assertEquals(2, box.height());
    }

    @Test
    void activeAxisHandleCanResizeByDragging() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        manager.confirmDraft();

        Vec3 origin = new Vec3(10.5D, 67.0D, 10.5D);
        Vec3 direction = new Vec3(0.0D, -1.0D, 0.0D);
        manager.handleWorldAction(null, origin, direction);

        assertEquals(Direction.UP, manager.activeHandleDirection());
        assertTrue(manager.handleActiveHandleDrag(0.0D, -18.0D, 0.0D, -1.0D));

        RtsCullingBox box = manager.boxes().get(0);
        assertEquals(new BlockPos(10, 64, 10), box.min());
        assertEquals(new BlockPos(10, 65, 10), box.max());

        assertTrue(manager.handleActiveHandleDrag(0.0D, 18.0D, 0.0D, -1.0D));

        box = manager.boxes().get(0);
        assertEquals(new BlockPos(10, 64, 10), box.min());
        assertEquals(new BlockPos(10, 64, 10), box.max());
    }

    @Test
    void mouseReleaseAfterDraggingClearsOnlyDraggedHandleLock() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        manager.confirmDraft();

        Vec3 origin = new Vec3(10.5D, 67.0D, 10.5D);
        Vec3 direction = new Vec3(0.0D, -1.0D, 0.0D);
        manager.handleWorldAction(null, origin, direction);

        assertEquals(Direction.UP, manager.activeHandleDirection());
        assertFalse(manager.releaseActiveHandleIfDragged(),
                "普通点击锁定仍要保留滚轮调整能力");
        assertEquals(Direction.UP, manager.activeHandleDirection());

        assertTrue(manager.handleActiveHandleDrag(0.0D, -4.0D, 0.0D, -1.0D));
        assertTrue(manager.releaseActiveHandleIfDragged());
        assertNull(manager.activeHandleDirection());
    }

    @Test
    void hoveredBoxClickConsumesInsteadOfStartingDraftBehindIt() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);

        clickBlock(manager, new BlockPos(10, 64, 10));
        manager.confirmDraft();

        Vec3 hoverOrigin = new Vec3(10.5D, 64.5D, 8.0D);
        Vec3 hoverDirection = new Vec3(0.0D, 0.0D, 1.0D);
        manager.updateHover(hoverOrigin, hoverDirection);
        assertEquals(1, manager.hoveredId());

        BlockPos behind = new BlockPos(10, 64, 12);
        assertTrue(manager.handleWorldAction(
                new BlockHitResult(Vec3.atCenterOf(behind), Direction.UP, behind, false),
                null,
                null));

        assertEquals(RtsCullingManager.Phase.IDLE, manager.phase(),
                "点击已有剔除盒时不应穿透到后面的方块并开始新草稿");
        assertEquals(1, manager.boxes().size());
        assertEquals(1, manager.selectedId());
    }

    private static void clickBlock(RtsCullingManager manager, BlockPos pos) {
        manager.handleWorldAction(
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false),
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 1.0D));
    }
}

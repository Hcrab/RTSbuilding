package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定“RTS 放置记录可直接回收，但仍尊重保护事件且绝不依赖玩家工具”的发布语义。 */
class RtsPlacedRecoveryBypassContractTest {

    @Test
    void trackedRecoveryMustBypassHarvestToolAndSilkTouchWithoutSwallowingBlock() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacedRecoveryService.java"));
        String capture = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningDropCapture.java"));

        assertTrue(source.contains("RtsClaimProtectionService.canBreakBlock("));
        assertTrue(source.contains("RtsMiningDropCapture.captureInstantRecovery("));
        assertTrue(source.contains("ItemStack internalTool = createInternalSilkTouchTool(level);"));
        assertTrue(source.contains("TemporaryContextSwitcher.withTemporaryMainHandItem("));
        assertTrue(source.contains("player.gameMode.destroyBlock(targetPos)"));
        assertTrue(source.contains("if (state.equals(level.getBlockState(targetPos)))"));
        assertTrue(source.contains("tracker.clear(targetPos);"));
        assertFalse(source.contains("getCloneItemStack(level, pos, state)"));
        assertTrue(capture.contains("PlayerEvent.HarvestCheck"));
        assertTrue(capture.contains("event.setCanHarvest(true)"));
        assertTrue(capture.contains("targetState.equals(event.getTargetBlock())"));
        assertTrue(capture.contains("event.getEntity() != context.player"));
        assertFalse(capture.contains("AABB"));

        String trackingSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/tracking/RtsBlockTrackingEvents.java"));
        assertTrue(trackingSource.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(trackingSource.contains("if (event.isCanceled())"));
    }
}

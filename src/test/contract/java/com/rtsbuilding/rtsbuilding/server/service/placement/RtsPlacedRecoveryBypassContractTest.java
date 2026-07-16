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

        assertTrue(source.contains("getCloneItemStack(level, pos, state)"));
        assertTrue(source.contains("ForgeHooks.onBlockBreakEvent("));
        assertTrue(source.contains("level.destroyBlock(pos, false, player)"));
        assertTrue(source.contains("if (recoveredBlock.isEmpty())"));
        assertTrue(source.contains("tracker.mark(targetPos);"));
        assertFalse(source.contains("Items.NETHERITE_PICKAXE"));
        assertFalse(source.contains("SILK_TOUCH"));
        assertFalse(source.contains("player.gameMode.destroyBlock(pos)"));

        String trackingSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/tracking/RtsBlockTrackingEvents.java"));
        assertTrue(trackingSource.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(trackingSource.contains("if (event.isCanceled())"));
    }
}

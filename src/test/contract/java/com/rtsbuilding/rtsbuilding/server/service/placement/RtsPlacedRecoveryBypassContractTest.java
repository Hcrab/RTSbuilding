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

        assertTrue(source.contains("getCloneItemStack(level, pos, state)"),
                "移除前必须先取得可回收的方块物品。即使玩家工具等级不足，也不能先删方块再等待掉落。 ");
        assertTrue(source.contains("CommonHooks.fireBlockBreak("),
                "直接回收仍必须允许区块保护和其他模组取消 BreakEvent。 ");
        assertTrue(source.contains("level.destroyBlock(pos, false, player)"),
                "已记录回收应无战利品移除，由回收队列持有唯一方块物品。 ");
        assertTrue(source.contains("if (recoveredBlock.isEmpty())"),
                "无法构造回收物时必须拒绝移除，不能吞方块。 ");
        assertTrue(source.contains("tracker.mark(targetPos);"),
                "保护事件之后若世界移除失败，必须恢复 RTS 放置记录。 ");
        assertFalse(source.contains("Items.NETHERITE_PICKAXE"),
                "回收语义不应继续伪装成某一固定等级的镐。 ");
        assertFalse(source.contains("SILK_TOUCH"),
                "回收语义不应依赖精准采集附魔。 ");
        assertFalse(source.contains("player.gameMode.destroyBlock(pos)"),
                "玩家采掘入口会重新应用当前工具与挖掘等级检查。 ");

        String trackingSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/tracking/RtsBlockTrackingEvents.java"));
        assertTrue(trackingSource.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(trackingSource.contains("if (event.isCanceled())"),
                "被保护模组取消的破坏不能提前丢失 RTS 放置记录。 ");
    }
}

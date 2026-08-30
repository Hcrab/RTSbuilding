package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPlacedRecoveryWithoutStorageContractTest {
    @Test
    void trackedRecoveryUsesInventoryFallbackWithoutRequiringLinkedStorage() throws Exception {
        String recovery = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacedRecoveryService.java"));
        String capture = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningDropCapture.java"));

        String directStoreBody = methodBody(capture, "private static void storeInstantRecoveryDrops");
        String inventoryFallbackBody = methodBody(
                capture, "private static ItemStack moveToInventoryWithReservedMainHand");

        assertFalse(recovery.contains("hasAnyStorage"),
                "恢复服务不应在玩家背包兜底之前拒绝无储存玩家。");
        assertTrue(directStoreBody.contains("storeToLinkedOnlyPreferExisting"),
                "瞬时回收必须先尝试链接储存。");
        assertTrue(directStoreBody.contains("moveToInventoryWithReservedMainHand")
                        && inventoryFallbackBody.contains("moveToPlayerInventoryOnly"),
                "无链接储存或链接储存已满时必须回退玩家背包。");
        assertFalse(directStoreBody.contains("player.drop("),
                "最终余量必须留给原版事件落地，不能另行生成玩家附近实体。");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        return source.substring(open);
    }
}

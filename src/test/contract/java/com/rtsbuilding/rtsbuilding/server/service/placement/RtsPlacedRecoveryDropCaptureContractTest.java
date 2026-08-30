package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPlacedRecoveryDropCaptureContractTest {
    @Test
    void directStrategyConsumesOnlyAcceptedFinalEventAmounts() throws Exception {
        String source = source();
        String direct = methodBody(source, "private static void storeInstantRecoveryDrops");

        assertTrue(source.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(source.contains("storeInstantRecoveryDrops(context, event.getDrops())"));
        assertTrue(direct.contains("Iterator<ItemEntity> iterator = drops.iterator()"));
        assertTrue(direct.contains("ItemStack original = entity.getItem()"));
        assertTrue(direct.contains("storeToLinkedOnlyPreferExisting"));
        assertTrue(direct.contains("moveToInventoryWithReservedMainHand"));
        assertTrue(direct.contains("int accepted = originalCount - remainder.getCount()"));
        assertTrue(direct.contains("if (accepted <= 0) continue"));
        assertTrue(direct.contains("iterator.remove()"));
        assertTrue(direct.contains("entity.setItem(remainder)"));
        assertFalse(direct.contains("enqueueCapturedDrops"));
        assertFalse(direct.contains("miningDropBuffer"));
        assertFalse(direct.contains("ItemStack.isSameItem"));
        assertFalse(source.contains("new ItemEntity("));
        assertFalse(source.contains("setDroppedExperience"));
        assertFalse(source.contains("ExperienceOrb"));

        String inventoryFallback = methodBody(
                source, "private static ItemStack moveToInventoryWithReservedMainHand");
        assertTrue(inventoryFallback.contains("moveToPlayerInventoryOnly"));
        assertTrue(inventoryFallback.contains("context.restoredMainHand = context.player.getMainHandItem()"));
        assertTrue(source.contains("player.setItemInHand(InteractionHand.MAIN_HAND, context.restoredMainHand)"));
    }

    @Test
    void disabledAutoStoreLeavesDropsUntouchedAndContextsRemainNested() throws Exception {
        String source = source();

        assertTrue(source.contains("if (!context.autoStoreDrops"));
        assertTrue(source.contains("stack.push(context)"));
        assertTrue(source.contains("stack.pop()"));
        assertTrue(source.contains("if (stack.isEmpty()) ACTIVE.remove()"));
        assertTrue(source.contains("event.getBreaker() != context.player"));
        assertTrue(source.contains("event.getLevel() != context.player.serverLevel()"));
        assertTrue(source.contains("context.targetPos.equals(event.getPos())"));
    }

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningDropCapture.java"));
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

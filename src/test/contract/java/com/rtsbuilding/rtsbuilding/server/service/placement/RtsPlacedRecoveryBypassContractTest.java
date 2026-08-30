package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPlacedRecoveryBypassContractTest {
    @Test
    void trackedRecoveryBypassesHarvestChecksWithoutWeakeningCleanClaims() throws Exception {
        String recovery = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacedRecoveryService.java"));
        String capture = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningDropCapture.java"));

        assertTrue(recovery.contains("player.gameMode.destroyBlock(targetPos)"));
        assertTrue(recovery.contains("TemporaryContextSwitcher.withTemporaryMainHandItem"));
        assertTrue(recovery.contains("Items.NETHERITE_PICKAXE"));
        assertTrue(recovery.contains("Enchantments.SILK_TOUCH"));
        assertTrue(recovery.contains("RtsMiningDropCapture.captureInstantRecovery"));
        assertTrue(recovery.contains("enum InstantRecoveryResult"));
        assertTrue(recovery.contains("NOT_TRACKED"));
        assertTrue(recovery.contains("REJECTED"));
        assertTrue(recovery.contains("FAILED"));
        assertTrue(recovery.contains("tracker.restoreSnapshot(targetPos, originalCredential);"));
        assertFalse(recovery.contains("tracker.mark("));
        assertTrue(recovery.contains("tracker.clear(targetPos);"));
        assertFalse(recovery.contains("getCloneItemStack"));
        assertFalse(recovery.contains("materializeRecoveredBlock"));
        assertFalse(recovery.contains("snapshotNearbyDrops"));
        assertFalse(recovery.contains("collectNewNearbyDrops"));
        assertFalse(recovery.contains("new PlacedRecoveryJob("));

        assertTrue(capture.contains("PlayerEvent.HarvestCheck"));
        assertTrue(capture.contains("event.setCanHarvest(true)"));
        assertTrue(capture.contains("context.targetPos.equals(event.getPos())"));
        assertTrue(capture.contains("event.getEntity() != context.player"));
        assertFalse(capture.contains("AABB"));

        String trackingSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/tracking/RtsBlockTrackingEvents.java"));
        assertTrue(trackingSource.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(trackingSource.contains("if (event.isCanceled())"));
    }
}

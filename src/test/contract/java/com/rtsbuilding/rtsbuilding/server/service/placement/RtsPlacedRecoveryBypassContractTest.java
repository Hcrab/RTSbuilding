package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPlacedRecoveryBypassContractTest {
    @Test
    void trackedRecoveryBypassesHarvestChecksWithoutWeakeningCleanClaims() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacedRecoveryService.java"));

        assertTrue(source.contains("getCloneItemStack(level, pos, state)"));
        assertTrue(source.contains("CommonHooks.fireBlockBreak("));
        assertTrue(source.contains("level.destroyBlock(pos, false, player)"));
        assertTrue(source.contains("if (recoveredBlock.isEmpty())"));
        assertTrue(source.contains("tracker.mark(targetPos);"));
        assertTrue(source.contains("materializeRecoveredBlock(level, targetPos, recoveredBlock)"));
        assertTrue(source.contains("new PlacedRecoveryClaim("));
        assertTrue(source.contains("requiredPersistedRevision() <= persistedPlacementRevision"));
        assertTrue(source.contains("claim.matches(droppedStack)"));
        assertFalse(source.contains("Items.NETHERITE_PICKAXE"));
        assertFalse(source.contains("SILK_TOUCH"));
        assertFalse(source.contains("player.gameMode.destroyBlock(pos)"));
        assertFalse(source.contains("stacks.addLast(recoveredBlock.copy())"));

        String trackingSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/tracking/RtsBlockTrackingEvents.java"));
        assertTrue(trackingSource.contains("@SubscribeEvent(priority = EventPriority.LOWEST)"));
        assertTrue(trackingSource.contains("if (event.isCanceled())"));
    }
}

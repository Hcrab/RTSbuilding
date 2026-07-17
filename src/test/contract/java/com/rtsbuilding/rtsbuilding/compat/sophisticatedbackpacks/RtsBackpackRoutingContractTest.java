package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBackpackRoutingContractTest {
    @Test
    void carriedBackpackKeepsUuidBindingAndPlacementNeverFallsBackToOpen() throws Exception {
        String compat = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/compat/sophisticatedbackpacks/RtsBackpackCompat.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String placement = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java"));
        String lifecycle = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/resolver/RtsLinkedStorageBlockEventHandler.java"));

        assertTrue(compat.contains("PlayerInventoryProvider$BackpackInventorySlotConsumer")
                        && compat.contains("findCarriedBackpack(player, uuid)"),
                "UUID resolution must cover Sophisticated Backpacks' carried and accessory slots.");
        assertTrue(screen.contains("forcePlace || forceBackpackPlacement")
                        && screen.contains("!forceBackpackPlacement && !forcePlace"),
                "Right-clicking a backpack must bypass interaction and enter placement.");
        assertTrue(placement.contains("forcePlace || sophisticatedBackpackPlacementOnly")
                        && placement.contains(
                        "!sophisticatedBackpackPlacementOnly && !selectedOutcome.result().consumesAction()"),
                "Failed backpack placement must not fall back to opening the backpack.");
        assertTrue(lifecycle.contains("markDetached(ref)"),
                "Moving a backpack off the ground must preserve its UUID binding.");
        assertFalse(lifecycle.contains("removeBrokenLinkedStorageRef"),
                "Moving a backpack off the ground must not delete its UUID binding.");
    }
}

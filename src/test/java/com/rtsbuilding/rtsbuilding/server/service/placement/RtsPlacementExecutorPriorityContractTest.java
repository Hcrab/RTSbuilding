package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPlacementExecutorPriorityContractTest {
    @Test
    void shiftMainHandPlacementFallsBackToNormalInteraction() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java"));
        String body = methodBody(source, "private static boolean placeWithMainHand");

        int forceFallback = body.indexOf("if (forcePlace) {", body.indexOf("mainHandUseFallback"));
        int blockInteractFallback = body.indexOf("useItemOnWithRealMainHand(player, level, hit, false)", forceFallback);
        int itemInteractFallback = body.indexOf("useItemWithRealMainHand(player, level, false)", forceFallback);

        assertTrue(forceFallback >= 0, "Shift placement should have a normal-interaction fallback block");
        assertTrue(blockInteractFallback > forceFallback,
                "failed Shift placement should try non-shift block interaction next");
        assertTrue(itemInteractFallback > blockInteractFallback,
                "failed non-shift block interaction should fall back to non-shift item use");
    }

    @Test
    void shiftStoragePlacementFallsBackToNormalInteractionWithRemainder() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java"));
        String body = methodBody(source, "private static boolean placeWithStorageItem");

        int firstFallback = body.indexOf("if (forcePlace && !finalOutcome.result().consumesAction())");
        int remainderCarry = body.indexOf("nextAttemptStack(finalOutcome, lastAttemptStack)", firstFallback);
        int blockInteractFallback = body.indexOf(
                "useItemOnWithMainHand(player, level, storageInteractStack, hit, false)",
                firstFallback);
        int itemInteractFallback = body.indexOf(
                "useItemWithMainHand(player, level, storageItemInteractStack, false)",
                firstFallback);

        assertTrue(firstFallback >= 0, "Shift storage placement should have a normal-interaction fallback block");
        assertTrue(remainderCarry > firstFallback && remainderCarry < blockInteractFallback,
                "storage fallback must continue with the previous remainder instead of recreating from item id");
        assertTrue(blockInteractFallback > firstFallback,
                "failed Shift storage placement should try non-shift block interaction next");
        assertTrue(itemInteractFallback > blockInteractFallback,
                "failed non-shift storage block interaction should fall back to non-shift item use");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}

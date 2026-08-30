package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DropBufferHotPathContractTest {
    @Test
    void timeoutMakesOneFinalStorageAttemptAndLimitsDroppedEntities() throws IOException {
        String source = read("server/service/mining/RtsDropAbsorber.java");
        assertTrue(source.contains("DropInsertContext insertContext = createInsertContext"));
        assertTrue(source.contains("int stackLimit = fallbackEligible ? Math.min(maxStacks, 16) : maxStacks"));
        assertTrue(source.contains("if (stored <= 0 && fallbackEligible"));
        assertTrue(source.contains("mergeRemainder(timedOutRemainders, remainder)"));
    }

    @Test
    void bufferIsPersistedWithFullStackComponents() throws IOException {
        String source = read("server/data/SessionSerializer.java");
        assertTrue(source.contains("serializeDropBuffer"));
        assertTrue(source.contains("save(player.registryAccess())"));
        assertTrue(source.contains("ItemStack.parseOptional(player.registryAccess()"));
    }

    @Test
    void miningCapturesExactPreSpawnDropsInsteadOfScanningWorldEntities() throws IOException {
        String capture = read("server/service/mining/RtsMiningDropCapture.java");
        String mining = read("server/service/mining/RtsMiningStateMachine.java");

        assertTrue(capture.contains("BlockDropsEvent"));
        assertTrue(capture.contains("EventPriority.LOWEST"));
        assertTrue(capture.contains("enqueueCapturedDrops"));
        assertTrue(mining.contains("RtsMiningDropCapture.capture(player, session"));
        assertFalse(mining.contains("absorbMinedDropsImmediately"));
        assertFalse(mining.contains("dropsToAbsorb"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}

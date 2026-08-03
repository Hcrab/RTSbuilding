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
        assertTrue(source.contains("DropInsertContext context = createInsertContext"));
        assertTrue(source.contains("int limit = fallback ? Math.min(maxStacks, 16) : maxStacks"));
        assertTrue(source.contains("if (stored <= 0 && fallback"));
        assertTrue(source.contains("mergeRemainder(worldRemainders, remainder)"));
    }

    @Test
    void bufferIsPersistedWithFullStackComponents() throws IOException {
        String source = read("server/data/SessionSerializer.java");
        assertTrue(source.contains("serializeDropBuffer"));
        assertTrue(source.contains("writeToNBT(new NBTTagCompound())"));
        assertTrue(source.contains("new ItemStack(stacks.getCompoundTagAt(i))"));
    }

    @Test
    void miningCapturesStandardAndDirectEntityDropsWithoutScanningExistingWorldEntities() throws IOException {
        String capture = read("server/service/mining/RtsMiningDropCapture.java");
        String mining = read("server/service/mining/RtsMiningStateMachine.java");

        assertTrue(capture.contains("BlockEvent.HarvestDropsEvent"));
        assertTrue(capture.contains("EntityJoinWorldEvent"));
        assertTrue(capture.contains("instanceof EntityItem"));
        assertTrue(capture.contains("event.setCanceled(true)"));
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

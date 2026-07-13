package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DropBufferHotPathContractTest {
    @Test
    void timeoutFallbackDoesNotResolveStorageAndLimitsDroppedEntities() throws IOException {
        String source = read("server/service/mining/RtsDropAbsorber.java");
        assertTrue(source.contains("DropInsertContext insertContext = timeout ? null : createInsertContext"));
        assertTrue(source.contains("int stackLimit = timeout ? Math.min(maxStacks, 16) : maxStacks"));
        assertTrue(source.contains("mergeRemainder(timedOutRemainders, remainder)"));
    }

    @Test
    void bufferIsPersistedWithFullStackComponents() throws IOException {
        String source = read("server/data/SessionSerializer.java");
        assertTrue(source.contains("serializeDropBuffer"));
        assertTrue(source.contains("save(player.registryAccess())"));
        assertTrue(source.contains("ItemStack.parseOptional(player.registryAccess()"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}

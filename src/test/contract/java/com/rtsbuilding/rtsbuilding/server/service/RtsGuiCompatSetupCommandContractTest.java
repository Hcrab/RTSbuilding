package com.rtsbuilding.rtsbuilding.server.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RtsGuiCompatSetupCommandContractTest {
    @Test
    void namespacedBlockIdsUseTheResourceLocationArgument() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsGuiCompatSetupCommand.java"));

        assertTrue(source.contains("Commands.argument(\"blockId\", ResourceLocationArgument.id())"));
        assertTrue(source.contains("ResourceLocationArgument.getId(context, \"blockId\").toString()"));
    }
}

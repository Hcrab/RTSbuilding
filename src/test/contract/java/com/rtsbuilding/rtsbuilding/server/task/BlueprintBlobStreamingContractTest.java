package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止大蓝图持久化重新退化成“完整压缩数组 + 完整文件数组”的双份峰值内存。 */
class BlueprintBlobStreamingContractTest {
    private static final Path REPOSITORY = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/task/persistence/asset/blueprint/"
                    + "AtomicBlueprintBlobRepository.java");
    private static final Path CODEC = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/task/persistence/asset/blueprint/"
                    + "BlueprintBlobCodec.java");

    @Test
    void repositoryUsesStreamingWriteAndLoadWithoutWholeFileArrays() throws IOException {
        String repository = Files.readString(REPOSITORY);

        assertTrue(repository.contains("Channels.newOutputStream(channel)"));
        assertTrue(repository.contains("codec.writeCompressed(record, output)"));
        assertTrue(repository.contains("new BufferedInputStream(Files.newInputStream(file))"));
        assertFalse(repository.contains("codec.encodeCompressed(record)"));
        assertFalse(repository.contains("Files.readAllBytes"));
    }

    @Test
    void freezeDoesNotConstructAThrowawayDeepCopiedRecord() throws IOException {
        String codec = Files.readString(CODEC);

        assertFalse(codec.contains("BlueprintBlobRecord draft"));
        assertFalse(codec.contains("\"0\".repeat(64)"));
        assertTrue(codec.contains("validateLogical(assetId, taskId, blockCount"));
    }
}

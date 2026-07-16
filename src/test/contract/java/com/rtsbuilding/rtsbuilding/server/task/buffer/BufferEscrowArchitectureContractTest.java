package com.rtsbuilding.rtsbuilding.server.task.buffer;

import com.rtsbuilding.rtsbuilding.server.task.BufferDrainTaskPayload;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferEscrowArchitectureContractTest {

    @Test
    void payloadContainsOnlyIdentityAndPureEscrowState() {
        Class<?>[] fieldTypes = Arrays.stream(BufferDrainTaskPayload.class.getRecordComponents())
                .map(component -> component.getType())
                .toArray(Class<?>[]::new);

        assertFalse(Arrays.asList(fieldTypes).contains(ServerPlayer.class));
        assertFalse(Arrays.asList(fieldTypes).contains(RtsStorageSession.class));
        assertTrue(Arrays.asList(fieldTypes).contains(BufferEscrowState.class));
    }

    @Test
    void detachedDrainMethodDoesNotUseSessionBufferAsLifecycleAuthority() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/buffer/RtsBufferEscrowExecutor.java"));
        int start = source.indexOf("public static BufferDrainSliceResult executeReservedDrainSlice(");
        int end = source.indexOf("public static BufferDrainTaskPayload snapshotLegacyBuffer(", start);
        assertTrue(start >= 0 && end > start, "找不到 detached drain 方法边界");

        String detachedMethod = source.substring(start, end);
        assertFalse(detachedMethod.contains("miningDropBuffer"));
        assertFalse(detachedMethod.contains("stacks.remove"));
        assertTrue(detachedMethod.contains("createInsertContext(player, transientSession)"));
    }

    @Test
    void crashRecoveryForReservedDrainIsExplicitNotAutomaticRetry() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/task/buffer/BufferEscrowState.java"));

        assertTrue(source.contains("DRAIN_OUTCOME_UNKNOWN"));
        assertTrue(source.contains("recoverLoadedSnapshot"));
        assertFalse(source.contains("recoverLoadedSnapshot().reserveDrainBatch"));
    }
}

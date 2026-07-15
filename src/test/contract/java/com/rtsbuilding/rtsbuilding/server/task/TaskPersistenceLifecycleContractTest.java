package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止事件整理时把 durable task 冲刷退回世界停止后，或放到 Session 清理之后。 */
class TaskPersistenceLifecycleContractTest {
    private static final Path MOD_ENTRY = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java");

    @Test
    void lifecycleUsesStartingPostTickOwnerLogoutAndStoppingBoundaries() throws IOException {
        String source = Files.readString(MOD_ENTRY);

        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.start(event.getServer())"));
        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.tick()"));
        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.flushOwner(serverPlayer.getUUID())"));
        assertTrue(source.contains("onServerStopping(ServerStoppingEvent event)"));
        assertEquals(1, occurrences(source, "TaskPersistenceRuntime.INSTANCE.stop()"),
                "stop 只能在 ServerStopping 调用，ServerStopped 不得再次触碰已 reset 的 runtime");

        int ownerFlush = source.indexOf("TaskPersistenceRuntime.INSTANCE.flushOwner(serverPlayer.getUUID())");
        int sessionCleanup = source.indexOf("ServiceRegistry.getInstance().session().onPlayerLogout(serverPlayer)");
        assertTrue(ownerFlush >= 0 && sessionCleanup > ownerFlush,
                "durable owner flush 必须发生在 Session 清理之前");

        int stopping = source.indexOf("onServerStopping(ServerStoppingEvent event)");
        int stopped = source.indexOf("onServerStopped(ServerStoppedEvent event)");
        assertTrue(stopping >= 0 && stopped > stopping,
                "最终 drain 必须位于 ServerStopping 生命周期，而不是世界停止之后");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}

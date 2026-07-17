package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止事件整理时把 durable task 冲刷退回世界停止后，或放到 Session 清理之后。 */
class TaskPersistenceLifecycleContractTest {
    private static final Path MOD_ENTRY = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java");

    @Test
    void lifecycleUsesStartingPostTickOwnerLogoutAndStoppedBoundaries() throws IOException {
        String source = Files.readString(MOD_ENTRY);

        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.start(event.getServer())"));
        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.tick()"));
        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.flushOwner(serverPlayer.getUUID())"));
        assertTrue(source.contains("onServerStopping(ServerStoppingEvent event)"));
        assertEquals(1, occurrences(source, "TaskPersistenceRuntime.INSTANCE.stop()"),
                "writer 只能关闭一次");

        int ownerFlush = source.indexOf("TaskPersistenceRuntime.INSTANCE.flushOwner(serverPlayer.getUUID())");
        int sessionCleanup = source.indexOf("ServiceRegistry.getInstance().session().onPlayerLogout(serverPlayer)");
        assertTrue(ownerFlush >= 0 && sessionCleanup > ownerFlush,
                "durable owner flush 必须发生在 Session 清理之前");
        int logoutCatch = source.indexOf("登出时 durable task 冲刷失败", ownerFlush);
        int playerDetach = source.indexOf("RtsCameraManager.stopIfActive(serverPlayer)", logoutCatch);
        assertTrue(logoutCatch >= 0 && playerDetach > logoutCatch);
        assertFalse(source.substring(logoutCatch, playerDetach).contains("throw failure"),
                "owner flush 失败必须继续 detach/Session/SaveScheduler 清理，dirty 留给后续重试");

        int stopping = source.indexOf("onServerStopping(ServerStoppingEvent event)");
        int stopped = source.indexOf("onServerStopped(ServerStoppedEvent event)");
        assertTrue(stopping >= 0 && stopped > stopping,
                "在线执行冻结必须先于玩家登出完成后的 writer 关闭");
        String stoppedBody = source.substring(stopped);
        int startedGuard = stoppedBody.indexOf("TaskPersistenceRuntime.INSTANCE.isStarted()");
        int stopCall = stoppedBody.indexOf("TaskPersistenceRuntime.INSTANCE.stop()");
        assertTrue(startedGuard >= 0 && stopCall > startedGuard,
                "启动读取失败也会触发 ServerStopped；必须先确认 Runtime 已启动再关闭 writer");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}

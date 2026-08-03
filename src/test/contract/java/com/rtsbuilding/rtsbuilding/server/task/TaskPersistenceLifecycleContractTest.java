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

        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.start(activeServer)"));
        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.tick()"));
        assertTrue(source.contains("TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUniqueID())"));
        assertTrue(source.contains("onServerStopping(FMLServerStoppingEvent event)"));
        assertEquals(1, occurrences(source, "TaskPersistenceRuntime.INSTANCE.stop()"),
                "writer 只能关闭一次");

        int logoutStart = source.indexOf("void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event)");
        int ownerFlush = source.indexOf(
                "TaskPersistenceRuntime.INSTANCE.flushOwner(player.getUniqueID())", logoutStart);
        int sessionCleanup = source.indexOf("ServiceRegistry.getInstance().session().onPlayerLogout(player)");
        assertTrue(ownerFlush >= 0 && sessionCleanup > ownerFlush,
                "durable owner flush 必须发生在 Session 清理之前");
        int logoutCatch = source.indexOf("} catch (RuntimeException failure) {", ownerFlush);
        int playerDetach = source.indexOf("RtsCameraManager.stopIfActive(player)", logoutCatch);
        assertTrue(logoutCatch >= 0 && playerDetach > logoutCatch);
        assertFalse(source.substring(logoutCatch, playerDetach).contains("throw failure"),
                "owner flush 失败必须继续 detach/Session/SaveScheduler 清理，dirty 留给后续重试");

        int stopping = source.indexOf("onServerStopping(FMLServerStoppingEvent event)");
        int stopped = source.indexOf("onServerStopped(FMLServerStoppedEvent event)");
        assertTrue(stopping >= 0 && stopped > stopping,
                "在线执行冻结必须先于玩家登出完成后的 writer 关闭");
        String stoppingBody = source.substring(stopping, stopped);
        String stoppedBody = source.substring(stopped);
        assertTrue(stoppingBody.contains("RtsWorkflowEngine.getInstance().saveAll(server)"),
                "1.12 工作流必须在 WorldServer 卸载前保存");
        assertFalse(stoppedBody.contains("RtsWorkflowEngine.getInstance().saveAll(server)"),
                "ServerStopped 阶段不得再通过 MinecraftServer 解析世界路径");
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

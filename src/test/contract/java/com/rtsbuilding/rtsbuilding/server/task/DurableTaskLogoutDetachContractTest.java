package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 登出是在线载荷 detach，不是 durable task 的业务取消命令。 */
class DurableTaskLogoutDetachContractTest {

    @Test
    void sessionLogoutDetachesTaskEngineInsteadOfCancellingOwnerLane() throws IOException {
        String sessionService = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsSessionServiceImpl.java"));
        String taskEngine = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/task/RtsTaskEngine.java"));

        assertTrue(sessionService.contains("RtsTaskEngine.INSTANCE.detachPlayer(player.getUUID())"));
        assertFalse(sessionService.contains("RtsTaskEngine.INSTANCE.onPlayerLogout(player.getUUID())"));
        assertTrue(taskEngine.contains("scheduler.detachOwner(playerId)"));
        assertTrue(taskEngine.contains("if (!isPhaseOneDurable(detached.type())) detached.cancel(now)"));
    }
}

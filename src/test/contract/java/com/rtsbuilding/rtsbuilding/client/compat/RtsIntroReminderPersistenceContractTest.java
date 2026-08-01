package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsIntroReminderPersistenceContractTest {
    @Test
    void explicitDismissalIsPersistedImmediately() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/common/persist/RtsClientUiStateStore.java"));
        int methodStart = source.indexOf("public static synchronized void dismissIntroReminder");
        int methodEnd = source.indexOf("\n    }", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("CACHE.flush()"),
                "玩家点击“不再提醒”后必须立即落盘，不能依赖打开 RTS 界面后的延迟刷新");
        assertFalse(method.contains("CACHE.markDirty()"),
                "显式关闭提醒不应只留在内存脏标记中");
    }
}

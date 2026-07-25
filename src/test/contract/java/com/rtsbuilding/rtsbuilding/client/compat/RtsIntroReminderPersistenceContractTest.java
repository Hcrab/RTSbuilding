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
                "src/main/java/com/rtsbuilding/rtsbuilding/client/state/RtsClientUiStateStore.java"));
        int methodStart = source.indexOf("public static synchronized void dismissIntroReminder");
        int methodEnd = source.indexOf(
                "public static synchronized boolean isContainerOverlayEnabled", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("key == null || key.isBlank()"),
                "未解析出存档或服务器身份时不能写入共享的空作用域");
        assertTrue(method.contains("save(state)"),
                "玩家点击“不再提醒”后必须立即落盘，不能依赖打开 RTS 界面后的延迟刷新");
        assertFalse(method.contains("markDirty"),
                "显式关闭提醒不应只留在内存脏标记中");
    }
}

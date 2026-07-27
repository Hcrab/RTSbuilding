package com.rtsbuilding.rtsbuilding.server.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsHomeCameraStartContractTest {
    @Test
    void homeOnlyLimitsWhereTheRtsSessionCanStart() throws IOException {
        String progression = read("server/progression/RtsProgressionManager.java");
        String camera = read("server/camera/RtsCameraManager.java");
        String worldAccess = read("server/storage/resolver/RtsLinkedStorageResolver.java");

        assertTrue(progression.contains("RtsHomeManager.canOpenRtsNearHome(player)"),
                "普通 RTS 启动必须检查玩家是否位于家园周围的 3x3 区块内");
        assertTrue(camera.contains("\"message.rtsbuilding.home.too_far\""),
                "超出 3x3 启动区域时应给玩家 actionbar 提示");
        assertTrue(camera.contains("TextFormatting.RED")
                        && camera.contains("setBold(Boolean.TRUE)"),
                "距离提示应使用醒目的红色粗体样式");
        assertFalse(worldAccess.contains("canAccessHomeRadius"),
                "会话启动后，世界操作范围不得再与家园半径取交集");
        assertFalse(camera.contains("Component.literal(\"Set an RTS home first.\")"),
                "相机启动失败提示必须使用 i18n");
    }

    private static String read(String relative) throws IOException {
        Path path = Paths.get("src/main/java/com/rtsbuilding/rtsbuilding", relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

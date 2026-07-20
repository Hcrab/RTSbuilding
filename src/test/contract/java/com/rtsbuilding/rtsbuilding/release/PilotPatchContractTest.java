package com.rtsbuilding.rtsbuilding.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotPatchContractTest {
    @Test
    void pilotVersionKeepsPatch4ClientDefaultsAndLocalizedCameraHint() throws Exception {
        String properties = Files.readString(Path.of("gradle.properties"));
        String config = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String camera = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/camera/RtsCameraManager.java"));
        String zhCn = Files.readString(Path.of(
                "src/main/resources/assets/rtsbuilding/lang/zh_cn.json"));

        assertTrue(properties.contains("mod_version=1.1.6-pilot"));
        assertTrue(config.contains(".define(\"useBlockGhostPreview\", false)"));
        assertTrue(camera.contains("\"message.rtsbuilding.camera_locked\""));
        assertTrue(camera.contains("\"item.rtsbuilding.rts_control_core\""));
        assertFalse(camera.contains("Component.literal(\"RTS camera is not unlocked.\")"));
        assertTrue(zhCn.contains("\"message.rtsbuilding.camera_locked\""));
        assertTrue(zhCn.contains("\"item.rtsbuilding.rts_control_core\""));
        assertTrue(zhCn.contains("当前版本为 1.1.6-pilot"));
        assertTrue(zhCn.contains("请退回 1.1.5-patch4"));
        assertTrue(Files.isRegularFile(Path.of(".github/workflows/publish-mod-release.yml")));
    }
}

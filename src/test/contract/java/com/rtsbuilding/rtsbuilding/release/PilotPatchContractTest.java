package com.rtsbuilding.rtsbuilding.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotPatchContractTest {
    @Test
    void releaseVersionKeepsClientDefaultsLocalizedCameraHintAndExplicitStableVersion() throws Exception {
        String properties = Files.readString(Path.of("gradle.properties"));
        String config = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String camera = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/camera/RtsCameraManager.java"));
        String onboarding = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientOnboardingReminder.java"));
        String zhCn = Files.readString(Path.of(
                "src/main/resources/assets/rtsbuilding/lang/zh_cn.json"));

        assertTrue(properties.lines().anyMatch("mod_version=1.1.6-patch3"::equals));
        assertTrue(config.contains(".define(\"useBlockGhostPreview\", false)"));
        assertTrue(camera.contains("\"message.rtsbuilding.camera_locked\""));
        assertTrue(camera.contains("\"item.rtsbuilding.rts_control_core\""));
        assertFalse(camera.contains("Component.literal(\"RTS camera is not unlocked.\")"));
        assertTrue(zhCn.contains("\"message.rtsbuilding.camera_locked\""));
        assertTrue(zhCn.contains("\"item.rtsbuilding.rts_control_core\""));
        assertTrue(onboarding.contains("getModContainerById(RtsbuildingMod.MODID)"));
        assertTrue(onboarding.contains("Component.literal(currentDisplayVersion())"));
        assertTrue(onboarding.contains("STABLE_VERSION = \"1.1.6\""));
        assertTrue(onboarding.contains("version.indexOf('-')"));
        assertTrue(zhCn.contains("当前测试版本：%1$s；稳定版：%2$s"));
        assertTrue(zhCn.contains("请退回稳定版"));
        assertTrue(Files.isRegularFile(Path.of(".github/workflows/publish-mod-release.yml")));
    }
}

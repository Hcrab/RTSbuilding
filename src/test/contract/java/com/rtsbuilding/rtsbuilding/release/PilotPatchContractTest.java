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
                "src/main/resources/assets/rtsbuilding/lang/zh_cn.lang"));

        assertTrue(properties.lines().map(String::trim)
                .anyMatch("mod_version = 0.0.2"::equals));
        assertTrue(properties.lines().map(String::trim)
                .anyMatch("mod_archive_name = rtsbuilding-forge-1.12.2"::equals));
        assertTrue(properties.lines().map(String::trim)
                .anyMatch("release_type = alpha"::equals));
        assertTrue(config.matches("(?s).*\"useBlockGhostPreview\",\\s*false,.*"));
        assertTrue(camera.contains("\"message.rtsbuilding.camera_locked\""));
        assertTrue(camera.contains("\"item.rtsbuilding.rts_control_core\""));
        assertFalse(camera.contains("new TextComponentString(\"RTS camera is not unlocked.\")"));
        assertTrue(zhCn.contains("message.rtsbuilding.camera_locked="));
        assertTrue(zhCn.contains("item.rtsbuilding.rts_control_core="));
        assertTrue(onboarding.contains(
                "Loader.instance().getIndexedModList().get(RtsbuildingMod.MODID)"));
        assertTrue(onboarding.contains("new TextComponentString(currentDisplayVersion())"));
        assertTrue(onboarding.contains("STABLE_VERSION = \"1.1.6-1.12.2-port\""));
        assertTrue(onboarding.contains("version.indexOf('-')"));
        assertTrue(zhCn.contains("chat.rtsbuilding.intro.version_warning="));
        assertTrue(zhCn.contains("%1$s") && zhCn.contains("%2$s") && zhCn.contains("%3$s"));
        assertTrue(Files.isRegularFile(Path.of(".github/workflows/publish-mod-release.yml")));
    }
}

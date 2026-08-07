package com.rtsbuilding.rtsbuilding.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge 1.19.2 0.0.2 测试线的版本与玩家提示契约。
 *
 * <p>语言文件不硬编码当前版本；入门提醒必须从 Forge ModContainer 读取实际版本，
 * 并把本版本线回退说明与官网作为参数传入。</p>
 */
class PilotPatchContractTest {
    @Test
    void forgePatchKeepsClientDefaultsAndReportsItsOwnVersionLine() throws Exception {
        String properties = Files.readString(Path.of("gradle.properties"));
        String config = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String camera = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/camera/RtsCameraManager.java"));
        String onboarding = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientOnboardingReminder.java"));
        String zhCn = Files.readString(Path.of(
                "src/main/resources/assets/rtsbuilding/lang/zh_cn.json"));

        assertTrue(properties.lines().anyMatch("mod_version=0.0.2"::equals),
                "Forge 1.19.2 测试线必须报告 0.0.2 版本");
        assertTrue(properties.lines().anyMatch(
                        "mod_archive_name=rtsbuilding-forge-1.19.2"::equals),
                "JAR 名必须显式包含加载器和 Minecraft 版本");
        assertTrue(config.contains(".define(\"useBlockGhostPreview\", false)"));
        assertTrue(camera.contains("\"message.rtsbuilding.camera_locked\""));
        assertTrue(camera.contains("\"item.rtsbuilding.rts_control_core\""));
        assertFalse(camera.contains("Component.literal(\"RTS camera is not unlocked.\")"));
        assertTrue(zhCn.contains("\"message.rtsbuilding.camera_locked\""));
        assertTrue(zhCn.contains("\"item.rtsbuilding.rts_control_core\""));
        assertTrue(onboarding.contains("ModList.get()")
                        && onboarding.contains("getModContainerById(RtsbuildingMod.MODID)")
                        && onboarding.contains("Component.literal(currentModVersion())")
                        && onboarding.contains("STABLE_VERSION = \"No prior Forge 1.19.2 release\"")
                        && onboarding.contains("Component.literal(STABLE_VERSION)")
                        && onboarding.contains("websiteComponent()"),
                "入门提醒必须读取实际版本，并传入本线回退说明和官网链接");
        assertTrue(zhCn.contains("%1$s") && zhCn.contains("%2$s") && zhCn.contains("%3$s"),
                "入门提醒翻译必须为当前版本、回退说明和官网保留三个占位符");
        assertFalse(zhCn.contains("1.1.6-pilot") || zhCn.contains("1.1.5-patch4"),
                "语言资源不能硬编码任一发布线版本号");
    }
}

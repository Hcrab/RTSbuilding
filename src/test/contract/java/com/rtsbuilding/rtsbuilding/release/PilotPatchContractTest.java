package com.rtsbuilding.rtsbuilding.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge 1.19.2 Alpha 移植线的版本与玩家提示契约。
 *
 * <p>移植线使用独立的 0.0.x 序列，因此语言文件不硬编码主线版本；入门提醒必须
 * 从 Forge ModContainer 读取实际版本，并把版本与官网作为两个参数传入。</p>
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

        assertTrue(properties.lines().anyMatch("mod_version=0.0.1"::equals),
                "Forge 1.19.2 Alpha 必须使用独立的 0.0.x 版本");
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
                        && onboarding.contains("websiteComponent()"),
                "入门提醒必须从运行中的 Forge ModContainer 读取实际版本，并传入官网链接");
        assertTrue(zhCn.contains("%1$s") && zhCn.contains("%2$s"),
                "入门提醒翻译必须为实际版本和官网保留两个占位符");
        assertFalse(zhCn.contains("1.1.6-pilot") || zhCn.contains("1.1.5-patch4"),
                "语言资源不能硬编码任一发布线版本号");
    }
}

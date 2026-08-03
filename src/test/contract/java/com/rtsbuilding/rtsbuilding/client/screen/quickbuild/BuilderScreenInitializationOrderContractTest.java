package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 Quick Build 初始化再次早于鼠标射线依赖绑定。
 *
 * <p>这里验证的是构造阶段依赖顺序，而不是玩家运行时行为。完整的射线与形状行为仍由
 * GameTest 和客户端人工验收覆盖。</p>
 */
class BuilderScreenInitializationOrderContractTest {
    @Test
    void cursorPickerIsBoundBeforeQuickBuildSnapshotCanReadRaycastState() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));

        int cursorInit = source.indexOf("this.cursorPicker.init(this, this.controller, this.shapeController);");
        int quickBuildInit = source.indexOf("this.quickBuildPanel.init(this, this.controller);");

        assertTrue(cursorInit >= 0, "BuilderScreen should bind ScreenCursorPicker");
        assertTrue(quickBuildInit >= 0, "BuilderScreen should initialize QuickBuildPanel");
        assertTrue(cursorInit < quickBuildInit,
                "ScreenCursorPicker must be bound before QuickBuildPanel creates its first snapshot");
    }

    @Test
    void constructorSnapshotDoesNotReadMinecraftFromUnattachedScreen() throws IOException {
        String adapter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildUiAdapter.java"));
        String panel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));

        assertTrue(!adapter.contains("uiScreen().getMinecraft()"),
                "BuilderScreen 构造期快照不能读取尚未挂载的 Screen.minecraft");
        assertTrue(panel.contains("Minecraft.getMinecraft().player"),
                "构造期需要玩家模式时应通过客户端单例安全读取");
    }
}

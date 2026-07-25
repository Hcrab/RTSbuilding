package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 Quick Build 初始化再次早于鼠标射线依赖绑定。
 */
class BuilderScreenInitializationOrderContractTest {
    @Test
    void cursorPickerIsBoundBeforeQuickBuildSnapshotCanReadRaycastState() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));

        int cursorInit = source.indexOf("this.cursorPicker.init(this, this.controller, this.shapeController);");
        int quickBuildInit = source.indexOf("this.quickBuildPanel.init(this, this.controller);");

        assertTrue(cursorInit >= 0, "BuilderScreen should bind ScreenCursorPicker");
        assertTrue(quickBuildInit >= 0, "BuilderScreen should initialize QuickBuildPanel");
        assertTrue(cursorInit < quickBuildInit,
                "ScreenCursorPicker must be bound before QuickBuildPanel creates its first snapshot");
    }
}

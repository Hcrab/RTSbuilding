package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁住普通交互目标框与 BuilderScreen 共用同一套固定 RTS Scale 鼠标坐标。 */
class InteractionTargetCoordinateFrameContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/overlay/InteractionTargetRenderer.java");

    @Test
    void uiOcclusionUsesBuilderScreensRecordedCursorInsteadOfConvertingRawMouseAgain() throws IOException {
        String source = Files.readString(SOURCE);
        String method = between(source,
                "private static boolean isInteractionBlockedByUI", "// ══════════════════════════════════════════════");

        assertTrue(method.contains("builderScreen.getCurrentMouseX()"));
        assertTrue(method.contains("builderScreen.getCurrentMouseY()"));
        assertTrue(method.contains("builderScreen.isWorldArea(mouseX, mouseY)"));
        assertFalse(method.contains("minecraft.mouseHandler"));
        assertFalse(method.contains("getGuiScaledWidth"));
        assertFalse(method.contains("getGuiScaledHeight"));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0 && endIndex > startIndex,
                () -> "无法定位源码片段: " + start + " ... " + end);
        return source.substring(startIndex, endIndex);
    }
}

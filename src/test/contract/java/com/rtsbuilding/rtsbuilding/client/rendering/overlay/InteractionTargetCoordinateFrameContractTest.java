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
        String method = methodBody(source, "private static boolean isInteractionBlockedByUi");

        assertTrue(method.contains("getMethod(\"getCurrentMouseX\")"));
        assertTrue(method.contains("getMethod(\"getCurrentMouseY\")"));
        assertTrue(method.contains("getMethod(\"isWorldArea\",double.class,double.class)"));
        assertFalse(method.contains("Mouse.getX()"));
        assertFalse(method.contains("Mouse.getY()"));
        assertFalse(method.contains("minecraft.displayWidth"));
        assertFalse(method.contains("minecraft.displayHeight"));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}

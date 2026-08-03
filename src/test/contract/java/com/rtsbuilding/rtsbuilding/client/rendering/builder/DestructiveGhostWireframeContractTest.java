package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定范围破坏包围盒在 GL_LINES 下逐边提交，避免再次退化成残缺四边。 */
class DestructiveGhostWireframeContractTest {
    @Test
    void destructivePreviewUsesTwelveIndependentEdges() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/DestructiveGhostRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("begin(LINES, GL11.GL_LINES"));
        assertTrue(source.contains("appendLineBox(LINES"));
        assertTrue(source.contains("appendLineBox(\n                        NODEPTH"));
        assertFalse(source.contains("RenderGlobal.drawBoundingBox"));

        int methodStart = source.indexOf("static void appendLineBox");
        int methodEnd = source.indexOf("private static void line", methodStart);
        String method = source.substring(methodStart, methodEnd);
        assertEquals(12, occurrences(method, "line(buffer,"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int from = 0; (from = source.indexOf(needle, from)) >= 0; from += needle.length()) {
            count++;
        }
        return count;
    }
}

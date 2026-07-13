package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapePreviewLimitContractTest {
    @Test
    void ordinaryAndAdvancedPreviewsClampBeforeGeometryGenerationAndReuseThePlan() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String method = methodBody(source, "private List<BlockPos> generateShapePositions");

        int clamp = method.indexOf("ShapeSelectionLimiter.clampDimensions");
        int cacheLookup = method.indexOf("key.equals(this.generatedShapeKey)");
        int advancedBuild = method.indexOf("ShapeGeometryUtil.buildAdvancedShapePositions");
        int normalBuild = method.indexOf("ShapeGeometryUtil.buildShapePositions");
        assertTrue(clamp >= 0 && clamp < advancedBuild && clamp < normalBuild,
                "长宽高必须在高级/普通几何列表生成前限幅");
        assertTrue(cacheLookup >= 0 && cacheLookup < advancedBuild && cacheLookup < normalBuild,
                "同一预览状态应复用形状计划，避免尺寸、计数和渲染重复生成大列表");
        assertTrue(method.contains(": SHAPE_MAX_DIMENSION"),
                "范围建造预览应使用与高级模式一致的形状维度上限");
    }

    @Test
    void sharedAnimatorFeedsNormalPreviewAdvancedHandlesAndDestroyEnvelope() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String selectionRenderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/AdvancedShapeSelectionBoxRenderer.java"));
        String ghostRenderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/ShapeGhostRenderer.java"));

        assertTrue(controller.contains("this.shapeBoxAnimator.renderAabb(this.generatedShapeBounds)"));
        assertTrue(selectionRenderer.contains("shapeSelectionRenderAabb()"));
        assertTrue(selectionRenderer.contains("if (!screen.isAdvancedShapeMode())"),
                "普通模式应保留平滑范围框，但不显示高级箭头");
        assertTrue(ghostRenderer.contains("screen.getShapeController().shapeSelectionRenderAabb()"),
                "范围破坏外框也必须使用同一份平滑 AABB");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
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

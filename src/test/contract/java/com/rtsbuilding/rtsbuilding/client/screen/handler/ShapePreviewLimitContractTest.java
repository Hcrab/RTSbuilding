package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapePreviewLimitContractTest {
    @Test
    void ordinaryAndAdvancedPreviewsClampBeforeGeometryGenerationAndReuseThePlan() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String planner = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeGenerationPlanCache.java"));
        String operations = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeWorldOperationPlanner.java"));
        String limiter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/RangeDestroySelectionLimiter.java"));
        String controllerMethod = methodBody(
                operations,
                "public List<BlockPos> generate");
        String planMethod = methodBody(
                planner,
                "public List<BlockPos> positions");

        int rangeClamp = planMethod.indexOf("RangeDestroySelectionLimiter.clampInput");
        int buildClamp = planMethod.indexOf("ShapeSelectionLimiter.clampDimensions");
        int cacheLookup = planMethod.indexOf("key.equals(this.cachedKey)");
        int advancedBuild = planMethod.indexOf("ShapeGeometryUtil.buildAdvancedShapePositions");
        int normalBuild = planMethod.indexOf("ShapeGeometryUtil.buildShapePositions");
        int rangeDestroyBuild = planMethod.indexOf("ShapeGeometryUtil.buildRangeDestroyShapePositions");
        assertTrue(rangeClamp >= 0
                        && buildClamp >= 0
                        && rangeClamp < advancedBuild
                        && buildClamp < advancedBuild
                        && rangeClamp < normalBuild
                        && buildClamp < normalBuild,
                "长宽高必须在高级/普通几何列表生成前限幅");
        assertTrue(rangeDestroyBuild > rangeClamp,
                "范围破坏必须走不含建造 32 格硬上限的独立几何入口");
        assertTrue(limiter.contains("ShapeSelectionLimiter.clampDimensionsAndVolume"),
                "范围破坏必须在几何生成前同时限制 XYZ 和覆盖体积");
        assertTrue(cacheLookup >= 0 && cacheLookup < advancedBuild && cacheLookup < normalBuild,
                "同一预览状态应复用形状计划，避免尺寸、计数和渲染重复生成大列表");
        assertTrue(controllerMethod.contains("this.generationPlans.positions"));
        assertTrue(controllerMethod.contains("new ShapeGenerationPlanCache.Request"));
        assertTrue(controllerMethod.contains("currentRangeDestroyLimits()"));
        assertTrue(controllerMethod.contains("SHAPE_MAX_DIMENSION"),
                "控制器必须把范围建造上限显式交给纯计划 owner");
        assertFalse(controller.contains("private record ShapeGenerationKey"));
        assertFalse(controller.contains("generatedShapePositions"));
        assertFalse(controller.contains("generatedShapeBounds"));
    }

    @Test
    void sharedAnimatorFeedsNormalPreviewAdvancedHandlesAndDestroyEnvelope() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String selectionRenderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/AdvancedShapeSelectionBoxRenderer.java"));
        String operations = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeWorldOperationPlanner.java"));
        String selectionBox = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeSelectionBoxController.java"));
        String ghostRenderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/rendering/builder/ShapeGhostRenderer.java"));

        assertTrue(controller.contains("this.worldOperations.generatedBounds()"));
        assertTrue(operations.contains("return this.generationPlans.bounds()"));
        assertTrue(selectionBox.contains("this.animator.renderAabb(generatedBounds)"));
        assertTrue(selectionRenderer.contains("shapeSelectionRenderAabb()"));
        assertTrue(selectionRenderer.contains("if (!screen.isAdvancedShapeMode())"),
                "普通模式应保留平滑范围框，但不显示高级箭头");
        assertTrue(ghostRenderer.contains("screen.getShapeController().shapeSelectionRenderAabb()"),
                "范围破坏外框也必须使用同一份平滑 AABB");
    }

    @Test
    void planCacheCannotReadScreenWorldConfigItemsOrNetwork() throws IOException {
        String planner = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeGenerationPlanCache.java"));

        assertFalse(planner.contains("BuilderScreen"));
        assertFalse(planner.contains("ClientRtsController"));
        assertFalse(planner.contains("import com.rtsbuilding.rtsbuilding.Config"));
        assertFalse(planner.contains("import net.minecraft.client"));
        assertFalse(planner.contains("ItemStack"));
        assertFalse(planner.contains("Packet"));
        assertTrue(planner.contains("List.copyOf(positions)"),
                "缓存不得向尺寸、成本或渲染调用方暴露可变坐标列表");
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

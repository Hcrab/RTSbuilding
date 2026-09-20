package com.rtsbuilding.rtsbuilding.client.screen.handler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapePreviewLimitContractTest {
    @Test
    void planCacheClampsBeforeGeometryAndReusesImmutablePlan() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String planner = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeGenerationPlanCache.java"));
        String operations = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeWorldOperationPlanner.java"));
        String limiter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/RangeDestroySelectionLimiter.java"));
        String planMethod = methodBody(planner, "public ShapeGenerationResult plan");

        int rangeClamp = planMethod.indexOf("RangeDestroySelectionLimiter.clampInput");
        int buildClamp = planMethod.indexOf("ShapeSelectionLimiter.clampShapeDimensions");
        int cacheLookup = planMethod.indexOf("key.equals(this.cachedKey)");
        int advancedBuild = planMethod.indexOf("ShapeGeometryUtil.buildAdvancedShapePlan");
        int normalBuild = planMethod.indexOf("ShapeGeometryUtil.buildShapePlan");
        int rangeDestroyBuild = planMethod.indexOf("ShapeGeometryUtil.buildRangeDestroyShapePlan");
        assertTrue(rangeClamp >= 0 && buildClamp >= 0
                        && rangeClamp < advancedBuild && buildClamp < advancedBuild
                        && rangeClamp < normalBuild && buildClamp < normalBuild,
                "长宽高必须在高级/普通几何计划生成前限幅");
        assertTrue(rangeDestroyBuild > rangeClamp,
                "范围破坏必须走不含建造 32 格硬上限的独立几何入口");
        assertTrue(limiter.contains("ShapeSelectionLimiter.clampDimensionsAndVolume"),
                "范围破坏必须在几何生成前同时限制 XYZ 和覆盖体积");
        assertTrue(cacheLookup >= 0 && cacheLookup < advancedBuild && cacheLookup < normalBuild,
                "同一预览状态应复用形状计划");
        assertTrue(planMethod.contains("request.buildMaxRadius()")
                        && planner.contains("cachedResult = result"),
                "半径必须进入缓存键/计划结果，且缓存保存完整不可变结果");
        assertTrue(controller.contains("this.worldOperations.generationPlan("));
        assertTrue(operations.contains("return this.generationPlans.bounds()"));
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
        assertTrue(selectionRenderer.contains("if (!screen.isAdvancedShapeMode())"));
        assertTrue(ghostRenderer.contains("screen.getShapeController().shapeSelectionRenderAabb()"));
    }

    @Test
    void planCacheOwnsStatusAndImmutableResultInsteadOfScreenOrNetworkState() throws IOException {
        String planner = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeGenerationPlanCache.java"));

        assertFalse(planner.contains("BuilderScreen"));
        assertFalse(planner.contains("ClientRtsController"));
        assertFalse(planner.contains("import com.rtsbuilding.rtsbuilding.Config"));
        assertFalse(planner.contains("import net.minecraft.client"));
        assertFalse(planner.contains("ItemStack"));
        assertFalse(planner.contains("Packet"));
        assertTrue(planner.contains("ShapeGenerationResult"));
        assertTrue(planner.contains("cachedResult"));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(bodyStart, i + 1);
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}

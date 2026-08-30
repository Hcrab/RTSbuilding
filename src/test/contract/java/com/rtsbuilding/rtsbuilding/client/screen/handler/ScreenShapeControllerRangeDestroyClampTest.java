package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.shape.RangeDestroySelectionLimiter;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenShapeControllerRangeDestroyClampTest {
    @Test
    void oversizedCircleClampsRadiusBeforeGeneratingTargets() {
        BlockPos origin = new BlockPos(0, 64, 0);
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.CIRCLE,
                Direction.UP,
                Direction.UP,
                origin,
                new BlockPos(100, 64, 0),
                0,
                false);

        RangeDestroySelectionLimiter.Limits limits =
                new RangeDestroySelectionLimiter.Limits(12, 12, 12, 1728);
        ShapeBuildTypes.Input clamped =
                RangeDestroySelectionLimiter.clampDimensions(input, limits);
        List<BlockPos> positions = ShapeGeometryUtil.buildShapePositions(clamped, ShapeFillMode.FILL);
        List<BlockPos> capped = RangeDestroySelectionLimiter.clampRoundPositions(
                clamped, positions, limits);

        assertTrue(capped.contains(new BlockPos(5, 64, 0)));
        assertTrue(capped.contains(new BlockPos(-5, 64, 0)));
        assertTrue(capped.contains(new BlockPos(0, 64, 5)));
        assertTrue(capped.contains(new BlockPos(0, 64, -5)));
        assertFalse(capped.contains(new BlockPos(6, 64, 0)));
        assertFalse(capped.contains(new BlockPos(5, 64, 5)));
    }

    @Test
    void oversizedCylinderClampsBaseRadiusAndHeightBeforeGeneratingTargets() {
        BlockPos origin = new BlockPos(0, 64, 0);
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.CYLINDER,
                Direction.UP,
                Direction.UP,
                origin,
                new BlockPos(100, 64, 0),
                100,
                false);

        RangeDestroySelectionLimiter.Limits limits =
                new RangeDestroySelectionLimiter.Limits(12, 12, 12, 1728);
        ShapeBuildTypes.Input clamped =
                RangeDestroySelectionLimiter.clampDimensions(input, limits);
        List<BlockPos> positions = ShapeGeometryUtil.buildShapePositions(clamped, ShapeFillMode.FILL);
        List<BlockPos> capped = RangeDestroySelectionLimiter.clampRoundPositions(
                clamped, positions, limits);

        assertTrue(capped.contains(new BlockPos(5, 64, 0)));
        assertTrue(capped.contains(new BlockPos(-5, 64, 0)));
        assertTrue(capped.contains(new BlockPos(0, 75, 0)));
        assertFalse(capped.contains(new BlockPos(6, 64, 0)));
        assertFalse(capped.contains(new BlockPos(0, 76, 0)));
    }

    @Test
    void oversizedRectilinearPreviewIsClampedBeforeGeneratingTargets() {
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.BOX,
                Direction.UP,
                Direction.UP,
                BlockPos.ZERO,
                new BlockPos(599, 0, 599),
                599,
                false);

        ShapeBuildTypes.Input clamped =
                RangeDestroySelectionLimiter.clampDimensions(
                        input,
                        new RangeDestroySelectionLimiter.Limits(
                                32,
                                32,
                                32,
                                32 * 32 * 32));
        List<BlockPos> positions = ShapeGeometryUtil.buildShapePositions(clamped, ShapeFillMode.FILL);

        assertTrue(positions.size() <= 32 * 32 * 32,
                "预览生成前就应把超大长宽高限到单次批量上限内");
        assertFalse(positions.contains(new BlockPos(32, 0, 0)));
        assertFalse(positions.contains(new BlockPos(0, 32, 0)));
        assertFalse(positions.contains(new BlockPos(0, 0, 32)));
    }

    @Test
    void advancedRoundShapesStartFromCenteredOrdinaryPreview() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String geometry = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/AdvancedShapeSelectionGeometry.java"));
        String session = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeSelectionSession.java"));
        String readySession = methodBody(
                session,
                "private ShapeBuildTypes.Session ready");

        assertTrue(geometry.contains("case CIRCLE -> centeredPlaneBox("),
                "advanced circle should begin as a centered normal circle envelope");
        assertTrue(geometry.contains("case CYLINDER -> centeredPlaneBox("),
                "advanced cylinder should begin from the same centered circular base");
        assertTrue(geometry.contains("case BALL -> centeredBox(center, spatialRadius(center, pointB))"),
                "advanced ball should begin as a centered sphere envelope");
        assertTrue(readySession.contains("AdvancedShapeSelectionGeometry.initialBox(ready)"),
                "ready advanced sessions should use the shape-aware initial box instead of the raw diagonal");
        assertFalse(controller.contains("private RtsCullingBox initialAdvancedShapeBox("));
        assertFalse(controller.contains("private static RtsCullingBox boxFromSession("));
        assertFalse(controller.contains("private static ShapeBuildTypes.Session sessionFromBox("));
    }

    @Test
    void rangeDestroyCapsUseTheDedicatedPureLimiter() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String planner = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeGenerationPlanCache.java"));
        String operationPlanner = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeWorldOperationPlanner.java"));
        String selectionBox = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeSelectionBoxController.java"));
        String limiter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/RangeDestroySelectionLimiter.java"));

        assertTrue(operationPlanner.contains("new ShapeGenerationPlanCache.Request("));
        assertTrue(operationPlanner.contains("currentRangeDestroyLimits()"));
        assertTrue(planner.contains("RangeDestroySelectionLimiter.clampInput("));
        assertTrue(planner.contains("RangeDestroySelectionLimiter.clampRoundPositions("));
        assertTrue(planner.contains("RangeDestroySelectionLimiter.clampPositions("));
        assertTrue(selectionBox.contains("RangeDestroySelectionLimiter.clampBox("));
        assertTrue(limiter.contains("record Limits("));
        assertFalse(controller.contains("RangeDestroySelectionLimiter.clampInput("));
        assertFalse(controller.contains("RangeDestroySelectionLimiter.clampRoundPositions("));
        assertFalse(controller.contains("RangeDestroySelectionLimiter.clampPositions("));
        assertFalse(controller.contains("new ShapeGenerationPlanCache.Request("),
                "顶层控制器不应重新内联形状计划");
        assertFalse(controller.contains("private static AxisBounds clampAxisAroundAnchor("));
        assertFalse(controller.contains("private static RtsCullingBox clampBoxToClientCapsAroundAnchor("));
        assertFalse(controller.contains("private static List<BlockPos> clampRangeDestroyPositionsToClientCaps("));
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
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}

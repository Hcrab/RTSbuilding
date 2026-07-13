package com.rtsbuilding.rtsbuilding.client.screen.handler;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
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

        ShapeBuildTypes.Input clamped = ScreenShapeController.clampRangeDestroyShapeInputForCaps(input, 12, 12, 12);
        List<BlockPos> positions = ShapeGeometryUtil.buildShapePositions(clamped, ShapeFillMode.FILL);
        List<BlockPos> capped = ScreenShapeController.clampRoundRangeDestroyPositionsForCaps(
                clamped, positions, 12, 12, 12, 1728);

        assertTrue(capped.contains(new BlockPos(6, 64, 0)));
        assertTrue(capped.contains(new BlockPos(-6, 64, 0)));
        assertTrue(capped.contains(new BlockPos(0, 64, 6)));
        assertTrue(capped.contains(new BlockPos(0, 64, -6)));
        assertFalse(capped.contains(new BlockPos(7, 64, 0)));
        assertFalse(capped.contains(new BlockPos(6, 64, 6)));
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

        ShapeBuildTypes.Input clamped = ScreenShapeController.clampRangeDestroyShapeInputForCaps(input, 12, 12, 12);
        List<BlockPos> positions = ShapeGeometryUtil.buildShapePositions(clamped, ShapeFillMode.FILL);
        List<BlockPos> capped = ScreenShapeController.clampRoundRangeDestroyPositionsForCaps(
                clamped, positions, 12, 12, 12, 1728);

        assertTrue(capped.contains(new BlockPos(6, 64, 0)));
        assertTrue(capped.contains(new BlockPos(-6, 64, 0)));
        assertTrue(capped.contains(new BlockPos(0, 75, 0)));
        assertFalse(capped.contains(new BlockPos(7, 64, 0)));
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

        ShapeBuildTypes.Input clamped = ScreenShapeController.clampRangeDestroyShapeInputForCaps(
                input, 32, 32, 32);
        List<BlockPos> positions = ShapeGeometryUtil.buildShapePositions(clamped, ShapeFillMode.FILL);

        assertTrue(positions.size() <= 32 * 32 * 32,
                "预览生成前就应把超大长宽高限到单次批量上限内");
        assertFalse(positions.contains(new BlockPos(32, 0, 0)));
        assertFalse(positions.contains(new BlockPos(0, 32, 0)));
        assertFalse(positions.contains(new BlockPos(0, 0, 32)));
    }

    @Test
    void advancedRoundShapesStartFromCenteredOrdinaryPreview() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String initialBox = methodBody(source, "private RtsCullingBox initialAdvancedShapeBox");
        String readySession = methodBody(source, "private ShapeBuildTypes.Session readySession");

        assertTrue(initialBox.contains("case CIRCLE -> centeredPlaneBox(center, planeRadius(center, pointB, session.planeFace()), session.planeFace(), 0)"),
                "advanced circle should begin as a centered normal circle envelope");
        assertTrue(initialBox.contains("case CYLINDER -> centeredPlaneBox(center, planeRadius(center, pointB, session.planeFace()),"),
                "advanced cylinder should begin from the same centered circular base");
        assertTrue(initialBox.contains("case BALL -> centeredBox(center, spatialRadius(center, pointB))"),
                "advanced ball should begin as a centered sphere envelope");
        assertTrue(readySession.contains("initialAdvancedShapeBox(ready)"),
                "ready advanced sessions should use the shape-aware initial box instead of the raw diagonal");
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

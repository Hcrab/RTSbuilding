package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 形状 UI 文案必须只由输入快照决定，不依赖屏幕初始化、当前语言或世界状态。
 */
class ShapeSelectionTextPresenterTest {
    @Test
    void fillShapeAndDimensionLabelsUseStableSemanticKeys() {
        assertEquals(
                "screen.rtsbuilding.fill.fill",
                ShapeSelectionTextPresenter.fillModeLabel(null, ShapeSelectionTextPresenterTest::translate));
        assertEquals(
                "screen.rtsbuilding.fill.hollow",
                ShapeSelectionTextPresenter.fillModeLabel(
                        ShapeFillMode.HOLLOW,
                        ShapeSelectionTextPresenterTest::translate));
        assertEquals(
                "screen.rtsbuilding.shape.block",
                ShapeSelectionTextPresenter.shapeLabel(null, ShapeSelectionTextPresenterTest::translate));
        assertEquals(
                "screen.rtsbuilding.shape.ball",
                ShapeSelectionTextPresenter.shapeLabel(
                        BuildShape.BALL,
                        ShapeSelectionTextPresenterTest::translate));

        assertEquals("1D", ShapeSelectionTextPresenter.dimensionLabel(BuildShape.LINE));
        assertEquals("2D", ShapeSelectionTextPresenter.dimensionLabel(BuildShape.CIRCLE));
        assertEquals("3D", ShapeSelectionTextPresenter.dimensionLabel(BuildShape.CYLINDER));
        assertEquals("3D", ShapeSelectionTextPresenter.dimensionLabel(BuildShape.BALL));
        assertEquals("3D", ShapeSelectionTextPresenter.dimensionLabel(BuildShape.BOX));
        assertEquals("2D", ShapeSelectionTextPresenter.dimensionLabel(null));
    }

    @Test
    void sizeTextUsesInclusiveBoundsAndHandlesSparseOrMissingPositions() {
        assertEquals(
                "1*1*1",
                ShapeSelectionTextPresenter.sizeText(BuildShape.BLOCK, List.of()));
        assertEquals(
                "0*0*0",
                ShapeSelectionTextPresenter.sizeText(BuildShape.WALL, null));
        assertEquals(
                "5*3*3",
                ShapeSelectionTextPresenter.sizeText(
                        BuildShape.BOX,
                        java.util.Arrays.asList(
                                new BlockPos(-2, 5, 7),
                                null,
                                new BlockPos(2, 7, 9),
                                new BlockPos(-2, 5, 7))));
        assertEquals(
                "4294967296*1*1",
                ShapeSelectionTextPresenter.sizeText(
                        BuildShape.LINE,
                        List.of(
                                new BlockPos(Integer.MIN_VALUE, 0, 0),
                                new BlockPos(Integer.MAX_VALUE, 0, 0))));
        assertEquals("0", ShapeSelectionTextPresenter.countText(-4));
        assertEquals("12", ShapeSelectionTextPresenter.countText(12));
    }

    @Test
    void closedAndChainStatesAreResolvedBeforeConfirmationKeyLookup() {
        AtomicBoolean keyRead = new AtomicBoolean();

        assertEquals(
                "",
                pending(
                        new ShapeSelectionTextPresenter.Status(
                                false,
                                BuildShape.WALL,
                                false,
                                false,
                                readySession(BuildShape.WALL)),
                        () -> {
                            keyRead.set(true);
                            return "K";
                        }));
        assertFalse(keyRead.get());

        assertEquals(
                "screen.rtsbuilding.shape_status.destroy_chain",
                pending(
                        new ShapeSelectionTextPresenter.Status(
                                true,
                                BuildShape.BLOCK,
                                true,
                                true,
                                null),
                        () -> {
                            keyRead.set(true);
                            return "K";
                        }));
        assertFalse(keyRead.get());
    }

    @Test
    void blockAndFirstPointStatesUseModeSpecificKeys() {
        assertEquals(
                "screen.rtsbuilding.shape_status.place",
                pending(status(BuildShape.BLOCK, false, null), () -> "RMB"));
        assertEquals(
                "screen.rtsbuilding.shape_status.destroy",
                pending(status(BuildShape.BLOCK, true, null), () -> "LMB"));
        assertEquals(
                "screen.rtsbuilding.shape_status.step_a",
                pending(status(BuildShape.WALL, false, null), () -> "RMB"));
        assertEquals(
                "screen.rtsbuilding.shape_status.destroy_step_a",
                pending(
                        status(
                                BuildShape.WALL,
                                true,
                                session(
                                        BuildShape.LINE,
                                        ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                                        new BlockPos(1, 2, 3))),
                        () -> "LMB"));
    }

    @Test
    void pointAndHeightStatesCarryOnlyTheirRequiredArguments() {
        assertEquals(
                "screen.rtsbuilding.shape_status.step_b|4|5|6",
                pending(
                        status(
                                BuildShape.WALL,
                                false,
                                session(
                                        BuildShape.WALL,
                                        ShapeBuildTypes.Phase.NEED_SECOND_POINT,
                                        new BlockPos(4, 5, 6))),
                        () -> "RMB"));
        assertEquals(
                "screen.rtsbuilding.shape_status.destroy_step_height",
                pending(
                        status(
                                BuildShape.BOX,
                                true,
                                session(
                                        BuildShape.BOX,
                                        ShapeBuildTypes.Phase.NEED_THIRD_POINT,
                                        new BlockPos(1, 2, 3))),
                        () -> "LMB"));
    }

    @Test
    void confirmationTextDistinguishesWallCylinderAndGenericShapes() {
        assertEquals(
                "screen.rtsbuilding.shape_status.confirm_wall|RMB",
                pending(status(BuildShape.WALL, false, readySession(BuildShape.WALL)), () -> "RMB"));
        assertEquals(
                "screen.rtsbuilding.shape_status.destroy_confirm_cylinder|K",
                pending(status(BuildShape.CYLINDER, true, readySession(BuildShape.CYLINDER)), () -> "K"));
        assertEquals(
                "screen.rtsbuilding.shape_status.confirm|ENTER",
                pending(status(BuildShape.BALL, false, readySession(BuildShape.BALL)), () -> "ENTER"));
    }

    private static ShapeSelectionTextPresenter.Status status(
            BuildShape shape,
            boolean destroyMode,
            ShapeBuildTypes.Session session) {
        return new ShapeSelectionTextPresenter.Status(true, shape, destroyMode, false, session);
    }

    private static ShapeBuildTypes.Session readySession(BuildShape shape) {
        return session(shape, ShapeBuildTypes.Phase.READY_CONFIRM, new BlockPos(1, 2, 3));
    }

    private static ShapeBuildTypes.Session session(
            BuildShape shape,
            ShapeBuildTypes.Phase phase,
            BlockPos pointA) {
        return new ShapeBuildTypes.Session(
                shape,
                Direction.UP,
                Direction.UP,
                pointA,
                new BlockPos(4, 5, 6),
                phase,
                0,
                0.0D);
    }

    private static String pending(
            ShapeSelectionTextPresenter.Status status,
            java.util.function.Supplier<String> confirmKey) {
        return ShapeSelectionTextPresenter.pendingStatusText(
                status,
                confirmKey,
                ShapeSelectionTextPresenterTest::translate);
    }

    private static String translate(String key, Object... args) {
        if (args == null || args.length == 0) {
            return key;
        }
        return key + "|" + String.join(
                "|",
                java.util.Arrays.stream(args).map(String::valueOf).toList());
    }
}

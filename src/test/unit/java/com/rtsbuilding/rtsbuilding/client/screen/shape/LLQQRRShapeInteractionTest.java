package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** 验证真实会话和预览入口；仅替换屏幕、配置和世界端口，不启动游戏或改变施工路径。 */
class LLQQRRShapeInteractionTest {
    private static final List<BuildShape> HEIGHT_SHAPES = List.of(
            BuildShape.BOX, BuildShape.CYLINDER, BuildShape.WALL);

    @Test
    void classicReadyConfirmationKeepsRepeatedHeightScrollAndConfigurationBounds() {
        try (var config = mockStatic(Config.class)) {
            config.when(Config::maxShapeDimension).thenReturn(32);
            for (BuildShape shape : HEIGHT_SHAPES) {
                ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.READY_CONFIRM, false);
                ShapeBuildTypes.Session before = owner.current();
                assertTrue(owner.adjustHeight(5), shape.name());
                assertTrue(owner.adjustDimension(-2, false, true), shape.name());
                assertEquals(3, owner.current().boxHeightOffset(), shape.name());
                assertTrue(owner.adjustHeight(100));
                assertEquals(31, owner.current().boxHeightOffset());
                assertTrue(owner.adjustHeight(-100));
                assertEquals(-31, owner.current().boxHeightOffset());
                assertEquals(before.phase(), owner.current().phase());
                assertEquals(before.pointA(), owner.current().pointA());
                assertEquals(before.pointB(), owner.current().pointB());
            }
        }
    }

    @Test
    void readyHeightKeepsSaturatingAdditionAtIntegerExtremes() {
        try (var config = mockStatic(Config.class)) {
            config.when(Config::maxShapeDimension).thenReturn(Integer.MAX_VALUE);
            for (BuildShape shape : HEIGHT_SHAPES) {
                ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.READY_CONFIRM, false);
                owner.adjustHeight(Integer.MAX_VALUE);
                owner.adjustHeight(10);
                assertEquals(Integer.MAX_VALUE - 1, owner.current().boxHeightOffset());
                owner.adjustHeight(Integer.MIN_VALUE);
                owner.adjustHeight(Integer.MIN_VALUE);
                owner.adjustHeight(-10);
                assertEquals(-(Integer.MAX_VALUE - 1), owner.current().boxHeightOffset());
            }
        }
    }

    @Test
    void advancedReadyConfirmationLeavesHeightToHandles() {
        for (BuildShape shape : HEIGHT_SHAPES) {
            ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.READY_CONFIRM, true);
            ShapeBuildTypes.Session before = owner.current();
            assertFalse(owner.adjustHeight(1), shape.name());
            assertFalse(owner.adjustDimension(-1, false, true), shape.name());
            assertSame(before, owner.current());
        }
    }

    @Test
    void firstAnchorCannotAdjustHeightButClassicHeightSelectionStillCan() {
        try (var config = mockStatic(Config.class)) {
            config.when(Config::maxShapeDimension).thenReturn(32);
            for (BuildShape shape : HEIGHT_SHAPES) {
                for (boolean advanced : new boolean[]{false, true}) {
                    ShapeSelectionSession early = session(shape, ShapeBuildTypes.Phase.NEED_SECOND_POINT, advanced);
                    ShapeBuildTypes.Session before = early.current();
                    assertFalse(early.adjustHeight(1), shape.name());
                    assertSame(before, early.current());
                }
                ShapeSelectionSession classic = session(shape, ShapeBuildTypes.Phase.NEED_THIRD_POINT, false);
                assertTrue(classic.adjustHeight(2), shape.name());
                assertEquals(2, classic.current().boxHeightOffset());
            }
        }
    }

    @Test
    void rotateSuppressesBuildPreviewAndReturningToInteractRestoresIt() {
        PreviewFixture fixture = new PreviewFixture();
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(BuildShape.BOX, Direction.UP,
                Direction.UP, BlockPos.ZERO, new BlockPos(1, 0, 1), 0, false);
        List<BlockPos> blocks = List.of(BlockPos.ZERO);
        when(fixture.controller.getBuildShape()).thenReturn(BuildShape.BOX);
        when(fixture.controller.hasSelectedItem()).thenReturn(true);
        when(fixture.runtime.resolveInput(null, false)).thenReturn(input);
        when(fixture.runtime.generationPlan(input)).thenReturn(
                new ShapeGenerationResult(ShapeGenerationStatus.READY, blocks, 1));
        when(fixture.runtime.filterPlacementTargets(input, blocks)).thenReturn(blocks);

        when(fixture.controller.getMode()).thenReturn(BuilderMode.INTERACT);
        assertEquals(blocks, fixture.provider.snapshot().blocks());
        when(fixture.controller.getMode()).thenReturn(BuilderMode.ROTATE);
        assertSame(ShapeDataRecords.GhostPreview.EMPTY, fixture.provider.snapshot());
        when(fixture.controller.getMode()).thenReturn(BuilderMode.INTERACT);
        assertEquals(blocks, fixture.provider.snapshot().blocks());
    }

    @Test
    void modeGateAlsoSuppressesSmartFillAndDestroyPreviews() {
        for (boolean smartFill : new boolean[]{false, true}) {
            PreviewFixture fixture = new PreviewFixture();
            ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                    List.of(BlockPos.ZERO), true, !smartFill, List.of());
            when(fixture.screen.isQuickBuildSmartFillMode()).thenReturn(smartFill);
            when(fixture.screen.getSmartFillGhostPreview()).thenReturn(preview);
            when(fixture.screen.isQuickBuildRangeDestroyMode()).thenReturn(!smartFill);
            when(fixture.screen.isQuickBuildConvenienceDestroyMode()).thenReturn(!smartFill);
            when(fixture.screen.getConvenienceDestroyGhostPreview()).thenReturn(preview);
            when(fixture.controller.getMode()).thenReturn(BuilderMode.INTERACT);
            assertSame(preview, fixture.provider.snapshot());
            for (BuilderMode mode : BuilderMode.values()) {
                if (mode == BuilderMode.INTERACT) continue;
                when(fixture.controller.getMode()).thenReturn(mode);
                assertSame(ShapeDataRecords.GhostPreview.EMPTY, fixture.provider.snapshot(), mode.name());
            }
        }
    }

    private static ShapeSelectionSession session(BuildShape shape, ShapeBuildTypes.Phase phase, boolean advanced) {
        BuilderScreen screen = mock(BuilderScreen.class);
        when(screen.isAdvancedShapeMode()).thenReturn(advanced);
        ShapeSelectionSession owner = new ShapeSelectionSession();
        ShapeSelectionBoxController boxes = new ShapeSelectionBoxController();
        boxes.init(screen, owner::current, owner::replace);
        owner.init(screen, boxes);
        owner.replace(new ShapeBuildTypes.Session(shape, Direction.UP, Direction.UP,
                BlockPos.ZERO, phase == ShapeBuildTypes.Phase.NEED_SECOND_POINT ? null : new BlockPos(1, 0, 1),
                phase, 0, 12.0D));
        return owner;
    }

    /** 预览 provider 保持真实分派，屏幕和运行时仅提供各分支已准备好的输入。 */
    private static final class PreviewFixture {
        final BuilderScreen screen = mock(BuilderScreen.class);
        final ClientRtsController controller = mock(ClientRtsController.class);
        final ShapeGhostPreviewProvider.Runtime runtime = mock(ShapeGhostPreviewProvider.Runtime.class);
        final ShapeGhostPreviewProvider provider = new ShapeGhostPreviewProvider();

        PreviewFixture() {
            provider.init(screen, controller, new ConfirmedDestroyPreviewState(), runtime);
        }
    }
}

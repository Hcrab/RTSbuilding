package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.test.ShapeConfigFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证玩家确认选区后仍能调高，并且旋转工具不会遗留建造或破坏虚影。 */
class LLQQRRShapeInteractionTest {
    @ParameterizedTest
    @EnumSource(value = BuildShape.class, names = {"BOX", "CYLINDER", "WALL"})
    @ExtendWith(ShapeConfigFixture.class)
    void classicReadySelectionStillAcceptsHeightWheel(BuildShape shape) {
        ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.READY_CONFIRM, false);
        ShapeBuildTypes.Session before = owner.current();

        assertTrue(owner.adjustDimension(3, false, true));
        assertEquals(7, owner.current().boxHeightOffset());
        assertTrue(owner.adjustHeight(-2));
        assertEquals(5, owner.current().boxHeightOffset());
        assertEquals(before.pointA(), owner.current().pointA());
        assertEquals(before.pointB(), owner.current().pointB());
        assertEquals(before.phase(), owner.current().phase());
        assertEquals(before.boxHeightMouseBaseY(), owner.current().boxHeightMouseBaseY());
    }

    @ParameterizedTest
    @EnumSource(value = BuildShape.class, names = {"BOX", "CYLINDER", "WALL"})
    void advancedReadySelectionKeepsHeightOwnedByHandles(BuildShape shape) {
        ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.READY_CONFIRM, true);
        ShapeBuildTypes.Session before = owner.current();

        assertFalse(owner.adjustHeight(1));
        assertFalse(owner.adjustDimension(-1, false, true));
        assertSame(before, owner.current());
    }

    @ParameterizedTest
    @EnumSource(value = BuildShape.class, names = {"BOX", "CYLINDER", "WALL"})
    void firstPointDoesNotPermitPrematureHeightChanges(BuildShape shape) {
        for (boolean advanced : new boolean[]{false, true}) {
            ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.NEED_SECOND_POINT, advanced);
            ShapeBuildTypes.Session before = owner.current();
            assertFalse(owner.adjustDimension(1, false, true));
            assertSame(before, owner.current());
        }
    }

    @ParameterizedTest
    @EnumSource(value = BuildShape.class, names = {"BOX", "CYLINDER", "WALL"})
    @ExtendWith(ShapeConfigFixture.class)
    void thirdPointHeightSelectionRetainsExistingWheelBehavior(BuildShape shape) {
        ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.NEED_THIRD_POINT, false);
        assertTrue(owner.adjustHeight(2));
        assertEquals(6, owner.current().boxHeightOffset());
        assertEquals(ShapeBuildTypes.Phase.NEED_THIRD_POINT, owner.current().phase());
    }

    @ParameterizedTest
    @EnumSource(value = BuildShape.class, names = {"BOX", "CYLINDER", "WALL"})
    void readyHeightAdditionSaturatesWithoutWrapping(BuildShape shape) {
        try (MockedStatic<Config> config = mockStatic(Config.class)) {
            config.when(Config::maxShapeDimension).thenReturn(Integer.MAX_VALUE);
            ShapeSelectionSession owner = session(shape, ShapeBuildTypes.Phase.READY_CONFIRM, false);
            assertTrue(owner.adjustHeight(Integer.MAX_VALUE));
            assertEquals(Integer.MAX_VALUE - 1, owner.current().boxHeightOffset());
            assertTrue(owner.adjustHeight(100));
            assertEquals(Integer.MAX_VALUE - 1, owner.current().boxHeightOffset());
            assertTrue(owner.adjustHeight(Integer.MIN_VALUE));
            assertEquals(-2, owner.current().boxHeightOffset());
            assertTrue(owner.adjustHeight(Integer.MIN_VALUE));
            assertEquals(-(Integer.MAX_VALUE - 1), owner.current().boxHeightOffset());
            assertTrue(owner.adjustHeight(-100));
            assertEquals(-(Integer.MAX_VALUE - 1), owner.current().boxHeightOffset());
        }
    }

    @ParameterizedTest
    @EnumSource(value = BuilderMode.class, names = "INTERACT", mode = EnumSource.Mode.EXCLUDE)
    void otherToolsSuppressEveryGhostSourceBeforeQueryingScreen(BuilderMode mode) {
        BuilderScreen screen = mock(BuilderScreen.class);
        ClientRtsController controller = mock(ClientRtsController.class);
        ShapeGhostPreviewProvider.Runtime runtime = mock(ShapeGhostPreviewProvider.Runtime.class);
        ConfirmedDestroyPreviewState confirmed = mock(ConfirmedDestroyPreviewState.class);
        when(controller.getMode()).thenReturn(mode);
        ShapeGhostPreviewProvider provider = new ShapeGhostPreviewProvider();
        provider.init(screen, controller, confirmed, runtime);

        assertSame(ShapeDataRecords.GhostPreview.EMPTY, provider.snapshot());
        verifyNoInteractions(screen, runtime, confirmed);
    }

    @Test
    void switchingBackToInteractRestoresSmartFillPreview() {
        BuilderScreen screen = mock(BuilderScreen.class);
        ClientRtsController controller = mock(ClientRtsController.class);
        ShapeGhostPreviewProvider.Runtime runtime = mock(ShapeGhostPreviewProvider.Runtime.class);
        ShapeDataRecords.GhostPreview expected = new ShapeDataRecords.GhostPreview(
                List.of(new BlockPos(10, 64, 12)), true);
        when(controller.getMode()).thenReturn(BuilderMode.ROTATE, BuilderMode.INTERACT);
        when(screen.isQuickBuildSmartFillMode()).thenReturn(true);
        when(screen.getSmartFillGhostPreview()).thenReturn(expected);
        ShapeGhostPreviewProvider provider = new ShapeGhostPreviewProvider();
        provider.init(screen, controller, mock(ConfirmedDestroyPreviewState.class), runtime);

        assertSame(ShapeDataRecords.GhostPreview.EMPTY, provider.snapshot());
        assertSame(expected, provider.snapshot());
        verify(screen, times(1)).getSmartFillGhostPreview();
        verifyNoInteractions(runtime);
    }

    @Test
    void interactRetainsConvenienceDestroyPreview() {
        BuilderScreen screen = mock(BuilderScreen.class);
        ClientRtsController controller = mock(ClientRtsController.class);
        ShapeGhostPreviewProvider.Runtime runtime = mock(ShapeGhostPreviewProvider.Runtime.class);
        ShapeDataRecords.GhostPreview expected = new ShapeDataRecords.GhostPreview(
                List.of(new BlockPos(10, 64, 12)), true, true);
        when(controller.getMode()).thenReturn(BuilderMode.INTERACT);
        when(screen.isQuickBuildRangeDestroyMode()).thenReturn(true);
        when(screen.isQuickBuildConvenienceDestroyMode()).thenReturn(true);
        when(screen.getConvenienceDestroyGhostPreview()).thenReturn(expected);
        ShapeGhostPreviewProvider provider = new ShapeGhostPreviewProvider();
        provider.init(screen, controller, mock(ConfirmedDestroyPreviewState.class), runtime);

        assertSame(expected, provider.snapshot());
        verifyNoInteractions(runtime);
    }

    private static ShapeSelectionSession session(BuildShape shape, ShapeBuildTypes.Phase phase, boolean advanced) {
        ShapeSelectionBoxController boxes = mock(ShapeSelectionBoxController.class);
        when(boxes.isAdvanced(shape)).thenReturn(advanced);
        ShapeSelectionSession owner = new ShapeSelectionSession();
        owner.init(null, boxes);
        owner.replace(new ShapeBuildTypes.Session(
                shape, Direction.UP, Direction.UP, new BlockPos(12, 64, 18),
                phase == ShapeBuildTypes.Phase.NEED_SECOND_POINT ? null : new BlockPos(16, 64, 22),
                phase, 4, 120.5D));
        return owner;
    }
}

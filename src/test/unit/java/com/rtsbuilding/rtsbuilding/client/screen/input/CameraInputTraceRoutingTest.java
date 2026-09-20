package com.rtsbuilding.rtsbuilding.client.screen.input;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * 真实执行 CameraInputHandler 的普通挖掘边界，锁定输入来源与停止来源不会在 handler 层丢失。
 */
class CameraInputTraceRoutingTest {
    private MockedStatic<Config> config;

    @BeforeEach
    void provideLoadedBlueprintSetting() {
        // 纯测试进程没有 loader 配置；只补正常世界的启用值，捕获状态和 handler 仍走真实实现。
        config = mockStatic(Config.class);
        config.when(Config::areBlueprintsEnabled).thenReturn(true);
    }

    @AfterEach
    void restoreConfigurationReads() {
        config.close();
    }

    @Test
    void mouseStartCarriesMouseKindAndPointerReleaseStopsOnce() {
        Fixture fixture = new Fixture();
        CameraInputHandler handler = fixture.handler();

        assertTrue(handler.startMiningAt(10.0D, 20.0D, 0, false));
        verify(fixture.controller).startMining(
                fixture.hit.getBlockPos(), fixture.hit.getDirection().get3DDataValue(),
                fixture.toolSlot, RtsTraceInputKind.MOUSE);
        assertTrue(handler.isLeftMiningActive());
        assertFalse(handler.isKeyboardMining());
        assertTrue(handler.getActiveMiningMouseButton() == 0);

        handler.stopActiveMining(RtsMiningStopOrigin.POINTER_RELEASE);
        handler.stopActiveMining(RtsMiningStopOrigin.POINTER_RELEASE);

        verify(fixture.controller, times(1)).abortMining(
                fixture.toolSlot, RtsMiningStopOrigin.POINTER_RELEASE);
        assertFalse(handler.isLeftMiningActive());
        assertFalse(handler.isKeyboardMining());
        assertTrue(handler.getActiveMiningMouseButton() == -1);
    }

    @Test
    void keyboardStartCarriesKeyboardKindAndKeyReleaseStopsOnce() {
        Fixture fixture = new Fixture();
        CameraInputHandler handler = fixture.handler();

        assertTrue(handler.startMiningAt(10.0D, 20.0D, -1, true));
        verify(fixture.controller).startMining(
                fixture.hit.getBlockPos(), fixture.hit.getDirection().get3DDataValue(),
                fixture.toolSlot, RtsTraceInputKind.KEYBOARD);
        assertTrue(handler.isLeftMiningActive());
        assertTrue(handler.isKeyboardMining());
        assertTrue(handler.getActiveMiningMouseButton() == -1);

        handler.stopActiveMining(RtsMiningStopOrigin.KEY_RELEASE);
        handler.stopActiveMining(RtsMiningStopOrigin.KEY_RELEASE);

        verify(fixture.controller, times(1)).abortMining(
                fixture.toolSlot, RtsMiningStopOrigin.KEY_RELEASE);
        assertFalse(handler.isLeftMiningActive());
        assertFalse(handler.isKeyboardMining());
        assertTrue(handler.getActiveMiningMouseButton() == -1);
    }

    private static final class Fixture {
        final ClientRtsController controller = mock(ClientRtsController.class);
        final BuilderScreen screen = mock(BuilderScreen.class);
        final ScreenShapeController shapeController = mock(ScreenShapeController.class);
        final BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(BlockPos.ZERO), Direction.UP, BlockPos.ZERO, false);
        final int toolSlot = 4;

        Fixture() {
            when(controller.getMode()).thenReturn(BuilderMode.INTERACT);
            when(screen.getPendingGuiBindSlot()).thenReturn(-1);
            when(screen.isWorldArea(anyDouble(), anyDouble())).thenReturn(true);
            when(screen.isQuickBuildRangeDestroyMode()).thenReturn(false);
            when(screen.getShapeController()).thenReturn(shapeController);
            when(shapeController.hasConfirmedDestroyWorkArea()).thenReturn(false);
            when(screen.pickInteractionTarget(false)).thenReturn(null);
            when(screen.pickBlockHit()).thenReturn(hit);
            when(screen.getSelectedToolSlot()).thenReturn(toolSlot);
        }

        CameraInputHandler handler() {
            CameraInputHandler handler = new CameraInputHandler();
            handler.init(screen, controller);
            return handler;
        }
    }
}

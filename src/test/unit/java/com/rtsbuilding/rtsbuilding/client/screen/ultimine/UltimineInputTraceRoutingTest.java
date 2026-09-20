package com.rtsbuilding.rtsbuilding.client.screen.ultimine;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 锁定 Ultimine UI 适配器经 reducer 后仍把真实输入来源交给控制器。 */
class UltimineInputTraceRoutingTest {
    @Test
    void confirmPreviewCarriesMouseKindAndOriginalMiningArguments() {
        ClientRtsController controller = mock(ClientRtsController.class);
        BuilderScreen screen = mock(BuilderScreen.class);
        ScreenShapeController shapeController = mock(ScreenShapeController.class);
        BlockPos anchor = new BlockPos(3, 4, 5);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(anchor), Direction.NORTH, anchor, false);
        List<BlockPos> preview = List.of(anchor, anchor.above());

        when(screen.uiController()).thenReturn(controller);
        when(screen.isQuickBuildRangeDestroyChainMode()).thenReturn(true);
        when(screen.getUltimineLimit()).thenReturn(48);
        when(screen.getSelectedToolSlot()).thenReturn(6);
        when(screen.getShapeController()).thenReturn(shapeController);

        try (var config = mockStatic(Config.class)) {
            config.when(Config::ultimineMaxBlocks).thenReturn(128);
            assertTrue(UltimineUiAdapter.confirmPreview(
                    screen, hit, preview, RtsTraceInputKind.MOUSE));
        }

        verify(shapeController).rememberConfirmedChainDestroyPreview(preview);
        verify(shapeController).recordPendingBreakForUndo(preview, Direction.NORTH, 6);
        verify(controller).startUltimine(
                anchor, Direction.NORTH.get3DDataValue(), 6, 48, (byte) 0, RtsTraceInputKind.MOUSE);
    }
}

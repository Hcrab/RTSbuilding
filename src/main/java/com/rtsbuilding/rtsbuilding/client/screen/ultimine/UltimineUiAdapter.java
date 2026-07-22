package com.rtsbuilding.rtsbuilding.client.screen.ultimine;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiAction;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiPhase;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiState;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.ULTIMINE_MAX_LIMIT;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.ULTIMINE_MIN_LIMIT;

/**
 * 1.21.1 连锁破坏会话适配器。
 *
 * <p>Core 只决定当前预览能否确认以及会话应进入哪个阶段；本类保留真实方块坐标、
 * 工具槽、撤回记录和网络发包。它不绘制独立 Ultimine 窗口，因为当前产品入口已经是
 * Quick Build → 范围破坏 → 连锁。</p>
 */
public final class UltimineUiAdapter {
    private UltimineUiAdapter() {}

    public static UltimineUiState snapshot(BuilderScreen screen, int previewBlocks) {
        ClientRtsController controller = screen.uiController();
        int processed = controller.getUltimineProgressProcessed();
        int total = controller.getUltimineProgressTotal();
        boolean enabled = screen.isQuickBuildRangeDestroyChainMode();
        UltimineUiPhase phase = processed >= 0 && total > 0 && processed < total
                ? UltimineUiPhase.RUNNING
                : previewBlocks > 0 ? UltimineUiPhase.PREVIEW : UltimineUiPhase.IDLE;
        return new UltimineUiState(enabled, enabled ? "" : "chain_not_active", phase,
                previewBlocks > 0, previewBlocks, 0, screen.getUltimineLimit(),
                ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT, processed, total);
    }

    /** 通过 Core 确认绿色连锁预览，再执行原有撤回记录与服务端请求。 */
    public static boolean confirmPreview(BuilderScreen screen, BlockHitResult hit,
            List<BlockPos> preview) {
        if (screen == null || hit == null || preview == null || preview.isEmpty()) {
            return false;
        }
        UltimineUiTransition transition = UltimineUiReducer.apply(
                snapshot(screen, preview.size()), UltimineUiAction.confirmPreview());
        if (transition.command != UltimineUiTransition.Command.START_CHAIN) {
            return false;
        }
        screen.getShapeController().rememberConfirmedChainDestroyPreview(preview);
        screen.getShapeController().recordPendingBreakForUndo(
                preview, hit.getDirection(), screen.getSelectedToolSlot());
        screen.uiController().startUltimine(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                screen.getSelectedToolSlot(), transition.state.limit, (byte) 0);
        return true;
    }
}

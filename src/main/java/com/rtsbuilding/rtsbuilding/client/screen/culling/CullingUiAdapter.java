package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiAction;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiDirection;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiPhase;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiState;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiTransition;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * 1.21.1 范围剔除平台适配器。
 *
 * <p>面板和输入只读 Core 快照/命令；真实 BlockPos/AABB、射线、动画和区块重绘
 * 继续由 `RtsCullingManager` 执行，不进入 Java 8 Core。</p>
 */
public final class CullingUiAdapter {
    private CullingUiAdapter() {}

    static CullingUiState snapshot(RtsCullingManager manager) {
        RtsCullingBox selected = manager.selectedBox().orElse(null);
        return new CullingUiState(manager.isManagementMode(), true, "",
                CullingUiPhase.valueOf(manager.phase().name()), manager.boxes().size(),
                manager.selectedId(), selected == null ? 0 : selected.width(),
                selected == null ? 0 : selected.height(), selected == null ? 0 : selected.depth(),
                manager.previewHeight(), manager.hoveredId(),
                direction(manager.hoveredHandleDirection()), direction(manager.activeHandleDirection()));
    }

    static CullingUiTransition dispatch(RtsCullingManager manager, CullingUiAction action) {
        CullingUiTransition transition = CullingUiReducer.apply(snapshot(manager), action);
        switch (transition.command) {
            case DELETE_SELECTED -> manager.deleteSelected();
            case CONFIRM_DRAFT -> manager.confirmDraft();
            case CANCEL_DRAFT -> manager.cancelDraftIfActive();
            case CLOSE -> manager.closeManagementMode();
            case ADJUST_HEIGHT -> manager.handleScroll(action.value > 0 ? 1.0D : -1.0D,
                    Math.abs(action.value) > 1);
            case RESIZE_HANDLE -> manager.adjustSelectedFromHandle(
                    Direction.valueOf(action.direction.name()), action.value);
            default -> { }
        }
        return transition;
    }

    static boolean worldPrimary(RtsCullingManager manager, BlockHitResult hit,
            Vec3 origin, Vec3 direction) {
        CullingUiTransition transition = CullingUiReducer.apply(snapshot(manager),
                CullingUiAction.simple(CullingUiAction.Type.WORLD_PRIMARY));
        return transition.command == CullingUiTransition.Command.WORLD_PRIMARY
                && manager.handleWorldAction(hit, origin, direction);
    }

    static boolean handleKey(RtsCullingManager manager, int keyCode) {
        CullingUiState state = snapshot(manager);
        if (!state.open) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            dispatch(manager, CullingUiAction.simple(CullingUiAction.Type.CONFIRM_DRAFT));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            dispatch(manager, CullingUiAction.simple(CullingUiAction.Type.DELETE_SELECTED));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (state.activeHandle != null) return manager.handleKey(keyCode, 0, 0);
            dispatch(manager, CullingUiAction.simple(state.phase == CullingUiPhase.IDLE
                    ? CullingUiAction.Type.CLOSE : CullingUiAction.Type.CANCEL_DRAFT));
            return true;
        }
        return false;
    }

    /** 把草稿高度或悬停/激活手柄滚轮统一翻译成 Core 命令。 */
    public static boolean handleScroll(RtsCullingManager manager, double scrollY, boolean fast) {
        CullingUiState state = snapshot(manager);
        if (!state.open || scrollY == 0.0D) return false;
        int delta = scrollY > 0.0D ? 1 : -1;
        if (fast) delta *= 4;
        if (state.phase != CullingUiPhase.IDLE) {
            return dispatch(manager, CullingUiAction.height(delta)).command
                    == CullingUiTransition.Command.ADJUST_HEIGHT;
        }
        CullingUiDirection direction = state.activeHandle != null
                ? state.activeHandle : state.hoveredHandle;
        return direction != null && dispatch(manager,
                CullingUiAction.handle(direction, delta)).command
                == CullingUiTransition.Command.RESIZE_HANDLE;
    }

    private static CullingUiDirection direction(Direction direction) {
        return direction == null ? null : CullingUiDirection.valueOf(direction.name());
    }
}

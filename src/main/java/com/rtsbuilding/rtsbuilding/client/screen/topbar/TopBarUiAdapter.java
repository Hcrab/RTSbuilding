package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.developer.RtsDeveloperTaskScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButton;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiTransition;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶栏 Core 状态与 Minecraft 控制器副作用之间的唯一生产边界。
 * 绘制和离屏回放只消费快照；模式切换、持久化和打开窗口仍留在生产端执行。
 */
final class TopBarUiAdapter {
    private TopBarUiAdapter() {
    }

    static TopBarUiState snapshot(BuilderScreen screen, ClientRtsController controller) {
        boolean locked = screen.isBlueprintPlacementModeLocked();
        TopBarUiState.Mode mode = locked ? TopBarUiState.Mode.INTERACT : mode(controller.getMode());
        List<TopBarUiButton> buttons = new ArrayList<>();
        buttons.add(button(TopBarUiButtonId.INTERACT, true, mode == TopBarUiState.Mode.INTERACT));
        buttons.add(button(TopBarUiButtonId.LINK, true, mode == TopBarUiState.Mode.LINK_STORAGE));
        buttons.add(button(TopBarUiButtonId.FUNNEL, true, mode == TopBarUiState.Mode.FUNNEL));
        buttons.add(button(TopBarUiButtonId.ROTATE, true, mode == TopBarUiState.Mode.ROTATE));
        buttons.add(button(TopBarUiButtonId.QUICK_BUILD,
                screen.canUseQuickBuild(), screen.isQuickBuildOpen()));
        buttons.add(button(TopBarUiButtonId.QUEST_DETECT,
                isFtbQuestIntegrationLoaded(), controller.isQuestDetectPopupVisible()));
        buttons.add(button(TopBarUiButtonId.CHUNK_VIEW, true, controller.isChunkCurtainVisible()));
        buttons.add(button(TopBarUiButtonId.RANGE_CULLING,
                screen.canUseRangeCulling(), screen.isRangeCullingManagementActive()));
        buttons.add(button(TopBarUiButtonId.GUIDE, true, screen.isGuideOpen()));
        buttons.add(button(TopBarUiButtonId.DEVELOPER,
                Config.isDeveloperModeEnabled(), false));
        buttons.add(button(TopBarUiButtonId.GEAR, true, screen.isGearMenuOpen()));
        return new TopBarUiState(buttons, mode, controller.isStorageLinked(),
                controller.getLinkedStorageName(), controller.isAutoStoreMinedDrops(),
                controller.isFunnelEnabled(),
                screen.isQuickBuildOpen() ? screen.pendingShapeStatusText() : "",
                screen.getPendingGuiBindSlot(), locked);
    }

    static boolean dispatch(TopBarUiAction action, BuilderScreen screen,
                            ClientRtsController controller, int buttonCenterX,
                            int buttonBottomY) {
        TopBarUiTransition transition = TopBarUiReducer.apply(snapshot(screen, controller), action);
        switch (transition.command) {
            case FORCE_INTERACT:
                controller.setMode(BuilderMode.INTERACT);
                controller.setFunnelEnabled(false);
                return true;
            case INTERACT:
                setMode(screen, controller, BuilderMode.INTERACT);
                return true;
            case LINK:
                setMode(screen, controller, BuilderMode.LINK_STORAGE);
                return true;
            case FUNNEL:
                setMode(screen, controller, BuilderMode.FUNNEL);
                return true;
            case ROTATE:
                setMode(screen, controller, BuilderMode.ROTATE);
                return true;
            case QUICK_BUILD:
                screen.toggleQuickBuild();
                screen.persistUiState();
                return true;
            case QUEST_DETECT:
                controller.detectQuestsNow();
                return true;
            case CHUNK_VIEW:
                controller.setChunkCurtainVisible(!controller.isChunkCurtainVisible());
                screen.persistUiState();
                return true;
            case RANGE_CULLING:
                screen.toggleRangeCullingManagement();
                return true;
            case GUIDE:
                screen.toggleTopGuide(buttonCenterX, buttonBottomY);
                return true;
            case DEVELOPER:
                Minecraft.getInstance().setScreen(new RtsDeveloperTaskScreen(screen));
                return true;
            case GEAR:
                screen.toggleGearMenu();
                return true;
            case NONE:
            default:
                return false;
        }
    }

    private static void setMode(BuilderScreen screen, ClientRtsController controller,
                                BuilderMode mode) {
        controller.setMode(mode);
        controller.setFunnelEnabled(false);
        screen.clearShapeBuildSession();
    }

    private static TopBarUiButton button(TopBarUiButtonId id, boolean visible, boolean active) {
        return new TopBarUiButton(id, visible, visible && active);
    }

    private static TopBarUiState.Mode mode(BuilderMode mode) {
        return switch (mode) {
            case INTERACT -> TopBarUiState.Mode.INTERACT;
            case LINK_STORAGE -> TopBarUiState.Mode.LINK_STORAGE;
            case FUNNEL -> TopBarUiState.Mode.FUNNEL;
            case SELECT_PAN -> TopBarUiState.Mode.CAMERA;
            case ROTATE -> TopBarUiState.Mode.ROTATE;
            default -> TopBarUiState.Mode.IDLE;
        };
    }

    private static boolean isFtbQuestIntegrationLoaded() {
        return ModList.get().isLoaded("ftbquests")
                || ModList.get().isLoaded("ftb_quests")
                || ModList.get().isLoaded("ftblibrary");
    }
}

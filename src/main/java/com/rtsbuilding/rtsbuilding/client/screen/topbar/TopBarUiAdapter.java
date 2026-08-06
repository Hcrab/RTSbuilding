package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.developer.RtsDeveloperTaskScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiAction;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButton;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiCatalog;
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
        for (TopBarUiButtonId id : TopBarUiCatalog.orderedButtonIds()) {
            boolean visible = visible(id, screen);
            buttons.add(button(id, visible, active(id, mode, screen, controller)));
        }
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
        if (mode != BuilderMode.LINK_STORAGE) {
            screen.getStorageBatchSelection().deactivate(screen.getMinecraft(), false);
        }
    }

    private static TopBarUiButton button(TopBarUiButtonId id, boolean visible, boolean active) {
        return new TopBarUiButton(id, visible, visible && active);
    }

    private static boolean visible(TopBarUiButtonId id, BuilderScreen screen) {
        return switch (id) {
            case QUICK_BUILD -> canShowQuickBuildButton(id, screen);
            case QUEST_DETECT -> isFtbQuestIntegrationLoaded();
            case RANGE_CULLING -> screen.canUseRangeCulling();
            case DEVELOPER -> Config.isDeveloperModeEnabled();
            default -> true;
        };
    }

    private static boolean canShowQuickBuildButton(TopBarUiButtonId id, BuilderScreen screen) {
        // 顶栏按钮由注册目录生成，但 Quick Build 仍必须服从远程放置插件门槛。
        return id == TopBarUiButtonId.QUICK_BUILD && screen.canUseQuickBuild();
    }

    private static boolean active(TopBarUiButtonId id, TopBarUiState.Mode mode,
                                  BuilderScreen screen, ClientRtsController controller) {
        return switch (id) {
            case INTERACT -> mode == TopBarUiState.Mode.INTERACT;
            case LINK -> mode == TopBarUiState.Mode.LINK_STORAGE;
            case FUNNEL -> mode == TopBarUiState.Mode.FUNNEL;
            case ROTATE -> mode == TopBarUiState.Mode.ROTATE;
            case QUICK_BUILD -> screen.isQuickBuildOpen();
            case QUEST_DETECT -> controller.isQuestDetectPopupVisible();
            case CHUNK_VIEW -> controller.isChunkCurtainVisible();
            case RANGE_CULLING -> screen.isRangeCullingManagementActive();
            case GUIDE -> screen.isGuideOpen();
            case GEAR -> screen.isGearMenuOpen();
            case DEVELOPER -> false;
        };
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

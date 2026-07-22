package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsSectionId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiAction;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiState;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiTransition;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiValue;
import com.rtsbuilding.rtsbuilding.uikit.layout.SettingsWindowLayout;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.EnumMap;
import java.util.EnumSet;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.MAX_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.MIN_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.RTS_GUI_SCALE_STEP;

/** 设置 Core 目录与生产控制器/持久化副作用之间的唯一适配层。 */
final class GearMenuUiAdapter {
    private GearMenuUiAdapter() {
    }

    static SettingsUiState snapshot(GearMenuPanel panel, BuilderScreen screen,
                                    ClientRtsController controller, int contentX,
                                    int contentWidth) {
        EnumMap<SettingsId, SettingsUiValue> values = new EnumMap<>(SettingsId.class);
        int presets = Math.max(1, controller.getInputSensitivityPresetCount());
        values.put(SettingsId.PAN_DRAG_SENSITIVITY, value(
                controller.getPanDragSensitivityLabel(), controller.getPanDragSensitivityIndex(), presets));
        values.put(SettingsId.ROTATE_VIEW_SENSITIVITY, value(
                controller.getRotateViewSensitivityLabel(), controller.getRotateViewSensitivityIndex(), presets));
        values.put(SettingsId.KEYBOARD_MOVE_SENSITIVITY, value(
                controller.getKeyboardMoveSensitivityLabel(), controller.getKeyboardMoveSensitivityIndex(), presets));
        values.put(SettingsId.WHEEL_ZOOM_SENSITIVITY, value(
                controller.getWheelZoomSensitivityLabel(), controller.getWheelZoomSensitivityIndex(), presets));
        values.put(SettingsId.START_CAMERA_AT_HEAD, toggle(controller.isStartCameraAtPlayerHead()));
        values.put(SettingsId.INVERT_PAN_X, toggle(controller.isInvertPanDragX()));
        values.put(SettingsId.INVERT_PAN_Y, toggle(controller.isInvertPanDragY()));
        values.put(SettingsId.KEYBOARD_BATCH_CONFIRM, toggle(Config.isKeyboardBatchConfirmEnabled()));

        int scaleCount = (int) Math.round((MAX_RTS_GUI_SCALE - MIN_RTS_GUI_SCALE)
                / RTS_GUI_SCALE_STEP) + 1;
        int scaleIndex = (int) Math.round((screen.getRtsGuiScale() - MIN_RTS_GUI_SCALE)
                / RTS_GUI_SCALE_STEP);
        values.put(SettingsId.UI_SCALE, value(screen.rtsGuiScaleLabel(), scaleIndex, scaleCount));
        values.put(SettingsId.PLAYER_STATUS_OVERLAY, toggle(controller.isPlayerStatusOverlayEnabled()));
        values.put(SettingsId.CONTAINER_OVERLAY, toggle(RtsClientUiStateStore.isContainerOverlayEnabled()));
        values.put(SettingsId.SHIFT_IMPORT, toggle(RtsClientUiStateStore.isOverlayShiftImportEnabled()));
        values.put(SettingsId.SHOW_STORAGE_READY_POPUP, toggle(RtsClientUiStateStore.isShowStorageReadyPopupEnabled()));
        values.put(SettingsId.SHOW_WORKFLOW_PANEL, toggle(RtsClientUiStateStore.isShowWorkflowPanelEnabled()));
        if (isJadeLoaded()) {
            values.put(SettingsId.JADE_TRACK_MOUSE, toggle(RtsClientUiStateStore.isJadePanelTrackMouseEnabled()));
            values.put(SettingsId.JADE_HIDDEN, toggle(RtsClientUiStateStore.isJadePanelHidden()));
        }

        values.put(SettingsId.AUTO_STORE, toggle(controller.isAutoStoreMinedDrops()));
        values.put(SettingsId.STORAGE_REFRESH_QUIET, toggle(RtsClientUiStateStore.isStorageRefreshQuietEnabled()));
        values.put(SettingsId.STORAGE_AUTO_REFRESH, toggle(RtsClientUiStateStore.isStorageAutoRefreshEnabled()));
        values.put(SettingsId.PLACED_RECOVERY, toggle(controller.isAllowPlacedBlockRecovery()));
        values.put(SettingsId.TOOL_PROTECTION, toggle(controller.isToolProtectionEnabled()));
        values.put(SettingsId.DAMAGE_AUTO_RETURN, toggle(controller.isDamageAutoReturnEnabled()));
        values.put(SettingsId.BD_NETWORK, toggle(controller.isBdNetworkEnabled()));

        values.put(SettingsId.RTS_SOUNDS, toggle(RtsClientUiStateStore.isRtsSoundsEnabled()));
        values.put(SettingsId.BREAK_SOUNDS, toggle(RtsClientUiStateStore.isRtsBreakSoundsEnabled()));
        values.put(SettingsId.DAMAGE_SOUND, toggle(controller.isDamageSoundEnabled()));
        int soundLimit = RtsClientUiStateStore.getRtsBlockSoundsPerTick();
        values.put(SettingsId.BLOCK_SOUNDS_PER_TICK,
                value(Integer.toString(soundLimit), soundLimit - 1, 16));

        values.put(SettingsId.SMOOTH_CAMERA, toggle(controller.isSmoothCamera()));
        values.put(SettingsId.PLACEMENT_BLOCK_GHOST_PREVIEW, toggle(Config.isPlacementBlockGhostPreviewEnabled()));
        values.put(SettingsId.PLACE_BLOCK_GHOST_ANIMATION, toggle(Config.isPlaceBlockGhostAnimationEnabled()));
        values.put(SettingsId.DESTROY_BLOCK_GHOST_ANIMATION, toggle(Config.isDestroyBlockGhostAnimationEnabled()));
        values.put(SettingsId.PLACEMENT_WIREFRAME_PREVIEW, toggle(Config.isPlacementWireframePreviewEnabled()));
        values.put(SettingsId.PLACE_WIREFRAME_ANIMATION, toggle(Config.isPlaceWireframeAnimationEnabled()));
        values.put(SettingsId.DESTROY_WIREFRAME_ANIMATION, toggle(Config.isDestroyWireframeAnimationEnabled()));
        values.put(SettingsId.RANGE_DESTROY_SKELETON, toggle(Config.isRangeDestroySkeletonEnabled()));

        EnumSet<SettingsId> expandable = EnumSet.noneOf(SettingsId.class);
        for (SettingsId id : values.keySet()) {
            if (id.kind == SettingsRowKind.HINT_TOGGLE && hintCanExpand(screen, contentX, contentWidth, id)) {
                expandable.add(id);
            }
        }
        return SettingsUiCatalog.create(values, panel.coreExpandedSections(), expandable,
                panel.coreExpandedHints(), panel.coreScroll());
    }

    static boolean dispatch(GearMenuPanel panel, BuilderScreen screen,
                            ClientRtsController controller, SettingsUiAction action,
                            int contentX, int contentWidth) {
        SettingsUiTransition transition = SettingsUiReducer.apply(
                snapshot(panel, screen, controller, contentX, contentWidth), action);
        switch (transition.command) {
            case APPLY_VIEW_STATE:
                panel.applyCoreViewState(transition.state);
                screen.persistUiState();
                return true;
            case SET_SENSITIVITY:
                setSensitivity(controller, action.settingId, action.fraction);
                return true;
            case ADJUST_VALUE:
                adjustValue(screen, action.settingId, action.amount);
                return true;
            case TOGGLE_VALUE:
                toggleValue(screen, controller, action.settingId);
                return true;
            case NONE:
            default:
                return false;
        }
    }

    private static void setSensitivity(ClientRtsController controller, SettingsId id, double fraction) {
        switch (id) {
            case PAN_DRAG_SENSITIVITY -> controller.setPanDragSensitivityByFraction(fraction);
            case ROTATE_VIEW_SENSITIVITY -> controller.setRotateViewSensitivityByFraction(fraction);
            case KEYBOARD_MOVE_SENSITIVITY -> controller.setKeyboardMoveSensitivityByFraction(fraction);
            case WHEEL_ZOOM_SENSITIVITY -> controller.setWheelZoomSensitivityByFraction(fraction);
            default -> { }
        }
    }

    private static void adjustValue(BuilderScreen screen, SettingsId id, int amount) {
        if (id == SettingsId.UI_SCALE) {
            screen.adjustRtsGuiScale(amount * RTS_GUI_SCALE_STEP);
        } else if (id == SettingsId.BLOCK_SOUNDS_PER_TICK) {
            RtsClientUiStateStore.setRtsBlockSoundsPerTick(
                    RtsClientUiStateStore.getRtsBlockSoundsPerTick() + amount);
        }
    }

    private static void toggleValue(BuilderScreen screen, ClientRtsController controller, SettingsId id) {
        switch (id) {
            case START_CAMERA_AT_HEAD -> controller.toggleStartCameraAtPlayerHead();
            case INVERT_PAN_X -> controller.toggleInvertPanDragX();
            case INVERT_PAN_Y -> controller.toggleInvertPanDragY();
            case KEYBOARD_BATCH_CONFIRM -> Config.setKeyboardBatchConfirmEnabled(!Config.isKeyboardBatchConfirmEnabled());
            case PLAYER_STATUS_OVERLAY -> controller.togglePlayerStatusOverlayEnabled();
            case CONTAINER_OVERLAY -> screen.toggleContainerOverlayEnabled();
            case SHIFT_IMPORT -> screen.toggleOverlayShiftImportEnabled();
            case SHOW_STORAGE_READY_POPUP -> screen.toggleShowStorageReadyPopup();
            case SHOW_WORKFLOW_PANEL -> screen.toggleShowWorkflowPanelEnabled();
            case JADE_TRACK_MOUSE -> screen.toggleJadePanelTrackMouse();
            case JADE_HIDDEN -> screen.toggleJadePanelHidden();
            case AUTO_STORE -> controller.toggleAutoStoreMinedDrops();
            case STORAGE_REFRESH_QUIET -> screen.toggleStorageRefreshQuietEnabled();
            case STORAGE_AUTO_REFRESH -> screen.toggleStorageAutoRefreshEnabled();
            case PLACED_RECOVERY -> controller.toggleAllowPlacedBlockRecovery();
            case TOOL_PROTECTION -> controller.toggleToolProtectionEnabled();
            case DAMAGE_AUTO_RETURN -> controller.toggleDamageAutoReturnEnabled();
            case BD_NETWORK -> controller.toggleBdNetworkEnabled();
            case RTS_SOUNDS -> RtsClientUiStateStore.setRtsSoundsEnabled(!RtsClientUiStateStore.isRtsSoundsEnabled());
            case BREAK_SOUNDS -> RtsClientUiStateStore.setRtsBreakSoundsEnabled(!RtsClientUiStateStore.isRtsBreakSoundsEnabled());
            case DAMAGE_SOUND -> controller.toggleDamageSoundEnabled();
            case SMOOTH_CAMERA -> controller.toggleSmoothCamera();
            case PLACEMENT_BLOCK_GHOST_PREVIEW -> Config.setPlacementBlockGhostPreviewEnabled(!Config.isPlacementBlockGhostPreviewEnabled());
            case PLACE_BLOCK_GHOST_ANIMATION -> Config.setPlaceBlockGhostAnimationEnabled(!Config.isPlaceBlockGhostAnimationEnabled());
            case DESTROY_BLOCK_GHOST_ANIMATION -> Config.setDestroyBlockGhostAnimationEnabled(!Config.isDestroyBlockGhostAnimationEnabled());
            case PLACEMENT_WIREFRAME_PREVIEW -> Config.setPlacementWireframePreviewEnabled(!Config.isPlacementWireframePreviewEnabled());
            case PLACE_WIREFRAME_ANIMATION -> Config.setPlaceWireframeAnimationEnabled(!Config.isPlaceWireframeAnimationEnabled());
            case DESTROY_WIREFRAME_ANIMATION -> Config.setDestroyWireframeAnimationEnabled(!Config.isDestroyWireframeAnimationEnabled());
            case RANGE_DESTROY_SKELETON -> Config.setRangeDestroySkeletonEnabled(!Config.isRangeDestroySkeletonEnabled());
            default -> { }
        }
        if (requiresExplicitPersist(id)) screen.persistUiState();
    }

    private static boolean requiresExplicitPersist(SettingsId id) {
        return id == SettingsId.START_CAMERA_AT_HEAD || id == SettingsId.INVERT_PAN_X
                || id == SettingsId.INVERT_PAN_Y || id == SettingsId.PLAYER_STATUS_OVERLAY
                || id == SettingsId.AUTO_STORE || id == SettingsId.PLACED_RECOVERY
                || id == SettingsId.TOOL_PROTECTION || id == SettingsId.DAMAGE_AUTO_RETURN
                || id == SettingsId.BD_NETWORK || id == SettingsId.DAMAGE_SOUND
                || id == SettingsId.SMOOTH_CAMERA;
    }

    private static boolean hintCanExpand(BuilderScreen screen, int x, int width, SettingsId id) {
        int hintX = x + 16 + SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE + 4;
        int toggleX = x + width - 92;
        int maxWidth = Math.max(24, toggleX - hintX - 8);
        return screen.font().width(Component.translatable(id.hintKey).getString()) > maxWidth;
    }

    private static SettingsUiValue toggle(boolean value) {
        return SettingsUiValue.toggle(value);
    }

    private static SettingsUiValue value(String label, int index, int count) {
        return SettingsUiValue.value(label, index, count);
    }

    private static boolean isJadeLoaded() {
        return ModList.get().isLoaded("jade");
    }
}

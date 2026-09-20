package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.Config;

import java.util.EnumMap;
import java.util.Map;

/**
 * 设置页个人域的唯一适配层。
 *
 * <p>这里复用已有 CLIENT 配置来源，不复制 HUD/Gear 的状态文件，也不让世界页的
 * 服务端镜像混入个人保存。窗口只在点击“保存个人设置”时调用 write。</p>
 */
final class RtsClientPersonalSettings {
    enum Key {
        UI_ANIMATIONS("screen.rtsbuilding.personal.ui_animations"),
        BLOCK_GHOST_PREVIEW("screen.rtsbuilding.personal.block_ghost_preview"),
        PLACE_GHOST_ANIMATION("screen.rtsbuilding.personal.place_ghost_animation"),
        DESTROY_GHOST_ANIMATION("screen.rtsbuilding.personal.destroy_ghost_animation"),
        WIREFRAME_PREVIEW("screen.rtsbuilding.personal.wireframe_preview"),
        PLACE_WIREFRAME_ANIMATION("screen.rtsbuilding.personal.place_wireframe_animation"),
        DESTROY_WIREFRAME_ANIMATION("screen.rtsbuilding.personal.destroy_wireframe_animation"),
        RANGE_DESTROY_SKELETON("screen.rtsbuilding.personal.range_destroy_skeleton"),
        KEYBOARD_CONFIRM("screen.rtsbuilding.personal.keyboard_confirm"),
        INVENTORY_BUTTON("screen.rtsbuilding.personal.inventory_button"),
        DEVELOPER_MODE("screen.rtsbuilding.personal.developer_mode");

        private final String labelKey;

        Key(String labelKey) {
            this.labelKey = labelKey;
        }

        String labelKey() {
            return labelKey;
        }

        String hintKey() {
            return labelKey + ".hint";
        }
    }

    private RtsClientPersonalSettings() {
    }

    static EnumMap<Key, Boolean> snapshot() {
        EnumMap<Key, Boolean> values = new EnumMap<>(Key.class);
        for (Key key : Key.values()) values.put(key, read(key));
        return values;
    }

    static boolean read(Key key) {
        return switch (key) {
            case UI_ANIMATIONS -> Config.isUiAnimationsEnabled();
            case BLOCK_GHOST_PREVIEW -> Config.isPlacementBlockGhostPreviewEnabled();
            case PLACE_GHOST_ANIMATION -> Config.isPlaceBlockGhostAnimationEnabled();
            case DESTROY_GHOST_ANIMATION -> Config.isDestroyBlockGhostAnimationEnabled();
            case WIREFRAME_PREVIEW -> Config.isPlacementWireframePreviewEnabled();
            case PLACE_WIREFRAME_ANIMATION -> Config.isPlaceWireframeAnimationEnabled();
            case DESTROY_WIREFRAME_ANIMATION -> Config.isDestroyWireframeAnimationEnabled();
            case RANGE_DESTROY_SKELETON -> Config.isRangeDestroySkeletonEnabled();
            case KEYBOARD_CONFIRM -> Config.isKeyboardBatchConfirmEnabled();
            case INVENTORY_BUTTON -> Config.isInventoryRtsButtonEnabled();
            case DEVELOPER_MODE -> Config.isDeveloperModeEnabled();
        };
    }

    static void write(Key key, boolean enabled) {
        switch (key) {
            case UI_ANIMATIONS -> Config.setUiAnimationsEnabled(enabled);
            case BLOCK_GHOST_PREVIEW -> Config.setPlacementBlockGhostPreviewEnabled(enabled);
            case PLACE_GHOST_ANIMATION -> Config.setPlaceBlockGhostAnimationEnabled(enabled);
            case DESTROY_GHOST_ANIMATION -> Config.setDestroyBlockGhostAnimationEnabled(enabled);
            case WIREFRAME_PREVIEW -> Config.setPlacementWireframePreviewEnabled(enabled);
            case PLACE_WIREFRAME_ANIMATION -> Config.setPlaceWireframeAnimationEnabled(enabled);
            case DESTROY_WIREFRAME_ANIMATION -> Config.setDestroyWireframeAnimationEnabled(enabled);
            case RANGE_DESTROY_SKELETON -> Config.setRangeDestroySkeletonEnabled(enabled);
            case KEYBOARD_CONFIRM -> Config.setKeyboardBatchConfirmEnabled(enabled);
            case INVENTORY_BUTTON -> Config.setInventoryRtsButtonEnabled(enabled);
            case DEVELOPER_MODE -> Config.setDeveloperModeEnabled(enabled);
        }
    }

    static void writeChanged(Map<Key, Boolean> draft, Map<Key, Boolean> baseline) {
        for (Key key : Key.values()) {
            boolean value = Boolean.TRUE.equals(draft.get(key));
            if (value != Boolean.TRUE.equals(baseline.get(key))) write(key, value);
        }
    }
}

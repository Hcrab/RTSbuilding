package com.rtsbuilding.rtsbuilding.uicore.settings;

/**
 * 设置窗全部玩家可见选项的稳定目录。
 *
 * <p>标签和说明键只在此定义一次，生产与离屏不能再各自删减或改序。</p>
 */
public enum SettingsId {
    PAN_DRAG_SENSITIVITY(SettingsSectionId.CONTROLS, SettingsRowKind.SENSITIVITY,
            "screen.rtsbuilding.settings.sensitivity.pan_drag", ""),
    ROTATE_VIEW_SENSITIVITY(SettingsSectionId.CONTROLS, SettingsRowKind.SENSITIVITY,
            "screen.rtsbuilding.settings.sensitivity.rotate_view", ""),
    KEYBOARD_MOVE_SENSITIVITY(SettingsSectionId.CONTROLS, SettingsRowKind.SENSITIVITY,
            "screen.rtsbuilding.settings.sensitivity.keyboard_move", ""),
    WHEEL_ZOOM_SENSITIVITY(SettingsSectionId.CONTROLS, SettingsRowKind.SENSITIVITY,
            "screen.rtsbuilding.settings.sensitivity.wheel_zoom", ""),
    START_CAMERA_AT_HEAD(SettingsSectionId.CONTROLS, SettingsRowKind.SIMPLE_TOGGLE,
            "screen.rtsbuilding.settings.head_start", ""),
    INVERT_PAN_X(SettingsSectionId.CONTROLS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.pan_drag_x_invert", "screen.rtsbuilding.settings.pan_drag_x_invert.hint"),
    INVERT_PAN_Y(SettingsSectionId.CONTROLS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.pan_drag_y_invert", "screen.rtsbuilding.settings.pan_drag_y_invert.hint"),
    KEYBOARD_BATCH_CONFIRM(SettingsSectionId.CONTROLS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.keyboard_batch_confirm", "screen.rtsbuilding.settings.keyboard_batch_confirm.hint"),

    UI_SCALE(SettingsSectionId.DISPLAY, SettingsRowKind.STEP_VALUE,
            "screen.rtsbuilding.settings.ui_scale", ""),
    PLAYER_STATUS_OVERLAY(SettingsSectionId.DISPLAY, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.player_status_overlay", "screen.rtsbuilding.settings.player_status_overlay.hint"),
    CONTAINER_OVERLAY(SettingsSectionId.DISPLAY, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.container_overlay", "screen.rtsbuilding.settings.container_overlay.hint"),
    SHIFT_IMPORT(SettingsSectionId.DISPLAY, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.shift_import", "screen.rtsbuilding.settings.shift_import.hint"),
    SHOW_STORAGE_READY_POPUP(SettingsSectionId.DISPLAY, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.show_storage_ready_popup", "screen.rtsbuilding.settings.show_storage_ready_popup.hint"),
    SHOW_WORKFLOW_PANEL(SettingsSectionId.DISPLAY, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.show_workflow_panel", "screen.rtsbuilding.settings.show_workflow_panel.hint"),
    JADE_TRACK_MOUSE(SettingsSectionId.DISPLAY, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.jade_panel_track_mouse", "screen.rtsbuilding.settings.jade_panel_track_mouse.hint"),
    JADE_HIDDEN(SettingsSectionId.DISPLAY, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.jade_panel_hidden", "screen.rtsbuilding.settings.jade_panel_hidden.hint"),

    AUTO_STORE(SettingsSectionId.HELPERS, SettingsRowKind.SIMPLE_TOGGLE,
            "screen.rtsbuilding.settings.auto_store", ""),
    STORAGE_REFRESH_QUIET(SettingsSectionId.HELPERS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.storage_refresh_quiet", "screen.rtsbuilding.settings.storage_refresh_quiet.hint"),
    STORAGE_AUTO_REFRESH(SettingsSectionId.HELPERS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.storage_auto_refresh", "screen.rtsbuilding.settings.storage_auto_refresh.hint"),
    PLACED_RECOVERY(SettingsSectionId.HELPERS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.placed_recovery", "screen.rtsbuilding.settings.placed_recovery.hint"),
    TOOL_PROTECTION(SettingsSectionId.HELPERS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.tool_protection", "screen.rtsbuilding.settings.tool_protection.hint"),
    DAMAGE_AUTO_RETURN(SettingsSectionId.HELPERS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.damage_auto_return", "screen.rtsbuilding.settings.damage_auto_return.hint"),
    BD_NETWORK(SettingsSectionId.HELPERS, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.bd_network", "screen.rtsbuilding.settings.bd_network.hint"),

    RTS_SOUNDS(SettingsSectionId.SOUND, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.rts_sounds", "screen.rtsbuilding.settings.rts_sounds.hint"),
    BREAK_SOUNDS(SettingsSectionId.SOUND, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.break_sounds", "screen.rtsbuilding.settings.break_sounds.hint"),
    DAMAGE_SOUND(SettingsSectionId.SOUND, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.damage_sound", "screen.rtsbuilding.settings.damage_sound.hint"),
    BLOCK_SOUNDS_PER_TICK(SettingsSectionId.SOUND, SettingsRowKind.STEP_VALUE,
            "screen.rtsbuilding.settings.block_sounds_per_tick", "screen.rtsbuilding.settings.block_sounds_per_tick.hint"),

    SMOOTH_CAMERA(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.smooth_camera", "screen.rtsbuilding.settings.smooth_camera.hint"),
    PLACEMENT_BLOCK_GHOST_PREVIEW(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.placement_block_ghost_preview", "screen.rtsbuilding.settings.placement_block_ghost_preview.hint"),
    PLACE_BLOCK_GHOST_ANIMATION(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.place_block_ghost_animation", "screen.rtsbuilding.settings.place_block_ghost_animation.hint"),
    DESTROY_BLOCK_GHOST_ANIMATION(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.destroy_block_ghost_animation", "screen.rtsbuilding.settings.destroy_block_ghost_animation.hint"),
    PLACEMENT_WIREFRAME_PREVIEW(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.placement_wireframe_preview", "screen.rtsbuilding.settings.placement_wireframe_preview.hint"),
    PLACE_WIREFRAME_ANIMATION(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.place_wireframe_animation", "screen.rtsbuilding.settings.place_wireframe_animation.hint"),
    DESTROY_WIREFRAME_ANIMATION(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.destroy_wireframe_animation", "screen.rtsbuilding.settings.destroy_wireframe_animation.hint"),
    RANGE_DESTROY_SKELETON(SettingsSectionId.ANIMATION, SettingsRowKind.HINT_TOGGLE,
            "screen.rtsbuilding.settings.range_destroy_skeleton", "screen.rtsbuilding.settings.range_destroy_skeleton.hint");

    public final SettingsSectionId section;
    public final SettingsRowKind kind;
    public final String labelKey;
    public final String hintKey;

    SettingsId(SettingsSectionId section, SettingsRowKind kind,
               String labelKey, String hintKey) {
        this.section = section;
        this.kind = kind;
        this.labelKey = labelKey;
        this.hintKey = hintKey;
    }
}

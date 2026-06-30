package com.rtsbuilding.rtsbuilding.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * RTS 模组按键映射集中定义。
 *
 * <p>按键分为两组，分别在「设置 → 控制 → 按键绑定」中显示为独立分类：
 * <ul>
 *   <li>{@link #CATEGORY_FUNCTION 功能按键组}——含 RTS 模式开关等</li>
 *   <li>{@link #CATEGORY_CAMERA 摄像机按键组}——含 W/A/S/D 等移动控制</li>
 * </ul>
 */
public final class RtsKeyMappings {

    private RtsKeyMappings() {}

    /** 功能按键分类（显示在控制设置界面） */
    public static final String CATEGORY_FUNCTION = "key.categories.rtsbuilding.function";

    /** 摄像机按键分类（显示在控制设置界面） */
    public static final String CATEGORY_CAMERA = "key.categories.rtsbuilding.camera";

    /** RTS 模式开关按键（默认 G） */
    public static final KeyMapping TOGGLE_RTS_KEY = new KeyMapping(
            "key.rtsbuilding.toggleRts",
            GLFW.GLFW_KEY_G,
            CATEGORY_FUNCTION
    );

    /** 打开设置面板（默认 Ctrl+,） */
    public static final KeyMapping OPEN_GEAR_MENU_KEY = new KeyMapping(
            "key.rtsbuilding.open_gear_menu",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            CATEGORY_FUNCTION
    );

    /** 切换辅助显示模式（默认 Shift+Alt+Z） */
    public static final KeyMapping TOGGLE_DEBUG_OVERLAY_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_debug_overlay",
            KeyConflictContext.GUI,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY_FUNCTION
    );

    /** 切换相机模式（自由/环绕玩家，默认 Ctrl+M） */
    public static final KeyMapping TOGGLE_CAMERA_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_camera_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY_FUNCTION
    );

    /** 移动玩家到目标位置（默认 Alt+右键） */
    public static final KeyMapping MOVE_PLAYER_KEY = new KeyMapping(
            "key.rtsbuilding.move_player",
            KeyConflictContext.GUI,
            KeyModifier.ALT,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY_FUNCTION
    );

    /** 切换选择模式（框选/点击，默认 Ctrl+T） */
    public static final KeyMapping TOGGLE_SELECT_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_select_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            CATEGORY_FUNCTION
    );

    /** 切换绑定模式（绑定/拖拽，默认 Ctrl+G） */
    public static final KeyMapping TOGGLE_BIND_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_bind_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY_FUNCTION
    );

    /** 切换方向旋转模式（默认 Ctrl+R） */
    public static final KeyMapping TOGGLE_DIRECTION_ROTATE_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_direction_rotate_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY_FUNCTION
    );

    /** 切换物品拾取模式（默认 Ctrl+F） */
    public static final KeyMapping TOGGLE_ITEM_PICKUP_MODE_KEY = new KeyMapping(
            "key.rtsbuilding.toggle_item_pickup_mode",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY_FUNCTION
    );

    /**
     * 注册所有按键映射到游戏。
     *
     * @param event NeoForge 的按键映射注册事件
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_RTS_KEY);
        event.register(OPEN_GEAR_MENU_KEY);
        event.register(TOGGLE_DEBUG_OVERLAY_KEY);
        event.register(TOGGLE_CAMERA_MODE_KEY);
        event.register(MOVE_PLAYER_KEY);
        event.register(TOGGLE_SELECT_MODE_KEY);
        event.register(TOGGLE_BIND_MODE_KEY);
        event.register(TOGGLE_DIRECTION_ROTATE_MODE_KEY);
        event.register(TOGGLE_ITEM_PICKUP_MODE_KEY);
    }
}

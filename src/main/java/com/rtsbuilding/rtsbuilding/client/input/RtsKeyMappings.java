package com.rtsbuilding.rtsbuilding.client.input;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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

    // ======================================================================
    //  Camera movement keys
    //  W/A/S/D 移动摄像机，空格上升，Shift 下降
    // ======================================================================

    /** W → 摄像机前进 */
    public static final KeyMapping CAMERA_FORWARD = new KeyMapping(
            "key.rtsbuilding.camera_forward",
            GLFW.GLFW_KEY_W,
            CATEGORY_CAMERA
    );
    /** S → 摄像机后退 */
    public static final KeyMapping CAMERA_BACK = new KeyMapping(
            "key.rtsbuilding.camera_back",
            GLFW.GLFW_KEY_S,
            CATEGORY_CAMERA
    );
    /** A → 摄像机左移 */
    public static final KeyMapping CAMERA_LEFT = new KeyMapping(
            "key.rtsbuilding.camera_left",
            GLFW.GLFW_KEY_A,
            CATEGORY_CAMERA
    );
    /** D → 摄像机右移 */
    public static final KeyMapping CAMERA_RIGHT = new KeyMapping(
            "key.rtsbuilding.camera_right",
            GLFW.GLFW_KEY_D,
            CATEGORY_CAMERA
    );
    /** 空格 → 摄像机上升 */
    public static final KeyMapping CAMERA_UP = new KeyMapping(
            "key.rtsbuilding.camera_up",
            GLFW.GLFW_KEY_SPACE,
            CATEGORY_CAMERA
    );
    /** Shift → 摄像机下降 */
    public static final KeyMapping CAMERA_DOWN = new KeyMapping(
            "key.rtsbuilding.camera_down",
            GLFW.GLFW_KEY_LEFT_SHIFT,
            CATEGORY_CAMERA
    );

    /**
     * 注册所有按键映射到游戏。
     *
     * @param event NeoForge 的按键映射注册事件
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_RTS_KEY);
        event.register(CAMERA_FORWARD);
        event.register(CAMERA_BACK);
        event.register(CAMERA_LEFT);
        event.register(CAMERA_RIGHT);
        event.register(CAMERA_UP);
        event.register(CAMERA_DOWN);
    }
}

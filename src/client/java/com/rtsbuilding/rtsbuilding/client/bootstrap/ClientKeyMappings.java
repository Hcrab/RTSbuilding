package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    private static final InputConstants.Key LEGACY_ROTATE_DRAG_DEFAULT =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    private static final InputConstants.Key LEGACY_PAN_DRAG_DEFAULT =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    private static final InputConstants.Key DEFAULT_ROTATE_DRAG =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    private static final InputConstants.Key DEFAULT_PAN_DRAG =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);

    public static final KeyMapping TOGGLE_RTS = new KeyMapping(
            "key.rtsbuilding.toggle_rts",
            GLFW.GLFW_KEY_G,
            "key.categories.rtsbuilding");
    public static final KeyMapping QUICK_FUNNEL = new KeyMapping(
            "key.rtsbuilding.quick_funnel",
            GLFW.GLFW_KEY_F,
            "key.categories.rtsbuilding");
    public static final KeyMapping QUICK_DROP = new KeyMapping(
            "key.rtsbuilding.quick_drop",
            GLFW.GLFW_KEY_Q,
            "key.categories.rtsbuilding");
    public static final KeyMapping ROTATE_SHAPE = new KeyMapping(
            "key.rtsbuilding.rotate_shape",
            GLFW.GLFW_KEY_R,
            "key.categories.rtsbuilding");
    public static final KeyMapping OPEN_CRAFT_TERMINAL = new KeyMapping(
            "key.rtsbuilding.open_craft_terminal",
            GLFW.GLFW_KEY_C,
            "key.categories.rtsbuilding");
    public static final KeyMapping PIN_QUICK_SLOT = new KeyMapping(
            "key.rtsbuilding.pin_quick_slot",
            GLFW.GLFW_KEY_P,
            "key.categories.rtsbuilding");
    public static final KeyMapping BLUEPRINT_CANCEL = new KeyMapping(
            "key.rtsbuilding.blueprint_cancel",
            GLFW.GLFW_KEY_X,
            "key.categories.rtsbuilding");
    public static final KeyMapping DECREASE_SENSITIVITY = new KeyMapping(
            "key.rtsbuilding.decrease_sensitivity",
            GLFW.GLFW_KEY_LEFT_BRACKET,
            "key.categories.rtsbuilding");
    public static final KeyMapping INCREASE_SENSITIVITY = new KeyMapping(
            "key.rtsbuilding.increase_sensitivity",
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.categories.rtsbuilding");
    public static final KeyMapping MODE_INTERACT = new KeyMapping(
            "key.rtsbuilding.mode_interact",
            GLFW.GLFW_KEY_I,
            "key.categories.rtsbuilding");
    public static final KeyMapping MODE_LINK_STORAGE = new KeyMapping(
            "key.rtsbuilding.mode_link_storage",
            GLFW.GLFW_KEY_L,
            "key.categories.rtsbuilding");
    public static final KeyMapping MODE_ROTATE = new KeyMapping(
            "key.rtsbuilding.mode_rotate",
            GLFW.GLFW_KEY_R,
            "key.categories.rtsbuilding");
    public static final KeyMapping MODE_FUNNEL = new KeyMapping(
            "key.rtsbuilding.mode_funnel",
            GLFW.GLFW_KEY_F,
            "key.categories.rtsbuilding");
    public static final KeyMapping ACTION_PRIMARY = new KeyMapping(
            "key.rtsbuilding.action_primary",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            "key.categories.rtsbuilding");
    public static final KeyMapping MOVE_PLAYER = new KeyMapping(
            "key.rtsbuilding.move_player",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            "key.categories.rtsbuilding");
    public static final KeyMapping ACTION_BREAK = new KeyMapping(
            "key.rtsbuilding.action_break",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            "key.categories.rtsbuilding");
    public static final KeyMapping CONFIRM_BATCH_PLACE = new KeyMapping(
            "key.rtsbuilding.confirm_batch_place",
            GLFW.GLFW_KEY_ENTER,
            "key.categories.rtsbuilding");
    public static final KeyMapping CONFIRM_BATCH_DESTROY = new KeyMapping(
            "key.rtsbuilding.confirm_batch_destroy",
            GLFW.GLFW_KEY_ENTER,
            "key.categories.rtsbuilding");
    public static final KeyMapping CAMERA_ROTATE_DRAG = new KeyMapping(
            "key.rtsbuilding.camera_rotate_drag",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            "key.categories.rtsbuilding");
    public static final KeyMapping CAMERA_PAN_DRAG = new KeyMapping(
            "key.rtsbuilding.camera_pan_drag",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.rtsbuilding");
    public static final KeyMapping PICK_BLOCK = new KeyMapping(
            "key.rtsbuilding.pick_block",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.rtsbuilding");
    public static final KeyMapping CAMERA_UP = new KeyMapping(
            "key.rtsbuilding.camera_up",
            GLFW.GLFW_KEY_SPACE,
            "key.categories.rtsbuilding");
    public static final KeyMapping CAMERA_UP_SECONDARY = new KeyMapping(
            "key.rtsbuilding.camera_up_secondary",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.rtsbuilding");
    public static final KeyMapping CAMERA_DOWN = new KeyMapping(
            "key.rtsbuilding.camera_down_arrow",
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.rtsbuilding");

    /** Fabric 键位本身只保存鼠标键，Ctrl 语义由 RTS 屏幕路由显式判定。 */
    public static boolean matchesMovePlayerMouse(int button, boolean controlDown) {
        return controlDown && MOVE_PLAYER.matchesMouse(button);
    }

    /** 与鼠标路径共享同一 Ctrl 门槛，兼容玩家把“移动玩家”改绑到键盘。 */
    public static boolean matchesMovePlayerKey(int keyCode, int scanCode, boolean controlDown) {
        return controlDown && MOVE_PLAYER.matches(keyCode, scanCode);
    }
    public static final KeyMapping SELECTION_NUDGE_FORWARD = new KeyMapping(
            "key.rtsbuilding.selection_nudge_forward",
            GLFW.GLFW_KEY_UP,
            "key.categories.rtsbuilding");
    public static final KeyMapping SELECTION_NUDGE_BACK = new KeyMapping(
            "key.rtsbuilding.selection_nudge_back",
            GLFW.GLFW_KEY_DOWN,
            "key.categories.rtsbuilding");
    public static final KeyMapping SELECTION_NUDGE_LEFT = new KeyMapping(
            "key.rtsbuilding.selection_nudge_left",
            GLFW.GLFW_KEY_LEFT,
            "key.categories.rtsbuilding");
    public static final KeyMapping SELECTION_NUDGE_RIGHT = new KeyMapping(
            "key.rtsbuilding.selection_nudge_right",
            GLFW.GLFW_KEY_RIGHT,
            "key.categories.rtsbuilding");
    public static final KeyMapping SELECTION_NUDGE_UP = new KeyMapping(
            "key.rtsbuilding.selection_nudge_up",
            GLFW.GLFW_KEY_PAGE_UP,
            "key.categories.rtsbuilding");
    public static final KeyMapping SELECTION_NUDGE_DOWN = new KeyMapping(
            "key.rtsbuilding.selection_nudge_down",
            GLFW.GLFW_KEY_PAGE_DOWN,
            "key.categories.rtsbuilding");

    private ClientKeyMappings() {
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOGGLE_RTS);
        KeyBindingHelper.registerKeyBinding(QUICK_FUNNEL);
        KeyBindingHelper.registerKeyBinding(QUICK_DROP);
        KeyBindingHelper.registerKeyBinding(ROTATE_SHAPE);
        KeyBindingHelper.registerKeyBinding(OPEN_CRAFT_TERMINAL);
        KeyBindingHelper.registerKeyBinding(PIN_QUICK_SLOT);
        KeyBindingHelper.registerKeyBinding(BLUEPRINT_CANCEL);
        KeyBindingHelper.registerKeyBinding(DECREASE_SENSITIVITY);
        KeyBindingHelper.registerKeyBinding(INCREASE_SENSITIVITY);
        KeyBindingHelper.registerKeyBinding(MODE_INTERACT);
        KeyBindingHelper.registerKeyBinding(MODE_LINK_STORAGE);
        KeyBindingHelper.registerKeyBinding(MODE_ROTATE);
        KeyBindingHelper.registerKeyBinding(MODE_FUNNEL);
        KeyBindingHelper.registerKeyBinding(ACTION_PRIMARY);
        KeyBindingHelper.registerKeyBinding(MOVE_PLAYER);
        KeyBindingHelper.registerKeyBinding(ACTION_BREAK);
        KeyBindingHelper.registerKeyBinding(CONFIRM_BATCH_PLACE);
        KeyBindingHelper.registerKeyBinding(CONFIRM_BATCH_DESTROY);
        KeyBindingHelper.registerKeyBinding(CAMERA_ROTATE_DRAG);
        KeyBindingHelper.registerKeyBinding(CAMERA_PAN_DRAG);
        KeyBindingHelper.registerKeyBinding(PICK_BLOCK);
        KeyBindingHelper.registerKeyBinding(CAMERA_UP);
        KeyBindingHelper.registerKeyBinding(CAMERA_UP_SECONDARY);
        KeyBindingHelper.registerKeyBinding(CAMERA_DOWN);
        KeyBindingHelper.registerKeyBinding(SELECTION_NUDGE_FORWARD);
        KeyBindingHelper.registerKeyBinding(SELECTION_NUDGE_BACK);
        KeyBindingHelper.registerKeyBinding(SELECTION_NUDGE_LEFT);
        KeyBindingHelper.registerKeyBinding(SELECTION_NUDGE_RIGHT);
        KeyBindingHelper.registerKeyBinding(SELECTION_NUDGE_UP);
        KeyBindingHelper.registerKeyBinding(SELECTION_NUDGE_DOWN);
        migrateLegacyDragDefaults();
    }

    private static void migrateLegacyDragDefaults() {
        if (((KeyMappingAccessor) CAMERA_ROTATE_DRAG).getBoundKey().equals(LEGACY_ROTATE_DRAG_DEFAULT)
                && ((KeyMappingAccessor) CAMERA_PAN_DRAG).getBoundKey().equals(LEGACY_PAN_DRAG_DEFAULT)) {
            CAMERA_ROTATE_DRAG.setKey(DEFAULT_ROTATE_DRAG);
            CAMERA_PAN_DRAG.setKey(DEFAULT_PAN_DRAG);
            KeyMapping.resetMapping();
        }
    }
}

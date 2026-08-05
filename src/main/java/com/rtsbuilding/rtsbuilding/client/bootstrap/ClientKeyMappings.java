package com.rtsbuilding.rtsbuilding.client.bootstrap;

import net.minecraft.client.settings.KeyBinding;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** Forge 1.12 客户端按键表；鼠标键使用原版的 {@code button - 100} 编号。 */
@SideOnly(Side.CLIENT)
public final class ClientKeyMappings {
    public static final int MOUSE_LEFT = -100;
    public static final int MOUSE_RIGHT = -99;
    public static final int MOUSE_MIDDLE = -98;
    private static final String CATEGORY = "key.categories.rtsbuilding";

    public static final KeyBinding TOGGLE_RTS = key("key.rtsbuilding.toggle_rts", Keyboard.KEY_G);
    public static final KeyBinding QUICK_FUNNEL = key("key.rtsbuilding.quick_funnel", Keyboard.KEY_F);
    public static final KeyBinding QUICK_DROP = key("key.rtsbuilding.quick_drop", Keyboard.KEY_Q);
    public static final KeyBinding ROTATE_SHAPE = key("key.rtsbuilding.rotate_shape", Keyboard.KEY_R);
    public static final KeyBinding OPEN_CRAFT_TERMINAL = key("key.rtsbuilding.open_craft_terminal", Keyboard.KEY_C);
    public static final KeyBinding PIN_QUICK_SLOT = key("key.rtsbuilding.pin_quick_slot", Keyboard.KEY_P);
    public static final KeyBinding BLUEPRINT_CANCEL = key("key.rtsbuilding.blueprint_cancel", Keyboard.KEY_X);
    public static final KeyBinding DECREASE_SENSITIVITY = key("key.rtsbuilding.decrease_sensitivity", Keyboard.KEY_LBRACKET);
    public static final KeyBinding INCREASE_SENSITIVITY = key("key.rtsbuilding.increase_sensitivity", Keyboard.KEY_RBRACKET);
    public static final KeyBinding MODE_INTERACT = key("key.rtsbuilding.mode_interact", Keyboard.KEY_I);
    public static final KeyBinding MODE_LINK_STORAGE = key("key.rtsbuilding.mode_link_storage", Keyboard.KEY_L);
    public static final KeyBinding MODE_ROTATE = key("key.rtsbuilding.mode_rotate", Keyboard.KEY_R);
    public static final KeyBinding MODE_FUNNEL = key("key.rtsbuilding.mode_funnel", Keyboard.KEY_F);
    public static final KeyBinding ACTION_PRIMARY = key("key.rtsbuilding.action_primary", MOUSE_RIGHT);
    // 1.7.10 的 KeyBinding 没有冲突上下文/修饰键；输入路由仍显式检查 Ctrl。
    public static final KeyBinding MOVE_PLAYER = key("key.rtsbuilding.move_player", MOUSE_RIGHT);
    public static final KeyBinding ACTION_BREAK = key("key.rtsbuilding.action_break", MOUSE_LEFT);
    public static final KeyBinding CONFIRM_BATCH_PLACE = key("key.rtsbuilding.confirm_batch_place", Keyboard.KEY_RETURN);
    public static final KeyBinding CONFIRM_BATCH_DESTROY = key("key.rtsbuilding.confirm_batch_destroy", Keyboard.KEY_RETURN);
    public static final KeyBinding CAMERA_ROTATE_DRAG = key("key.rtsbuilding.camera_rotate_drag", MOUSE_RIGHT);
    public static final KeyBinding CAMERA_PAN_DRAG = key("key.rtsbuilding.camera_pan_drag", MOUSE_MIDDLE);
    public static final KeyBinding PICK_BLOCK = key("key.rtsbuilding.pick_block", MOUSE_MIDDLE);
    public static final KeyBinding CAMERA_UP = key("key.rtsbuilding.camera_up", Keyboard.KEY_SPACE);
    public static final KeyBinding CAMERA_UP_SECONDARY = key("key.rtsbuilding.camera_up_secondary", Keyboard.KEY_NONE);
    public static final KeyBinding CAMERA_DOWN = key("key.rtsbuilding.camera_down_arrow", Keyboard.KEY_LSHIFT);
    public static final KeyBinding SELECTION_NUDGE_FORWARD = key("key.rtsbuilding.selection_nudge_forward", Keyboard.KEY_UP);
    public static final KeyBinding SELECTION_NUDGE_BACK = key("key.rtsbuilding.selection_nudge_back", Keyboard.KEY_DOWN);
    public static final KeyBinding SELECTION_NUDGE_LEFT = key("key.rtsbuilding.selection_nudge_left", Keyboard.KEY_LEFT);
    public static final KeyBinding SELECTION_NUDGE_RIGHT = key("key.rtsbuilding.selection_nudge_right", Keyboard.KEY_RIGHT);
    public static final KeyBinding SELECTION_NUDGE_UP = key("key.rtsbuilding.selection_nudge_up", Keyboard.KEY_PRIOR);
    public static final KeyBinding SELECTION_NUDGE_DOWN = key("key.rtsbuilding.selection_nudge_down", Keyboard.KEY_NEXT);

    private static final KeyBinding[] ALL = {
            TOGGLE_RTS, QUICK_FUNNEL, QUICK_DROP, ROTATE_SHAPE, OPEN_CRAFT_TERMINAL,
            PIN_QUICK_SLOT, BLUEPRINT_CANCEL, DECREASE_SENSITIVITY, INCREASE_SENSITIVITY,
            MODE_INTERACT, MODE_LINK_STORAGE, MODE_ROTATE, MODE_FUNNEL, ACTION_PRIMARY,
            MOVE_PLAYER, ACTION_BREAK, CONFIRM_BATCH_PLACE, CONFIRM_BATCH_DESTROY,
            CAMERA_ROTATE_DRAG, CAMERA_PAN_DRAG, PICK_BLOCK, CAMERA_UP, CAMERA_UP_SECONDARY,
            CAMERA_DOWN, SELECTION_NUDGE_FORWARD, SELECTION_NUDGE_BACK,
            SELECTION_NUDGE_LEFT, SELECTION_NUDGE_RIGHT, SELECTION_NUDGE_UP,
            SELECTION_NUDGE_DOWN
    };

    private static boolean registered;

    private ClientKeyMappings() {
    }

    public static synchronized void register() {
        if (registered) return;
        for (KeyBinding binding : ALL) {
            ClientRegistry.registerKeyBinding(binding);
        }
        registered = true;
        migrateLegacyDragDefaults();
    }

    private static KeyBinding key(String description, int keyCode) {
        return new KeyBinding(description, keyCode, CATEGORY);
    }

    private static void migrateLegacyDragDefaults() {
        if (CAMERA_ROTATE_DRAG.getKeyCode() == MOUSE_MIDDLE
                && CAMERA_PAN_DRAG.getKeyCode() == MOUSE_RIGHT) {
            CAMERA_ROTATE_DRAG.setKeyCode(MOUSE_RIGHT);
            CAMERA_PAN_DRAG.setKeyCode(MOUSE_MIDDLE);
            KeyBinding.resetKeyBindingArrayAndHash();
        }
    }
}

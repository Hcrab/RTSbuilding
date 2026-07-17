package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientKeyMappings {
    /**
     * 26.1 将按键分类从翻译键字符串升级为强类型 ID；仅在客户端注册阶段登记。
     */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "rtsbuilding"));
    private static final InputConstants.Key LEGACY_ROTATE_DRAG_DEFAULT =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    private static final InputConstants.Key LEGACY_PAN_DRAG_DEFAULT =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    private static final InputConstants.Key DEFAULT_ROTATE_DRAG =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    private static final InputConstants.Key DEFAULT_PAN_DRAG =
            InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);

    public static final RtsKeyMapping TOGGLE_RTS = new RtsKeyMapping(
            "key.rtsbuilding.toggle_rts",
            GLFW.GLFW_KEY_G,
            CATEGORY);
    public static final RtsKeyMapping QUICK_FUNNEL = new RtsKeyMapping(
            "key.rtsbuilding.quick_funnel",
            GLFW.GLFW_KEY_F,
            CATEGORY);
    public static final RtsKeyMapping QUICK_DROP = new RtsKeyMapping(
            "key.rtsbuilding.quick_drop",
            GLFW.GLFW_KEY_Q,
            CATEGORY);
    public static final RtsKeyMapping ROTATE_SHAPE = new RtsKeyMapping(
            "key.rtsbuilding.rotate_shape",
            GLFW.GLFW_KEY_R,
            CATEGORY);
    public static final RtsKeyMapping OPEN_CRAFT_TERMINAL = new RtsKeyMapping(
            "key.rtsbuilding.open_craft_terminal",
            GLFW.GLFW_KEY_C,
            CATEGORY);
    public static final RtsKeyMapping PIN_QUICK_SLOT = new RtsKeyMapping(
            "key.rtsbuilding.pin_quick_slot",
            GLFW.GLFW_KEY_P,
            CATEGORY);
    public static final RtsKeyMapping BLUEPRINT_CANCEL = new RtsKeyMapping(
            "key.rtsbuilding.blueprint_cancel",
            GLFW.GLFW_KEY_X,
            CATEGORY);
    public static final RtsKeyMapping DECREASE_SENSITIVITY = new RtsKeyMapping(
            "key.rtsbuilding.decrease_sensitivity",
            GLFW.GLFW_KEY_LEFT_BRACKET,
            CATEGORY);
    public static final RtsKeyMapping INCREASE_SENSITIVITY = new RtsKeyMapping(
            "key.rtsbuilding.increase_sensitivity",
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY);
    public static final RtsKeyMapping MODE_INTERACT = new RtsKeyMapping(
            "key.rtsbuilding.mode_interact",
            GLFW.GLFW_KEY_I,
            CATEGORY);
    public static final RtsKeyMapping MODE_LINK_STORAGE = new RtsKeyMapping(
            "key.rtsbuilding.mode_link_storage",
            GLFW.GLFW_KEY_L,
            CATEGORY);
    public static final RtsKeyMapping MODE_ROTATE = new RtsKeyMapping(
            "key.rtsbuilding.mode_rotate",
            GLFW.GLFW_KEY_R,
            CATEGORY);
    public static final RtsKeyMapping MODE_FUNNEL = new RtsKeyMapping(
            "key.rtsbuilding.mode_funnel",
            GLFW.GLFW_KEY_F,
            CATEGORY);
    public static final RtsKeyMapping ACTION_PRIMARY = new RtsKeyMapping(
            "key.rtsbuilding.action_primary",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY);
    public static final RtsKeyMapping MOVE_PLAYER = new RtsKeyMapping(
            "key.rtsbuilding.move_player",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY);
    public static final RtsKeyMapping ACTION_BREAK = new RtsKeyMapping(
            "key.rtsbuilding.action_break",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            CATEGORY);
    public static final RtsKeyMapping CONFIRM_BATCH_PLACE = new RtsKeyMapping(
            "key.rtsbuilding.confirm_batch_place",
            GLFW.GLFW_KEY_ENTER,
            CATEGORY);
    public static final RtsKeyMapping CONFIRM_BATCH_DESTROY = new RtsKeyMapping(
            "key.rtsbuilding.confirm_batch_destroy",
            GLFW.GLFW_KEY_ENTER,
            CATEGORY);
    public static final RtsKeyMapping CAMERA_ROTATE_DRAG = new RtsKeyMapping(
            "key.rtsbuilding.camera_rotate_drag",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY);
    public static final RtsKeyMapping CAMERA_PAN_DRAG = new RtsKeyMapping(
            "key.rtsbuilding.camera_pan_drag",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY);
    public static final RtsKeyMapping PICK_BLOCK = new RtsKeyMapping(
            "key.rtsbuilding.pick_block",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY);
    public static final RtsKeyMapping CAMERA_UP = new RtsKeyMapping(
            "key.rtsbuilding.camera_up",
            GLFW.GLFW_KEY_SPACE,
            CATEGORY);
    public static final RtsKeyMapping CAMERA_UP_SECONDARY = new RtsKeyMapping(
            "key.rtsbuilding.camera_up_secondary",
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);
    public static final RtsKeyMapping CAMERA_DOWN = new RtsKeyMapping(
            "key.rtsbuilding.camera_down_arrow",
            GLFW.GLFW_KEY_LEFT_SHIFT,
            CATEGORY);
    public static final RtsKeyMapping SELECTION_NUDGE_FORWARD = new RtsKeyMapping(
            "key.rtsbuilding.selection_nudge_forward",
            GLFW.GLFW_KEY_UP,
            CATEGORY);
    public static final RtsKeyMapping SELECTION_NUDGE_BACK = new RtsKeyMapping(
            "key.rtsbuilding.selection_nudge_back",
            GLFW.GLFW_KEY_DOWN,
            CATEGORY);
    public static final RtsKeyMapping SELECTION_NUDGE_LEFT = new RtsKeyMapping(
            "key.rtsbuilding.selection_nudge_left",
            GLFW.GLFW_KEY_LEFT,
            CATEGORY);
    public static final RtsKeyMapping SELECTION_NUDGE_RIGHT = new RtsKeyMapping(
            "key.rtsbuilding.selection_nudge_right",
            GLFW.GLFW_KEY_RIGHT,
            CATEGORY);
    public static final RtsKeyMapping SELECTION_NUDGE_UP = new RtsKeyMapping(
            "key.rtsbuilding.selection_nudge_up",
            GLFW.GLFW_KEY_PAGE_UP,
            CATEGORY);
    public static final RtsKeyMapping SELECTION_NUDGE_DOWN = new RtsKeyMapping(
            "key.rtsbuilding.selection_nudge_down",
            GLFW.GLFW_KEY_PAGE_DOWN,
            CATEGORY);

    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_RTS);
        event.register(QUICK_FUNNEL);
        event.register(QUICK_DROP);
        event.register(ROTATE_SHAPE);
        event.register(OPEN_CRAFT_TERMINAL);
        event.register(PIN_QUICK_SLOT);
        event.register(BLUEPRINT_CANCEL);
        event.register(DECREASE_SENSITIVITY);
        event.register(INCREASE_SENSITIVITY);
        event.register(MODE_INTERACT);
        event.register(MODE_LINK_STORAGE);
        event.register(MODE_ROTATE);
        event.register(MODE_FUNNEL);
        event.register(ACTION_PRIMARY);
        event.register(MOVE_PLAYER);
        event.register(ACTION_BREAK);
        event.register(CONFIRM_BATCH_PLACE);
        event.register(CONFIRM_BATCH_DESTROY);
        event.register(CAMERA_ROTATE_DRAG);
        event.register(CAMERA_PAN_DRAG);
        event.register(PICK_BLOCK);
        event.register(CAMERA_UP);
        event.register(CAMERA_UP_SECONDARY);
        event.register(CAMERA_DOWN);
        event.register(SELECTION_NUDGE_FORWARD);
        event.register(SELECTION_NUDGE_BACK);
        event.register(SELECTION_NUDGE_LEFT);
        event.register(SELECTION_NUDGE_RIGHT);
        event.register(SELECTION_NUDGE_UP);
        event.register(SELECTION_NUDGE_DOWN);
        migrateLegacyDragDefaults();
    }

    private static void migrateLegacyDragDefaults() {
        if (CAMERA_ROTATE_DRAG.getKey().equals(LEGACY_ROTATE_DRAG_DEFAULT)
                && CAMERA_PAN_DRAG.getKey().equals(LEGACY_PAN_DRAG_DEFAULT)) {
            CAMERA_ROTATE_DRAG.setKey(DEFAULT_ROTATE_DRAG);
            CAMERA_PAN_DRAG.setKey(DEFAULT_PAN_DRAG);
            KeyMapping.resetMapping();
        }
    }
}

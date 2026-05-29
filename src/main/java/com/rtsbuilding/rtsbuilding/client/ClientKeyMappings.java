package com.rtsbuilding.rtsbuilding.client;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientKeyMappings {
    public static final KeyMapping TOGGLE_RTS = new KeyMapping(
            "key.rtsbuilding.toggle_rts",
            GLFW.GLFW_KEY_R,
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
            GLFW.GLFW_KEY_Q,
            "key.categories.rtsbuilding");
    public static final KeyMapping MODE_LINK_STORAGE = new KeyMapping(
            "key.rtsbuilding.mode_link_storage",
            GLFW.GLFW_KEY_E,
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
    public static final KeyMapping ACTION_BREAK = new KeyMapping(
            "key.rtsbuilding.action_break",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
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
    public static final KeyMapping CAMERA_DOWN = new KeyMapping(
            "key.rtsbuilding.camera_down",
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.rtsbuilding");

    private ClientKeyMappings() {
    }

    @SubscribeEvent
    public static void register(final RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_RTS);
        event.register(QUICK_FUNNEL);
        event.register(QUICK_DROP);
        event.register(ROTATE_SHAPE);
        event.register(OPEN_CRAFT_TERMINAL);
        event.register(PIN_QUICK_SLOT);
        event.register(DECREASE_SENSITIVITY);
        event.register(INCREASE_SENSITIVITY);
        event.register(MODE_INTERACT);
        event.register(MODE_LINK_STORAGE);
        event.register(MODE_ROTATE);
        event.register(MODE_FUNNEL);
        event.register(ACTION_PRIMARY);
        event.register(ACTION_BREAK);
        event.register(CAMERA_ROTATE_DRAG);
        event.register(CAMERA_PAN_DRAG);
        event.register(PICK_BLOCK);
        event.register(CAMERA_UP);
        event.register(CAMERA_DOWN);
    }
}

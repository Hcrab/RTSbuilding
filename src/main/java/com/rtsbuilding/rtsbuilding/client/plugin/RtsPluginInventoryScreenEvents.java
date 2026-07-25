package com.rtsbuilding.rtsbuilding.client.plugin;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsPluginManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Adds RTS plugin entry points to the vanilla inventory.
 *
 * <p>The events mirror Curios-style integration: a small button opens RTS's own
 * management screen, and Shift-clicking a plugin item sends an install request.
 * No client-side inventory mutation happens here.
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsPluginInventoryScreenEvents {
    private static final int VANILLA_INVENTORY_WIDTH = 176;
    private static final int VANILLA_INVENTORY_HEIGHT = 166;
    private static final int BUTTON_WIDTH = 28;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_X_INSET = 139;
    private static final int BUTTON_Y_INSET = 5;

    private RtsPluginInventoryScreenEvents() {
    }

    @SubscribeEvent
    public static void onInventoryInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen inventoryScreen)) {
            return;
        }
        if (!Config.isInventoryRtsButtonEnabled()) {
            return;
        }
        int x = vanillaInventoryLeft(screen) + BUTTON_X_INSET;
        int y = vanillaInventoryTop(screen) + BUTTON_Y_INSET;
        Button button = Button.builder(Component.literal("RTS"), btn -> openPluginScreen(screen))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("screen.rtsbuilding.plugins.open")))
                .build();
        event.addListener(button);
    }

    @SubscribeEvent
    public static void onInventoryMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen inventoryScreen) || !isShiftDown()) {
            return;
        }
        Slot slot = inventoryScreen.getSlotUnderMouse();
        Minecraft minecraft = Minecraft.getInstance();
        if (slot == null || minecraft.player == null || slot.container != minecraft.player.getInventory()) {
            return;
        }
        if (!RtsClientPluginCatalog.isPluginItem(slot.getItem())) {
            return;
        }
        ClientRtsController.get().installPluginFromInventorySlot(slot.getContainerSlot());
        ClientRtsController.get().requestPluginState();
        event.setCanceled(true);
    }

    private static void openPluginScreen(Screen parent) {
        ClientRtsController.get().requestPluginState();
        Minecraft.getInstance().setScreen(new RtsPluginManagementScreen(parent));
    }

    private static int vanillaInventoryLeft(Screen screen) {
        // 不使用 InventoryScreen#getGuiLeft()：部分属性/饰品类模组会偏移原版背包，
        // RTS 入口如果跟着偏移，容易压到对方新增的按钮或面板。
        int centered = (screen.width - VANILLA_INVENTORY_WIDTH) / 2;
        return Math.max(0, Math.min(centered, screen.width - BUTTON_WIDTH - BUTTON_X_INSET));
    }

    private static int vanillaInventoryTop(Screen screen) {
        int centered = (screen.height - VANILLA_INVENTORY_HEIGHT) / 2;
        return Math.max(0, Math.min(centered, screen.height - BUTTON_HEIGHT - BUTTON_Y_INSET));
    }

    private static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}

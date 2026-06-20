package com.rtsbuilding.rtsbuilding.client.plugin;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsPluginManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Adds RTS plugin entry points to the vanilla inventory.
 *
 * <p>The events mirror Curios-style integration: a small button opens RTS's own
 * management screen, and Shift-clicking a plugin item sends an install request.
 * No client-side inventory mutation happens here.
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RtsPluginInventoryScreenEvents {
    private RtsPluginInventoryScreenEvents() {
    }

    @SubscribeEvent
    public static void onInventoryInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen inventoryScreen)) {
            return;
        }
        int x = inventoryScreen.getGuiLeft() + 139;
        int y = inventoryScreen.getGuiTop() + 5;
        Button button = Button.builder(Component.literal("RTS"), btn -> openPluginScreen(screen))
                .bounds(x, y, 28, 18)
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

    private static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}

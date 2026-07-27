package com.rtsbuilding.rtsbuilding.client.plugin;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Constructor;

/**
 * 把插件管理入口接入 1.12.2 原版背包。
 *
 * <p>这里只渲染按钮并发送安装意图，不在客户端直接修改背包。插件合法性和实际扣取仍由服务端判定。</p>
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
public final class RtsPluginInventoryScreenEvents {
    private static final int VANILLA_INVENTORY_WIDTH = 176;
    private static final int VANILLA_INVENTORY_HEIGHT = 166;
    private static final int BUTTON_WIDTH = 28;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_X_INSET = 139;
    private static final int BUTTON_Y_INSET = 5;
    private static final int BUTTON_ID = 0x525453;
    private static final String PLUGIN_SCREEN =
            "com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsPluginManagementScreen";

    private RtsPluginInventoryScreenEvents() {
    }

    @SubscribeEvent
    public static void onInventoryInit(GuiScreenEvent.InitGuiEvent.Post event) {
        final GuiScreen screen = event.getGui();
        if (!(screen instanceof GuiInventory) || !Config.isInventoryRtsButtonEnabled()) {
            return;
        }
        int x = vanillaInventoryLeft(screen) + BUTTON_X_INSET;
        int y = vanillaInventoryTop(screen) + BUTTON_Y_INSET;
        event.getButtonList().add(new GuiButton(BUTTON_ID, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "RTS") {
            @Override
            public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
                boolean pressed = super.mousePressed(minecraft, mouseX, mouseY);
                if (pressed) {
                    openPluginScreen(screen);
                }
                return pressed;
            }
        });
    }

    @SubscribeEvent
    public static void onInventoryMousePressed(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiInventory) || !Mouse.getEventButtonState() || !isShiftDown()) {
            return;
        }
        GuiInventory inventoryScreen = (GuiInventory) event.getGui();
        Slot slot = inventoryScreen.getSlotUnderMouse();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (slot == null || minecraft.player == null || slot.inventory != minecraft.player.inventory
                || !RtsClientPluginCatalog.isPluginItem(slot.getStack())) {
            return;
        }
        ClientRtsController.get().installPluginFromInventorySlot(slot.getSlotIndex());
        ClientRtsController.get().requestPluginState();
        event.setCanceled(true);
    }

    private static void openPluginScreen(GuiScreen parent) {
        ClientRtsController.get().requestPluginState();
        try {
            Class<?> type = Class.forName(PLUGIN_SCREEN);
            Constructor<?> constructor = type.getConstructor(GuiScreen.class);
            Object screen = constructor.newInstance(parent);
            if (!(screen instanceof GuiScreen)) {
                throw new IllegalStateException(PLUGIN_SCREEN + " 不是 1.12 GuiScreen");
            }
            Minecraft.getMinecraft().displayGuiScreen((GuiScreen) screen);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法打开 RTS 插件管理界面", failure);
        }
    }

    private static int vanillaInventoryLeft(GuiScreen screen) {
        int centered = (screen.width - VANILLA_INVENTORY_WIDTH) / 2;
        return Math.max(0, Math.min(centered, screen.width - BUTTON_WIDTH - BUTTON_X_INSET));
    }

    private static int vanillaInventoryTop(GuiScreen screen) {
        int centered = (screen.height - VANILLA_INVENTORY_HEIGHT) / 2;
        return Math.max(0, Math.min(centered, screen.height - BUTTON_HEIGHT - BUTTON_Y_INSET));
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}

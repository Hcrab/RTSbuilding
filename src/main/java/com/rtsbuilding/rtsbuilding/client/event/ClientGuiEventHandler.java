package com.rtsbuilding.rtsbuilding.client.event;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Method;

/** RTS 主面板打开时，把原版聊天区抬到下方面板上方。 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
public final class ClientGuiEventHandler {
    private static final int CHAT_BOTTOM_MARGIN = 4;
    private static final String BUILDER_SCREEN =
            "com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen";

    private ClientGuiEventHandler() {
    }

    @SubscribeEvent
    public static void onChatOverlay(RenderGameOverlayEvent.Chat event) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen == null || !BUILDER_SCREEN.equals(screen.getClass().getName())) {
            return;
        }
        try {
            Method getBottomY = screen.getClass().getMethod("getBottomY");
            Object value = getBottomY.invoke(screen);
            if (value instanceof Number) {
                event.setPosY(((Number) value).intValue() - CHAT_BOTTOM_MARGIN);
            }
        } catch (ReflectiveOperationException ignored) {
            // 屏幕尚未完成平台迁移时保持原版聊天位置；不让兼容辅助功能阻断主界面。
        }
    }
}

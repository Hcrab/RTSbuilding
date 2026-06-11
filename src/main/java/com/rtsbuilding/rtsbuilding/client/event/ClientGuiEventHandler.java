package com.rtsbuilding.rtsbuilding.client.event;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side GUI overlay hooks.
 *
 * <p>This class currently keeps vanilla chat visible while the RTS Builder
 * screen is open. It does not render RTS UI itself; it only nudges vanilla
 * overlays that would otherwise be hidden behind the bottom inventory panel.
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientGuiEventHandler {

    private static final int CHAT_BOTTOM_MARGIN = 4;

    private ClientGuiEventHandler() {
    }

    @SubscribeEvent
    public static void onChatOverlay(CustomizeGuiOverlayEvent.Chat event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BuilderScreen builderScreen) {
            event.setPosY(builderScreen.getBottomY() - CHAT_BOTTOM_MARGIN);
        }
    }
}

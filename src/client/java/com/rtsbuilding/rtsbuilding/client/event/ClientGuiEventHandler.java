package com.rtsbuilding.rtsbuilding.client.event;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-side event handler for GUI overlay customization.
 * <p>
 * Adjusts the vanilla chat overlay position when the RTS Builder screen is open,
 * so the chat renders above the RTS bottom panel instead of being hidden behind it.
 */
public final class ClientGuiEventHandler {

    private static final int CHAT_BOTTOM_MARGIN = 4;

    private ClientGuiEventHandler() {
    }

    /**
     * Called before the chat messages overlay is rendered.
     * <p>
     * When the RTS {@link BuilderScreen} is open, raises the chat Y position
     * to just above the bottom panel's top edge, so chat messages are visible
     * instead of being occluded by the panel.
     */
    public static int chatBottomY(int vanillaBottomY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BuilderScreen builderScreen) {
            int bottomPanelTopY = builderScreen.getBottomY();
            return bottomPanelTopY - CHAT_BOTTOM_MARGIN;
        }
        return vanillaBottomY;
    }
}

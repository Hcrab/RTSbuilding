package com.rtsbuilding.rtsbuilding.client.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;


public final class TextRenderer {

    private TextRenderer() {}

    
    public static void draw(GuiGraphics g, String text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, text, x, y, color, false);
    }

    
    public static void draw(GuiGraphics g, Component text, int x, int y, int color) {
        draw(g, text.getString(), x, y, color);
    }

    
    public static void drawCentered(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        String display = text == null ? "" : text;
        draw(g, display, centerX - font.width(display) / 2, y, color);
    }

    
    public static void drawCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        drawCentered(g, font, text == null ? "" : text.getString(), centerX, y, color);
    }

    
    public static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        int cut = text.length();
        while (cut > 0 && font.width(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
    }

    
    public static void drawSlotCountOverlay(GuiGraphics g, Font font,
                                             int slotX, int slotY, int slotSize,
                                             String countText, int color) {
        if (font == null || countText == null || countText.isEmpty()) return;

        float slotCountScale = 0.65F;

        
        g.fill(slotX + 1, slotY + slotSize - 7, slotX + slotSize - 1, slotY + slotSize - 1, 0xB0000000);
        
        g.pose().pushPose();
        g.pose().translate(slotX + slotSize - 2, slotY + slotSize - 7, 300.0F);
        g.pose().scale(slotCountScale, slotCountScale, 1.0F);
        g.drawString(font, countText, -font.width(countText), 0, color, true);
        g.pose().popPose();
    }
}

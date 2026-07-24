package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;


public final class ColorPreviewComponent {

    
    public static final int PREVIEW_BAR_H = 16;

    
    private static final int VALUE_RIGHT_MARGIN = 4;

    
    public void render(GuiGraphics g, int previewX, int previewY, int previewW,
                       int initialColor, int currentColor, boolean isHexDisplay) {
        int midX = previewX + previewW / 2;
        Font font = Minecraft.getInstance().font;

        
        g.fill(previewX, previewY, midX, previewY + PREVIEW_BAR_H, initialColor);
        
        g.fill(midX, previewY, previewX + previewW, previewY + PREVIEW_BAR_H, currentColor);

        int borderColor = 0xFF666666;
        
        g.hLine(previewX, previewX + previewW, previewY, borderColor);
        g.hLine(previewX, previewX + previewW, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX, previewY, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX + previewW, previewY, previewY + PREVIEW_BAR_H, borderColor);
        
        g.vLine(midX, previewY, previewY + PREVIEW_BAR_H, borderColor);

        
        String currentValueStr = formatColorValue(currentColor, isHexDisplay);
        String initialValueStr = formatColorValue(initialColor, isHexDisplay);
        int newColorTextColor = ColorMath.isDarkColor(currentColor) ? 0xFFFFFFFF : 0xFF000000;
        int oldColorTextColor = ColorMath.isDarkColor(initialColor) ? 0xFFFFFFFF : 0xFF000000;
        int colorValueY = previewY + (PREVIEW_BAR_H - font.lineHeight) / 2 + 1;

        int currentValueX = midX + (previewW / 2 - font.width(currentValueStr)) / 2;
        int initialValueX = previewX + (previewW / 2 - font.width(initialValueStr)) / 2;

        g.drawString(font, initialValueStr, initialValueX, colorValueY, oldColorTextColor, false);
        g.drawString(font, currentValueStr, currentValueX, colorValueY, newColorTextColor, false);
    }

    
    public boolean isClickOnInitialColor(double mouseX, double mouseY,
                                          int previewX, int previewW, int previewY) {
        int midX = previewX + previewW / 2;
        return mouseX >= previewX && mouseX < midX
                && mouseY >= previewY && mouseY < previewY + PREVIEW_BAR_H;
    }

    
    private static String formatColorValue(int color, boolean hexDisplay) {
        return hexDisplay
                ? String.format("#%06X", color & 0xFFFFFF)
                : String.valueOf(color & 0xFFFFFF);
    }
}

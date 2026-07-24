package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorGroup;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;


public final class SwatchSelectorComponent {

    
    public static final int ROW_H = 20;
    
    private static final int SWATCH_SIZE = 14;
    
    private static final int SWATCH_GAP = 12;
    
    private static final int SWATCH_TEXT_GAP = 3;
    
    private static final int LEFT_PADDING = 6;

    
    public void render(GuiGraphics g, int mouseX, int mouseY,
                       ColorGroup group, int activeSlotIndex, int sectionTop, int contentX) {
        if (group == null || group.size() <= 1) return;

        Font font = Minecraft.getInstance().font;
        int textColor = ThemeManager.getTextColor();
        int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
        int itemX = contentX + LEFT_PADDING;

        for (int i = 0; i < group.size(); i++) {
            String name = group.slot(i).displayName();
            int slotColor = group.slot(i).source().getColor();

            
            g.fill(itemX, swatchY, itemX + SWATCH_SIZE, swatchY + SWATCH_SIZE, slotColor);
            
            int swatchBorder = (i == activeSlotIndex) ? 0xFFFFFFFF : 0xFF444444;
            g.hLine(itemX - 1, itemX + SWATCH_SIZE, swatchY - 1, swatchBorder);
            g.hLine(itemX - 1, itemX + SWATCH_SIZE, swatchY + SWATCH_SIZE, swatchBorder);
            g.vLine(itemX - 1, swatchY - 1, swatchY + SWATCH_SIZE, swatchBorder);
            g.vLine(itemX + SWATCH_SIZE, swatchY - 1, swatchY + SWATCH_SIZE, swatchBorder);

            
            TextRenderer.draw(g, name, itemX + SWATCH_SIZE + SWATCH_TEXT_GAP,
                    sectionTop + (ROW_H - font.lineHeight) / 2 + 1, textColor);

            
            itemX += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name) + SWATCH_GAP;
        }
    }

    
    public int hitTest(double mouseX, double mouseY, ColorGroup group, int sectionTop, int contentX) {
        if (group == null || group.size() <= 1) return -1;

        Font font = Minecraft.getInstance().font;
        int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
        int itemX = contentX + LEFT_PADDING;

        for (int i = 0; i < group.size(); i++) {
            String name = group.slot(i).displayName();
            int itemW = SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name);
            if (mouseX >= itemX && mouseX < itemX + itemW
                    && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE) {
                return i;
            }
            itemX += itemW + SWATCH_GAP;
        }
        return -1;
    }

    
    public int computeMinWidth(ColorGroup group) {
        if (group == null || group.size() <= 1) return 0;

        Font font = Minecraft.getInstance().font;
        int total = LEFT_PADDING + 4; 
        for (int i = 0; i < group.size(); i++) {
            if (i > 0) total += SWATCH_GAP;
            total += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(group.slot(i).displayName());
        }
        return total;
    }
}

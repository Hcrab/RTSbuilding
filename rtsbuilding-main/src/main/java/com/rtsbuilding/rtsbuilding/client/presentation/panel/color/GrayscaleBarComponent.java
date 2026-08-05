package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;

public class GrayscaleBarComponent {

    public static final int BAR_W = 8;
    
    public static final int BAR_H = 95;
    
    public static final int GAP = 4;

    private static final int INDICATOR_DRAW_W = 12;
    private static final int INDICATOR_DRAW_H = 4;

    public void renderBar(GuiGraphics g, int barX, int barY, int baseColor) {
        int br = (baseColor >> 16) & 0xFF;
        int bg = (baseColor >> 8) & 0xFF;
        int bb = baseColor & 0xFF;

        for (int row = 0; row < BAR_H; row++) {
            float t = row / (float) (BAR_H - 1);
            int r = (int) (br * (1 - t));
            int gn = (int) (bg * (1 - t));
            int bn = (int) (bb * (1 - t));
            g.fill(barX, barY + row, barX + BAR_W, barY + row + 1,
                    0xFF000000 | (r << 16) | (gn << 8) | bn);
        }
    }

    public void renderIndicator(GuiGraphics g, int barX, int barY,
                                 float relY, AnimFloat animator,
                                 int mouseX, int mouseY, boolean dragging) {
        int drawX = barX - (INDICATOR_DRAW_W - BAR_W) / 2;
        int indicatorCenterY = barY + Math.round(relY * (BAR_H - 1));
        int drawY = indicatorCenterY - INDICATOR_DRAW_H / 2;

        int minY = barY - INDICATOR_DRAW_H / 2;
        int maxY = barY + BAR_H - INDICATOR_DRAW_H / 2;
        drawY = Math.max(minY, Math.min(maxY, drawY));

        SdfRenderer.drawRoundedRect(g, drawX, drawY, INDICATOR_DRAW_W, INDICATOR_DRAW_H, 3, DarkUiPalette.accent());
    }

    public float pickColor(double mouseY, int barY) {
        double relY = (mouseY - barY) / (double) BAR_H;
        relY = Math.max(0.0, Math.min(1.0, relY));
        return (float) relY;
    }
}


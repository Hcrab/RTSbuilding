package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;

import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


public class GrayscaleBarComponent {

    

    
    public static final int BAR_W = 8;
    
    public static final int BAR_H = 95;
    
    public static final int GAP = 4;

    

    
    private static final ResourceLocation GRAYSCALE_INDICATOR_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_5.png");
    private static final int GRAYSCALE_INDICATOR_TEX_W = 32;
    private static final int GRAYSCALE_INDICATOR_TEX_H = 48;
    
    private static final int GRAYSCALE_INDICATOR_STATE_H = 16;

    private static final TextureInfo GRAYSCALE_INDICATOR_TEX_INFO = new TextureInfo(
            GRAYSCALE_INDICATOR_TEXTURE, GRAYSCALE_INDICATOR_TEX_W, GRAYSCALE_INDICATOR_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);

    
    private static final int INDICATOR_DRAW_W = 12;
    private static final int INDICATOR_DRAW_H = 12;

    

    
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
                                 float relY, FloatAnimation animator,
                                 int mouseX, int mouseY, boolean dragging) {
        int targetState;
        if (dragging) {
            targetState = 2;
        } else if (mouseX >= barX && mouseX < barX + BAR_W
                && mouseY >= barY && mouseY < barY + BAR_H) {
            targetState = 1;
        } else {
            targetState = 0;
        }

        animator.start(targetState);
        animator.tick();

        float stateF = animator.getValue();
        int stateVOffset = Math.round(stateF * GRAYSCALE_INDICATOR_STATE_H);
        stateVOffset = Math.max(0, Math.min(
                GRAYSCALE_INDICATOR_TEX_H - GRAYSCALE_INDICATOR_STATE_H, stateVOffset));

        int drawX = barX - (INDICATOR_DRAW_W - BAR_W) / 2;
        int indicatorCenterY = barY + Math.round(relY * (BAR_H - 1));
        int drawY = indicatorCenterY - INDICATOR_DRAW_H / 2;

        int minY = barY - INDICATOR_DRAW_H / 2;
        int maxY = barY + BAR_H - INDICATOR_DRAW_H / 2;
        drawY = Math.max(minY, Math.min(maxY, drawY));

        SpriteRegion region = new SpriteRegion(
                GRAYSCALE_INDICATOR_TEX_INFO, 0, stateVOffset,
                GRAYSCALE_INDICATOR_TEX_INFO.halfWidth(), GRAYSCALE_INDICATOR_STATE_H);
        SpriteRenderer.drawSprite(g, region.withTheme(), drawX, drawY,
                INDICATOR_DRAW_W, INDICATOR_DRAW_H);
    }

    

    
    public float pickColor(double mouseY, int barY) {
        double relY = (mouseY - barY) / (double) BAR_H;
        relY = Math.max(0.0, Math.min(1.0, relY));
        return (float) relY;
    }
}


package com.rtsbuilding.rtsbuilding.client.presentation.panel.color;

import com.mojang.blaze3d.platform.NativeImage;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

public class ColorWheelComponent {

    private static final ResourceLocation COLOR_WHEEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/colorwheel.png");
    private static final int COLOR_WHEEL_TEX_W = 89;
    private static final int COLOR_WHEEL_TEX_H = 89;
    public static final int DRAW_SIZE = 95;
    public static final int PAD = 3;
    public static final int AREA_SIZE = DRAW_SIZE + PAD * 2;

    private static final TextureInfo COLOR_WHEEL_TEX_INFO = new TextureInfo(
            COLOR_WHEEL_TEXTURE, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H,
            TextureInfo.ThemeLayout.NONE, TextureInfo.FilterMode.NORMAL);

    private static final int WHEEL_CENTER_U = (COLOR_WHEEL_TEX_W - 1) / 2;
    private static final int WHEEL_CENTER_V = (COLOR_WHEEL_TEX_H - 1) / 2;
    private static final int WHEEL_RADIUS = WHEEL_CENTER_U - 1;
    private static final int INDICATOR_RADIUS = 2;
    private NativeImage wheelImage;
    public static class WheelPickResult {
        public final int texU;
        public final int texV;
        public final float relX;
        public final float relY;
        public final int color;

        public WheelPickResult(int texU, int texV, float relX, float relY, int color) {
            this.texU = texU;
            this.texV = texV;
            this.relX = relX;
            this.relY = relY;
            this.color = color;
        }
    }

    public static class IndicatorPos {
        public final int texU;
        public final int texV;
        public final float relX;
        public final float relY;

        public IndicatorPos(int texU, int texV, float relX, float relY) {
            this.texU = texU;
            this.texV = texV;
            this.relX = relX;
            this.relY = relY;
        }
    }

    public void renderWheel(GuiGraphics g, int wheelX, int wheelY) {
        SpriteRenderer.drawSprite(g, new SpriteRegion(
                        COLOR_WHEEL_TEX_INFO, 0, 0, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H),
                wheelX, wheelY, DRAW_SIZE, DRAW_SIZE);
    }

    public void renderIndicator(GuiGraphics g, int wheelX, int wheelY,
                                 float relX, float relY,
                                 AnimFloat animator,
                                 int mouseX, int mouseY, boolean dragging) {
        int dotCenterX = (int) Math.round(wheelX + relX * DRAW_SIZE);
        int dotCenterY = (int) Math.round(wheelY + relY * DRAW_SIZE);

        int minCenter = wheelX + INDICATOR_RADIUS;
        int maxCenter = wheelX + DRAW_SIZE - INDICATOR_RADIUS - 1;
        dotCenterX = Math.max(minCenter, Math.min(maxCenter, dotCenterX));
        minCenter = wheelY + INDICATOR_RADIUS;
        maxCenter = wheelY + DRAW_SIZE - INDICATOR_RADIUS - 1;
        dotCenterY = Math.max(minCenter, Math.min(maxCenter, dotCenterY));

        SdfRenderer.drawCircle(g, dotCenterX, dotCenterY, INDICATOR_RADIUS, 0xFFFFFFFF);
    }

    

    
    public WheelPickResult pickColor(double mouseX, double mouseY, int wheelX, int wheelY) {
        double relX = (mouseX - wheelX) / (double) DRAW_SIZE;
        double relY = (mouseY - wheelY) / (double) DRAW_SIZE;

        
        double centerU_f = WHEEL_CENTER_U / (double) (COLOR_WHEEL_TEX_W - 1);
        double centerV_f = WHEEL_CENTER_V / (double) (COLOR_WHEEL_TEX_H - 1);
        double maxDist = WHEEL_RADIUS / (double) (COLOR_WHEEL_TEX_W - 1);
        double centerOffX = relX - centerU_f;
        double centerOffY = relY - centerV_f;
        double dist = Math.sqrt(centerOffX * centerOffX + centerOffY * centerOffY);

        if (dist > maxDist) {
            double scale = maxDist / dist;
            relX = centerOffX * scale + centerU_f;
            relY = centerOffY * scale + centerV_f;
        }

        relX = Math.max(0.0, Math.min(1.0, relX));
        relY = Math.max(0.0, Math.min(1.0, relY));

        int texU = (int) Math.round(relX * (COLOR_WHEEL_TEX_W - 1));
        int texV = (int) Math.round(relY * (COLOR_WHEEL_TEX_H - 1));
        texU = Math.max(0, Math.min(COLOR_WHEEL_TEX_W - 1, texU));
        texV = Math.max(0, Math.min(COLOR_WHEEL_TEX_H - 1, texV));

        int pickedColor = readPixel(texU, texV);
        if (pickedColor == 0) {
            int[] nearest = findNearestValidColor(texU, texV);
            if (nearest != null) {
                texU = nearest[0];
                texV = nearest[1];
                pickedColor = nearest[2];
            }
        }

        if (pickedColor != 0) {
            return new WheelPickResult(texU, texV, (float) relX, (float) relY, pickedColor);
        }
        return null;
    }

    

    
    public int readPixel(int u, int v) {
        ensureWheelLoaded();
        if (this.wheelImage == null) return 0;

        int argb = this.wheelImage.getPixelRGBA(u, v);
        int a = (argb >> 24) & 0xFF;
        if (a < 200) return 0;

        int b = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int r = argb & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    
    public int[] findNearestValidColor(int startU, int startV) {
        if (this.wheelImage == null) return null;
        int maxR = Math.max(COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H);
        for (int r = 1; r <= maxR; r++) {
            for (int du = -r; du <= r; du++) {
                int u, v;
                u = startU + du;
                if (u >= 0 && u < COLOR_WHEEL_TEX_W) {
                    v = startV - r;
                    int px = checkPixel(u, v);
                    if (px != 0) return new int[]{u, v, px};
                }
                u = startU + du;
                if (u >= 0 && u < COLOR_WHEEL_TEX_W) {
                    v = startV + r;
                    int px = checkPixel(u, v);
                    if (px != 0) return new int[]{u, v, px};
                }
            }
            for (int dv = -r + 1; dv <= r - 1; dv++) {
                int u, v;
                v = startV + dv;
                if (v >= 0 && v < COLOR_WHEEL_TEX_H) {
                    u = startU - r;
                    int px = checkPixel(u, v);
                    if (px != 0) return new int[]{u, v, px};
                }
                v = startV + dv;
                if (v >= 0 && v < COLOR_WHEEL_TEX_H) {
                    u = startU + r;
                    int px = checkPixel(u, v);
                    if (px != 0) return new int[]{u, v, px};
                }
            }
        }
        return null;
    }

    
    public IndicatorPos syncIndicatorToColor(int targetColor) {
        int tr = (targetColor >> 16) & 0xFF;
        int tg = (targetColor >> 8) & 0xFF;
        int tb = targetColor & 0xFF;

        ensureWheelLoaded();

        int bestU = WHEEL_CENTER_U;
        int bestV = WHEEL_CENTER_V;
        long bestDist = Long.MAX_VALUE;

        if (this.wheelImage != null) {
            for (int v = 0; v < COLOR_WHEEL_TEX_H; v++) {
                for (int u = 0; u < COLOR_WHEEL_TEX_W; u++) {
                    int argb = this.wheelImage.getPixelRGBA(u, v);
                    int a = (argb >> 24) & 0xFF;
                    if (a < 200) continue;
                    int pb = (argb >> 16) & 0xFF;
                    int pg = (argb >> 8) & 0xFF;
                    int pr = argb & 0xFF;
                    long dr = tr - pr;
                    long dg = tg - pg;
                    long db = tb - pb;
                    long dist = dr * dr + dg * dg + db * db;
                    if (dist == 0) {
                        bestU = u;
                        bestV = v;
                        break;
                    }
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestU = u;
                        bestV = v;
                    }
                }
            }
        }

        return new IndicatorPos(bestU, bestV,
                bestU / (float) (COLOR_WHEEL_TEX_W - 1),
                bestV / (float) (COLOR_WHEEL_TEX_H - 1));
    }

    
    public IndicatorPos calcIndicatorUVFromHS(float hue, float saturation) {
        double angle = hue * 2.0 * Math.PI;
        double radius = saturation * WHEEL_RADIUS;
        int u = (int) Math.round(WHEEL_CENTER_U + radius * Math.cos(angle));
        int v = (int) Math.round(WHEEL_CENTER_V + radius * Math.sin(angle));
        u = Math.max(0, Math.min(COLOR_WHEEL_TEX_W - 1, u));
        v = Math.max(0, Math.min(COLOR_WHEEL_TEX_H - 1, v));
        return new IndicatorPos(u, v,
                u / (float) (COLOR_WHEEL_TEX_W - 1),
                v / (float) (COLOR_WHEEL_TEX_H - 1));
    }

    

    private void ensureWheelLoaded() {
        if (this.wheelImage != null) return;
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(COLOR_WHEEL_TEXTURE).orElse(null);
            if (resource == null) return;
            try (var stream = resource.open()) {
                this.wheelImage = NativeImage.read(stream);
            }
        } catch (IOException e) {
            this.wheelImage = null;
        }
    }

    private int checkPixel(int u, int v) {
        if (u < 0 || u >= COLOR_WHEEL_TEX_W || v < 0 || v >= COLOR_WHEEL_TEX_H) return 0;
        int argb = this.wheelImage.getPixelRGBA(u, v);
        int a = (argb >> 24) & 0xFF;
        if (a < 200) return 0;
        int b = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int r = argb & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    
    public void release() {
        if (this.wheelImage != null) {
            this.wheelImage.close();
            this.wheelImage = null;
        }
    }
}


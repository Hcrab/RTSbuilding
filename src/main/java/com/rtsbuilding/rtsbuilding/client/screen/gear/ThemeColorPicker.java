package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.mojang.blaze3d.platform.NativeImage;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

/**
 * 从 v2 颜色面板强搬交互语义后适配到当前浮窗体系的色轮。
 *
 * <p>它复用贡献者的色轮纹理和“色相/饱和度 + 明度条”模型，但不依赖 v2 已废弃的 Panel、
 * SpriteRenderer 或动画类。组件不负责主题保存，也不会在拖动时直接修改全局活动主题。</p>
 */
final class ThemeColorPicker {
    static final int WHEEL_SIZE = 95;
    static final int VALUE_W = 10;
    static final int VALUE_GAP = 8;
    private static final int SOURCE_SIZE = 89;
    private static final ResourceLocation WHEEL = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/colorwheel.png");
    private static final int INDICATOR_DRAW_SIZE = 7;

    private NativeImage wheelImage;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha = 255;
    private double indicatorOffsetX;
    private double indicatorOffsetY;

    void setColor(UiColor color) {
        float[] hsb = java.awt.Color.RGBtoHSB(color.red(), color.green(), color.blue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = color.alpha();
        double angle = this.hue * Math.PI * 2.0D;
        double radius = this.saturation * maximumRadius();
        this.indicatorOffsetX = Math.cos(angle) * radius;
        this.indicatorOffsetY = Math.sin(angle) * radius;
    }

    UiColor color() {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        return UiColor.argb(alpha, rgb >>> 16 & 0xFF, rgb >>> 8 & 0xFF, rgb & 0xFF);
    }

    void render(GuiGraphics g, int x, int y, boolean wheelDragging, boolean valueDragging) {
        g.blit(WHEEL, x, y, 0, 0, WHEEL_SIZE, WHEEL_SIZE, WHEEL_SIZE, WHEEL_SIZE);
        int valueX = x + WHEEL_SIZE + VALUE_GAP;
        int fullRgb = java.awt.Color.HSBtoRGB(hue, saturation, 1.0F);
        int fr = fullRgb >>> 16 & 0xFF;
        int fg = fullRgb >>> 8 & 0xFF;
        int fb = fullRgb & 0xFF;
        for (int row = 0; row < WHEEL_SIZE; row++) {
            float value = 1.0F - row / (float) (WHEEL_SIZE - 1);
            int argb = UiColor.opaque(Math.round(fr * value), Math.round(fg * value),
                    Math.round(fb * value)).toArgb();
            g.fill(valueX, y + row, valueX + VALUE_W, y + row + 1, argb);
        }

        int indicatorX = (int) Math.round(x + WHEEL_SIZE / 2.0D + indicatorOffsetX);
        int indicatorY = (int) Math.round(y + WHEEL_SIZE / 2.0D + indicatorOffsetY);
        drawColorIndicator(g, indicatorX, indicatorY, indicatorColor(), wheelDragging);
        int valueY = y + Math.round((1.0F - brightness) * (WHEEL_SIZE - 1));
        g.renderOutline(valueX - 2, valueY - 2, VALUE_W + 4, 5,
                (valueDragging ? SettingsWindowStyle.VALUE : SettingsWindowStyle.LABEL).toArgb());
    }

    /** 选色圆点内部直接使用最终保存的 ARGB，避免固定贴图颜色误导玩家。 */
    private static void drawColorIndicator(
            GuiGraphics g, int centerX, int centerY, int selectedArgb, boolean dragging) {
        int outline = contrastingOutline(selectedArgb);
        int left = centerX - INDICATOR_DRAW_SIZE / 2;
        int top = centerY - INDICATOR_DRAW_SIZE / 2;
        int[] outerInsets = {2, 1, 0, 0, 0, 1, 2};
        for (int row = 0; row < INDICATOR_DRAW_SIZE; row++) {
            int inset = outerInsets[row];
            g.fill(left + inset, top + row,
                    left + INDICATOR_DRAW_SIZE - inset, top + row + 1, outline);
        }

        int innerLeft = centerX - 2;
        int innerTop = centerY - 2;
        int[] innerInsets = {1, 0, 0, 0, 1};
        for (int row = 0; row < innerInsets.length; row++) {
            int inset = innerInsets[row];
            g.fill(innerLeft + inset, innerTop + row,
                    innerLeft + innerInsets.length - inset, innerTop + row + 1, selectedArgb);
        }

        if (dragging) {
            g.renderOutline(left - 1, top - 1,
                    INDICATOR_DRAW_SIZE + 2, INDICATOR_DRAW_SIZE + 2,
                    SettingsWindowStyle.VALUE.toArgb());
        }
    }

    int indicatorColor() {
        return color().toArgb();
    }

    private static int contrastingOutline(int argb) {
        int red = argb >>> 16 & 0xFF;
        int green = argb >>> 8 & 0xFF;
        int blue = argb & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return luminance >= 144
                ? UiColor.opaque(16, 16, 16).toArgb()
                : UiColor.opaque(240, 240, 240).toArgb();
    }

    boolean pickWheel(double mouseX, double mouseY, int x, int y) {
        double centerX = x + WHEEL_SIZE / 2.0D;
        double centerY = y + WHEEL_SIZE / 2.0D;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double maxRadius = maximumRadius();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > maxRadius && distance > 0.0D) {
            dx *= maxRadius / distance;
            dy *= maxRadius / distance;
        }
        this.indicatorOffsetX = dx;
        this.indicatorOffsetY = dy;
        this.hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0D));
        if (this.hue < 0.0F) this.hue += 1.0F;
        this.saturation = (float) Math.max(0.0D, Math.min(1.0D,
                Math.sqrt(dx * dx + dy * dy) / maxRadius));
        UiColor sampled = sample(mouseX, mouseY, x, y);
        if (sampled != null) {
            float[] hsb = java.awt.Color.RGBtoHSB(sampled.red(), sampled.green(), sampled.blue(), null);
            this.hue = hsb[0];
            this.saturation = hsb[1];
        }
        return true;
    }

    private static double maximumRadius() {
        return WHEEL_SIZE * 0.48D;
    }

    boolean pickValue(double mouseY, int y) {
        this.brightness = (float) Math.max(0.01D, Math.min(1.0D,
                1.0D - (mouseY - y) / (WHEEL_SIZE - 1.0D)));
        return true;
    }

    boolean insideWheel(double mouseX, double mouseY, int x, int y) {
        double dx = mouseX - (x + WHEEL_SIZE / 2.0D);
        double dy = mouseY - (y + WHEEL_SIZE / 2.0D);
        return dx * dx + dy * dy <= Math.pow(WHEEL_SIZE * 0.52D, 2.0D);
    }

    boolean insideValue(double mouseX, double mouseY, int x, int y) {
        int valueX = x + WHEEL_SIZE + VALUE_GAP;
        return mouseX >= valueX - 2 && mouseX < valueX + VALUE_W + 2
                && mouseY >= y && mouseY < y + WHEEL_SIZE;
    }

    private UiColor sample(double mouseX, double mouseY, int x, int y) {
        ensureLoaded();
        if (wheelImage == null) return null;
        int u = Math.max(0, Math.min(SOURCE_SIZE - 1,
                (int) Math.round((mouseX - x) / WHEEL_SIZE * (SOURCE_SIZE - 1))));
        int v = Math.max(0, Math.min(SOURCE_SIZE - 1,
                (int) Math.round((mouseY - y) / WHEEL_SIZE * (SOURCE_SIZE - 1))));
        int abgr = wheelImage.getPixelRGBA(u, v);
        int a = abgr >>> 24 & 0xFF;
        if (a < 200) return null;
        return UiColor.argb(alpha, abgr & 0xFF, abgr >>> 8 & 0xFF, abgr >>> 16 & 0xFF);
    }

    private void ensureLoaded() {
        if (wheelImage != null) return;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(WHEEL).orElse(null);
            if (resource == null) return;
            try (var stream = resource.open()) {
                wheelImage = NativeImage.read(stream);
            }
        } catch (IOException ignored) {
            wheelImage = null;
        }
    }

    void release() {
        if (wheelImage != null) {
            wheelImage.close();
            wheelImage = null;
        }
    }
}

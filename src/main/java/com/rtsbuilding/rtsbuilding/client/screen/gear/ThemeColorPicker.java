package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;

import com.mojang.blaze3d.platform.NativeImage;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.io.IOException;

/**
 * 主题 Palette 模式使用的色相/饱和度/明度选择器。
 *
 * <p>该组件只修改内存草稿，不会在拖动过程中切换全局主题。色轮圆点内部始终绘制最终
 * ARGB 值，保证所见颜色、十六进制文本和最终保存值完全一致。</p>
 */
final class ThemeColorPicker {
    static final int WHEEL_SIZE = 95;
    static final int VALUE_W = 10;
    static final int VALUE_GAP = 8;
    private static final int SOURCE_SIZE = 89;
    private static final Identifier WHEEL = Identifier.fromNamespaceAndPath(
            "rtsbuilding", "textures/gui/color/colorwheel.png");
    private static final int INDICATOR_SIZE = 7;

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
        int rgb = java.awt.Color.HSBtoRGB(this.hue, this.saturation, this.brightness);
        return UiColor.argb(this.alpha, rgb >>> 16 & 0xFF, rgb >>> 8 & 0xFF, rgb & 0xFF);
    }

    void render(GuiGraphicsExtractor graphics, int x, int y,
                boolean wheelDragging, boolean valueDragging) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, WHEEL, x, y, 0, 0,
                WHEEL_SIZE, WHEEL_SIZE, SOURCE_SIZE, SOURCE_SIZE, RtsMainlineTheme.LEGACY_FFFFFFFF.toArgb());
        int valueX = x + WHEEL_SIZE + VALUE_GAP;
        int fullRgb = java.awt.Color.HSBtoRGB(this.hue, this.saturation, 1.0F);
        int red = fullRgb >>> 16 & 0xFF;
        int green = fullRgb >>> 8 & 0xFF;
        int blue = fullRgb & 0xFF;
        for (int row = 0; row < WHEEL_SIZE; row++) {
            float value = 1.0F - row / (float) (WHEEL_SIZE - 1);
            graphics.fill(valueX, y + row, valueX + VALUE_W, y + row + RtsMainlineLayout.D1,
                    UiColor.opaque(Math.round(red * value), Math.round(green * value),
                            Math.round(blue * value)).toArgb());
        }
        int indicatorX = (int) Math.round(x + WHEEL_SIZE / 2.0D + this.indicatorOffsetX);
        int indicatorY = (int) Math.round(y + WHEEL_SIZE / 2.0D + this.indicatorOffsetY);
        drawIndicator(graphics, indicatorX, indicatorY, color().toArgb(), wheelDragging);
        int valueY = y + Math.round((1.0F - this.brightness) * (WHEEL_SIZE - 1));
        outline(graphics, valueX - 2, valueY - 2, VALUE_W + 4, 5,
                (valueDragging ? SettingsWindowStyle.VALUE : SettingsWindowStyle.LABEL).toArgb());
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

    void pickWheel(double mouseX, double mouseY, int x, int y) {
        double centerX = x + WHEEL_SIZE / 2.0D;
        double centerY = y + WHEEL_SIZE / 2.0D;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double maximum = maximumRadius();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > maximum && distance > 0.0D) {
            dx *= maximum / distance;
            dy *= maximum / distance;
        }
        this.indicatorOffsetX = dx;
        this.indicatorOffsetY = dy;
        this.hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0D));
        if (this.hue < 0.0F) this.hue += 1.0F;
        this.saturation = (float) Math.max(0.0D, Math.min(1.0D,
                Math.sqrt(dx * dx + dy * dy) / maximum));
        UiColor sampled = sample(mouseX, mouseY, x, y);
        if (sampled != null) {
            float[] hsb = java.awt.Color.RGBtoHSB(sampled.red(), sampled.green(), sampled.blue(), null);
            this.hue = hsb[0];
            this.saturation = hsb[1];
        }
    }

    void pickValue(double mouseY, int y) {
        this.brightness = (float) Math.max(0.01D, Math.min(1.0D,
                1.0D - (mouseY - y) / (WHEEL_SIZE - 1.0D)));
    }

    void release() {
        if (this.wheelImage != null) {
            this.wheelImage.close();
            this.wheelImage = null;
        }
    }

    private UiColor sample(double mouseX, double mouseY, int x, int y) {
        ensureLoaded();
        if (this.wheelImage == null) return null;
        int u = Math.max(0, Math.min(SOURCE_SIZE - 1,
                (int) Math.round((mouseX - x) / WHEEL_SIZE * (SOURCE_SIZE - 1))));
        int v = Math.max(0, Math.min(SOURCE_SIZE - 1,
                (int) Math.round((mouseY - y) / WHEEL_SIZE * (SOURCE_SIZE - 1))));
        int argb = this.wheelImage.getPixel(u, v);
        if ((argb >>> 24 & 0xFF) < 200) return null;
        return UiColor.argb(this.alpha, argb >>> 16 & 0xFF, argb >>> 8 & 0xFF, argb & 0xFF);
    }

    private void ensureLoaded() {
        if (this.wheelImage != null) return;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(WHEEL).orElse(null);
            if (resource == null) return;
            try (var stream = resource.open()) {
                this.wheelImage = NativeImage.read(stream);
            }
        } catch (IOException ignored) {
            this.wheelImage = null;
        }
    }

    private static void drawIndicator(GuiGraphicsExtractor graphics, int centerX, int centerY,
                                      int selectedArgb, boolean dragging) {
        int outline = contrastingOutline(selectedArgb);
        int left = centerX - INDICATOR_SIZE / 2;
        int top = centerY - INDICATOR_SIZE / 2;
        int[] outerInsets = {2, 1, 0, 0, 0, 1, 2};
        for (int row = 0; row < INDICATOR_SIZE; row++) {
            int inset = outerInsets[row];
            graphics.fill(left + inset, top + row, left + INDICATOR_SIZE - inset, top + row + RtsMainlineLayout.D1, outline);
        }
        int innerLeft = centerX - 2;
        int innerTop = centerY - 2;
        int[] innerInsets = {1, 0, 0, 0, 1};
        for (int row = 0; row < innerInsets.length; row++) {
            int inset = innerInsets[row];
            graphics.fill(innerLeft + inset, innerTop + row,
                    innerLeft + innerInsets.length - inset, innerTop + row + RtsMainlineLayout.D1, selectedArgb);
        }
        if (dragging) outline(graphics, left - 1, top - 1, INDICATOR_SIZE + 2, INDICATOR_SIZE + 2,
                SettingsWindowStyle.VALUE.toArgb());
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + RtsMainlineLayout.D1, color);
        graphics.fill(x, y + height - RtsMainlineLayout.D1, x + width, y + height, color);
        graphics.fill(x, y, x + RtsMainlineLayout.D1, y + height, color);
        graphics.fill(x + width - RtsMainlineLayout.D1, y, x + width, y + height, color);
    }

    private static int contrastingOutline(int argb) {
        int red = argb >>> 16 & 0xFF;
        int green = argb >>> 8 & 0xFF;
        int blue = argb & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 >= 144
                ? UiColor.opaque(16, 16, 16).toArgb() : UiColor.opaque(240, 240, 240).toArgb();
    }

    private static double maximumRadius() {
        return WHEEL_SIZE * 0.48D;
    }
}

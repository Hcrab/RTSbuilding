package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

import com.mojang.blaze3d.platform.NativeImage;
import com.rtsbuilding.rtsbuilding.client.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

/**
 * 颜色轮盘组件——管理轮盘贴图加载、像素读取、指示点渲染与取色交互。
 *
 * <p>组件内部仅持有轮盘贴图的 {@link NativeImage} 缓存（懒加载），
 * 渲染和交互方法均需外部传入坐标和状态参数。布局由调用者（面板）统一编排。</p>
 */
public class ColorWheelComponent {

    // ======================== 贴图常量 ========================

    private static final ResourceLocation COLOR_WHEEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/colorwheel.png");
    private static final int COLOR_WHEEL_TEX_W = 89;
    private static final int COLOR_WHEEL_TEX_H = 89;
    /** 颜色轮盘在面板内的绘制尺寸 */
    public static final int DRAW_SIZE = 95;
    /** 颜色轮盘区域的外边距（给浮窗背景留空间） */
    public static final int PAD = 3;

    /** 轮盘浮窗区域尺寸 = 绘制尺寸 + 双边距 */
    public static final int AREA_SIZE = DRAW_SIZE + PAD * 2;

    /** 颜色轮盘贴图元数据（避免每帧 new） */
    private static final TextureInfo COLOR_WHEEL_TEX_INFO = new TextureInfo(
            COLOR_WHEEL_TEXTURE, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H,
            TextureInfo.ThemeLayout.NONE, TextureInfo.FilterMode.NORMAL);

    /** 色盘圆心在贴图中的像素坐标（贴图 89×89 → (44,44)） */
    private static final int WHEEL_CENTER_U = (COLOR_WHEEL_TEX_W - 1) / 2;
    private static final int WHEEL_CENTER_V = (COLOR_WHEEL_TEX_H - 1) / 2;
    /** 色盘有效圆形区域的像素半径（贴图有 1px 透明边，实际 r=43） */
    private static final int WHEEL_RADIUS = WHEEL_CENTER_U - 1;

    // ======================== 指示点贴图 ========================

    private static final ResourceLocation INDICATOR_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/color_palette_indicator.png");
    private static final int INDICATOR_TEX_W = 72;
    private static final int INDICATOR_TEX_H = 216;
    /** 每个状态的贴图高度（72px） */
    private static final int INDICATOR_STATE_H = 72;

    private static final TextureInfo INDICATOR_TEX_INFO = new TextureInfo(
            INDICATOR_TEXTURE, INDICATOR_TEX_W, INDICATOR_TEX_H,
            TextureInfo.ThemeLayout.NONE, TextureInfo.FilterMode.PIXEL);
    /** 指示点在屏幕上的绘制尺寸 */
    private static final int INDICATOR_DRAW_SIZE = 5;

    // ======================== 轮盘贴图缓存 ========================

    private NativeImage wheelImage;

    // ======================== 取色结果 ========================

    /**
     * 轮盘取色结果——包含贴图 UV、归一化位置和 ARGB 颜色值。
     */
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

    /**
     * 指示点 UV/归一化坐标结果。
     */
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

    // ======================== 渲染 ========================

    /**
     * 绘制轮盘精灵图。
     *
     * @param g       绘制上下文
     * @param wheelX  轮盘图像左上角屏幕 X
     * @param wheelY  轮盘图像左上角屏幕 Y
     */
    public void renderWheel(GuiGraphics g, int wheelX, int wheelY) {
        RtsClientUiUtil.drawSprite(g, new SpriteRegion(
                        COLOR_WHEEL_TEX_INFO, 0, 0, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H),
                wheelX, wheelY, DRAW_SIZE, DRAW_SIZE);
    }

    /**
     * 在轮盘上绘制指示点，支持三态平滑动画。
     *
     * @param g          绘制上下文
     * @param wheelX     轮盘图像左上角 X
     * @param wheelY     轮盘图像左上角 Y
     * @param relX       指示点归一化 X [0,1]
     * @param relY       指示点归一化 Y [0,1]
     * @param animator   状态过渡动画器
     * @param mouseX     鼠标屏幕 X
     * @param mouseY     鼠标屏幕 Y
     * @param dragging   是否正在拖拽
     */
    public void renderIndicator(GuiGraphics g, int wheelX, int wheelY,
                                 float relX, float relY,
                                 SmoothAnimator animator,
                                 int mouseX, int mouseY, boolean dragging) {
        int targetState;
        if (dragging) {
            targetState = 2;
        } else if (mouseX >= wheelX && mouseX < wheelX + DRAW_SIZE
                && mouseY >= wheelY && mouseY < wheelY + DRAW_SIZE) {
            targetState = 1;
        } else {
            targetState = 0;
        }

        animator.start(targetState);
        animator.tick();

        float stateF = animator.getValue();
        int stateVOffset = Math.round(stateF * INDICATOR_STATE_H);
        stateVOffset = Math.max(0, Math.min(INDICATOR_TEX_H - INDICATOR_STATE_H, stateVOffset));

        int dotCenterX = (int) Math.round(wheelX + relX * DRAW_SIZE);
        int dotCenterY = (int) Math.round(wheelY + relY * DRAW_SIZE);

        int halfDot = INDICATOR_DRAW_SIZE / 2;
        int minCenter = wheelX + halfDot;
        int maxCenter = wheelX + DRAW_SIZE - halfDot - 1;
        dotCenterX = Math.max(minCenter, Math.min(maxCenter, dotCenterX));
        minCenter = wheelY + halfDot;
        maxCenter = wheelY + DRAW_SIZE - halfDot - 1;
        dotCenterY = Math.max(minCenter, Math.min(maxCenter, dotCenterY));

        SpriteRegion region = new SpriteRegion(
                INDICATOR_TEX_INFO, 0, stateVOffset,
                INDICATOR_TEX_W, INDICATOR_STATE_H);
        RtsClientUiUtil.drawSprite(g, region,
                dotCenterX - halfDot, dotCenterY - halfDot,
                INDICATOR_DRAW_SIZE, INDICATOR_DRAW_SIZE);
    }

    // ======================== 取色交互 ========================

    /**
     * 根据鼠标在轮盘上的位置取色。
     *
     * @param mouseX  鼠标屏幕 X
     * @param mouseY  鼠标屏幕 Y
     * @param wheelX  轮盘图像左上角 X
     * @param wheelY  轮盘图像左上角 Y
     * @return 取色结果，若像素全透明则返回 null
     */
    public WheelPickResult pickColor(double mouseX, double mouseY, int wheelX, int wheelY) {
        double relX = (mouseX - wheelX) / (double) DRAW_SIZE;
        double relY = (mouseY - wheelY) / (double) DRAW_SIZE;

        // 圆形边界吸附
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

    // ======================== 像素读取 ========================

    /**
     * 读取轮盘贴图在 (u, v) 位置的像素颜色（ARGB）。
     * 懒加载贴图数据，透明像素返回 0。
     */
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

    /**
     * 从位置 (startU, startV) 开始，沿矩形环向外查找最近的非透明像素。
     *
     * @return 长度为 3 的数组 [u, v, argb] 或 null（全透明）
     */
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

    /**
     * 在当前轮盘贴图中查找与目标颜色最接近的像素位置。
     *
     * @return 指示点位置信息
     */
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

    /**
     * 根据色调和饱和度计算轮盘指示点的 UV/归一化坐标。
     */
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

    // ======================== 内部工具 ========================

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

    /** 释放轮盘贴图缓存（资源重载时调用） */
    public void release() {
        if (this.wheelImage != null) {
            this.wheelImage.close();
            this.wheelImage = null;
        }
    }
}

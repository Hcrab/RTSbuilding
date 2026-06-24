package com.rtsbuilding.rtsbuilding.client.screen.panel.util;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * 缩放滑条组件——渲染一个水平滑条轨道和滑块，支持点击跳转和拖拽微调。
 * <p>滑条轨道与滑块均使用九宫格贴图渲染，支持任意宽度。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * ScaleSliderComponent slider = new ScaleSliderComponent();
 *
 * // 渲染
 * slider.render(g, mouseX, mouseY, trackX, trackY, trackW, MIN, MAX, currentValue);
 *
 * // 点击
 * Double newVal = slider.handleClick(mouseX, mouseY, trackX, trackY, trackW, MIN, MAX);
 * if (newVal != null) { setValue(newVal); }
 *
 * // 拖拽（在 mouseDragged 中）
 * if (slider.isDragging()) {
 *     double val = slider.handleDrag(mouseX, trackX, trackW, MIN, MAX);
 *     setValue(val);
 * }
 *
 * // 释放（在 mouseReleased 中）
 * slider.endDrag();
 * }</pre>
 */
public class ScaleSliderComponent {

    // ======================== 贴图常量 ========================

    private static final ResourceLocation SLIDER_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/mouse_wheel_2.png");
    private static final int SLIDER_TEX_W = 32;
    private static final int SLIDER_TEX_H = 32;
    private static final int SLIDER_BORDER = 2;
    private static final int TRACK_SRC_W = 16;
    private static final int TRACK_SRC_H = 4;
    private static final int THUMB_SRC_W = 16;
    private static final int THUMB_SRC_H = 6;

    /** 渲染时滑块的视觉宽度 */
    private static final int THUMB_W = 8;
    /** 滑条轨道点击检测在 Y 方向向外扩展的像素数，方便点击到窄轨道 */
    private static final int TRACK_CLICK_PADDING = 3;

    // ======================== 拖拽状态 ========================

    private boolean dragging = false;
    /** 点击时鼠标的 X 坐标（拖拽增量基准） */
    private double clickMouseX = 0;
    /** 点击时的滑块值（拖拽增量基准） */
    private double clickValue = 0;
    /** 拖拽时每像素对应的值增量 */
    private double valuePerPixel = 0;
    /** 最近渲染时滑块左上角 X（用于点击检测） */
    private int renderedThumbX = 0;
    /** 最近渲染时的当前值（用于拖拽基准） */
    private double currentValue = 0;

    // ======================== 渲染 ========================

    /**
     * 渲染滑条轨道和滑块。
     *
     * @param g        渲染上下文
     * @param mouseX   鼠标 X（用于悬浮检测，当前未使用）
     * @param mouseY   鼠标 Y（当前未使用）
     * @param trackX   滑条轨道左上角 X
     * @param trackY   滑条轨道左上角 Y
     * @param trackW   滑条轨道宽度
     * @param min      最小值
     * @param max      最大值
     * @param value    当前值
     */
    public void render(GuiGraphics g, int mouseX, int mouseY,
                       int trackX, int trackY, int trackW,
                       double min, double max, double value) {
        if (trackW <= 0) return;

        // 拖拽状态下切换为下层贴图
        int layerOffset = dragging ? SLIDER_TEX_H / 2 : 0;

        // 滑条轨道
        RtsClientUiUtil.drawNineSliceRegion(g, SLIDER_TEXTURE,
                trackX, trackY, trackW, TRACK_SRC_H, SLIDER_BORDER,
                SLIDER_TEX_W, SLIDER_TEX_H,
                0, layerOffset, TRACK_SRC_W, TRACK_SRC_H);

        // 滑块（垂直居中于滑条轨道）
        int thumbX = trackX + (int) Math.round((value - min) / (max - min) * (trackW - THUMB_W));
        this.renderedThumbX = thumbX;
        this.currentValue = value;
        int thumbY = trackY + (TRACK_SRC_H - THUMB_SRC_H) / 2;
        RtsClientUiUtil.drawNineSliceRegion(g, SLIDER_TEXTURE,
                thumbX, thumbY, THUMB_W, THUMB_SRC_H, SLIDER_BORDER,
                SLIDER_TEX_W, SLIDER_TEX_H,
                0, 5 + layerOffset, THUMB_SRC_W, THUMB_SRC_H);
    }

    // ======================== 交互 ========================

    /**
     * 处理滑条点击。仅当点击命中滑块（thumb）时开启拖拽追踪，不直接跳转值。
     *
     * @return 始终返回 {@code null}（点击不改变值，仅激活拖拽）
     */
    @Nullable
    public Double handleClick(double mouseX, double mouseY,
                              int trackX, int trackY, int trackW,
                              double min, double max) {
        if (trackW <= 0) return null;

        // Y 方向双向扩展 TRACK_CLICK_PADDING 方便点到窄轨道
        // X 方向只检测滑块区域，点击轨道本身不做跳转
        if (mouseY >= trackY - TRACK_CLICK_PADDING
                && mouseY < trackY + TRACK_SRC_H + TRACK_CLICK_PADDING
                && mouseX >= renderedThumbX && mouseX < renderedThumbX + THUMB_W) {

            // 开始拖拽追踪（基于当前值，不改变值）
            clickMouseX = mouseX;
            clickValue = currentValue;
            double pixelRange = Math.max(1, trackW - THUMB_W);
            valuePerPixel = (max - min) / pixelRange;
            dragging = true;

            return null;
        }
        return null;
    }

    // ======================== 滚轮 ========================

    /**
     * 处理鼠标滚轮在滑条区域上的滚动。
     * 鼠标需要位于轨道区域内才会响应。
     *
     * @return 如果鼠标在滑条区域内则返回调整后的新值，否则返回 {@code null}
     */
    @Nullable
    public Double handleScroll(double mouseX, double mouseY, double scrollY,
                               int trackX, int trackY, int trackW,
                               double min, double max) {
        if (trackW <= 0) return null;

        // Y 方向检测范围与 handleClick 一致
        if (mouseY >= trackY - TRACK_CLICK_PADDING
                && mouseY < trackY + TRACK_SRC_H + TRACK_CLICK_PADDING
                && mouseX >= trackX && mouseX < trackX + trackW) {

            double step = 0.1; // 每格滚轮调整 0.1
            double newValue = currentValue + (scrollY > 0 ? step : -step);
            newValue = Mth.clamp(newValue, min, max);
            newValue = Math.round(newValue * 100.0) / 100.0;
            return newValue;
        }
        return null;
    }

    // ======================== 拖拽 ========================

    /**
     * 处理滑块拖拽（基于鼠标增量，避免分母不一致或坐标漂移导致的跳变）。
     *
     * @param mouseX  鼠标当前 X
     * @param trackX  滑条轨道左上角 X（用于映射）
     * @param trackW  滑条轨道宽度
     * @param min     最小值
     * @param max     最大值
     * @return 拖拽后的新值
     */
    public double handleDrag(double mouseX, int trackX, int trackW,
                             double min, double max) {
        if (!dragging || trackW <= 0) return min;

        double dx = mouseX - clickMouseX;
        double newValue = clickValue + dx * valuePerPixel;
        newValue = Mth.clamp(newValue, min, max);
        newValue = Math.round(newValue * 10.0) / 10.0;
        return newValue;
    }

    // ======================== 状态查询 ========================

    /** 是否正在拖拽滑块 */
    public boolean isDragging() {
        return dragging;
    }

    /** 结束滑块拖拽 */
    public void endDrag() {
        this.dragging = false;
    }
}

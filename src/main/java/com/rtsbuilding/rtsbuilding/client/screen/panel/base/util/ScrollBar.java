package com.rtsbuilding.rtsbuilding.client.screen.panel.base.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;

/**
 * 垂直滚动条组件——管理滚动状态、渲染轨道/滑块、处理鼠标交互。
 *
 * <p>支持鼠标滚轮滚动、点击轨道翻页跳转、拖拽滑块精确滚动。
 * 滑块大小随内容可见比例自动缩放。
 *
 * <p>使用方式：
 * <pre>{@code
 * ScrollBar scrollBar = new ScrollBar();
 *
 * // 在内容尺寸变化时设置范围
 * scrollBar.setContent(contentHeight, visibleHeight);
 *
 * // 渲染
 * scrollBar.render(g, barX, barY, barH);
 *
 * // 事件分发
 * scrollBar.handleScroll(scrollY);
 * scrollBar.handleClick(mouseX, mouseY, barX, barY, barH);
 * scrollBar.handleDrag(mouseY, barY, barH);
 * scrollBar.endDrag();
 * }</pre>
 */
public class ScrollBar {

    // ======================== 默认外观常量 ========================

    private static final int DEFAULT_TRACK_WIDTH = 2;
    private static final int THUMB_EXTEND = 1; // 滑块比轨道左右各宽 1px（视觉凸出）
    private static final int MIN_THUMB_SIZE = 12;

    // ======================== 贴图常量 ========================

    private static final ResourceLocation SCROLLBAR_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/mouse_wheel.png");
    /** 贴图文件总宽度（双主题横向翻倍） */
    private static final int SCROLLBAR_TEX_W = 32;
    /** 贴图文件总高度 */
    private static final int SCROLLBAR_TEX_H = 32;
    /** 九宫格边框像素宽度 */
    private static final int SCROLLBAR_BORDER = 1;
    /** 滑条源矩形（左半区内 x=0..4, y=0..16 区域，4×16） */
    private static final int TRACK_SRC_W = 4;
    private static final int TRACK_SRC_H = 16;
    /** 滑块源矩形（左半区内 x=5..11, y=0..16 区域，6×16） */
    private static final int THUMB_SRC_W = 6;
    private static final int THUMB_SRC_H = 16;

    // ======================== 状态字段 ========================

    private int scroll;
    private int maxScroll;
    private int totalContent;
    private int visibleContent;

    /** 拖拽滑块中 */
    private boolean dragging;
    private int dragStartY;
    private int dragStartScroll;

    /** 上一帧鼠标是否悬浮在滑块上，用于切换下层高亮贴图 */
    private boolean hovering;

    // ======================== 外观配置 ========================

    private static final int DEFAULT_TRACK_COLOR     = 0x662E3B4C;
    private static final int DEFAULT_THUMB_COLOR     = 0xFF586A80;
    private static final int DEFAULT_THUMB_HOVER_COLOR = 0xFF6A7E96;

    private int trackColor = DEFAULT_TRACK_COLOR;
    private int thumbColor = DEFAULT_THUMB_COLOR;
    private int thumbHoverColor = DEFAULT_THUMB_HOVER_COLOR;
    private int trackWidth = DEFAULT_TRACK_WIDTH;
    private int minThumbSize = MIN_THUMB_SIZE;

    // ======================== 构造 ========================

    public ScrollBar() {
    }

    // ======================== 内容范围管理 ========================

    /**
     * 设置滚动内容范围。
     *
     * @param totalContent   内容总大小（像素/行数均可，用于计算比例）
     * @param visibleContent 可见区域大小（单位与 totalContent 一致）
     */
    public void setContent(int totalContent, int visibleContent) {
        this.totalContent = Math.max(1, totalContent);
        this.visibleContent = Math.max(1, visibleContent);
        this.maxScroll = Math.max(0, this.totalContent - this.visibleContent + 6);
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
    }

    /**
     * 直接设置滚动位置和最大滚动值。
     *
     * @param scroll    当前滚动位置
     * @param maxScroll 最大滚动值（&ge; 0）
     */
    public void setRange(int scroll, int maxScroll) {
        this.maxScroll = Math.max(0, maxScroll);
        this.scroll = Mth.clamp(scroll, 0, this.maxScroll);
        // 从 maxScroll 反向推导比例信息，供滑块大小使用
        this.visibleContent = 1;
        this.totalContent = this.maxScroll + 1;
    }

    // ======================== 访问器 ========================

    public int getScroll() {
        return this.scroll;
    }

    public int getMaxScroll() {
        return this.maxScroll;
    }

    /**
     * 直接设置滚动位置（用于持久化恢复）。
     * <p>不会触发 {@link #setContent} 中的范围限制检查，
     * 但会被下一次 {@link #setContent} 或 {@link #setRange} 钳制到合法范围。</p>
     */
    public void setScroll(int scroll) {
        this.scroll = Math.max(0, scroll);
    }

    public boolean isDragging() {
        return this.dragging;
    }

    /**
     * 是否显示滚动条（内容超出可见区域时需要滚动条）。
     */
    public boolean isVisible() {
        return this.maxScroll > 0;
    }

    // ======================== 事件处理 ========================

    /**
     * 处理鼠标滚轮滚动。
     *
     * @param scrollY 滚轮 delta Y（正值=向上滚动）
     * @return 滚动位置是否发生变化
     */
    public boolean handleScroll(double scrollY) {
        if (this.maxScroll <= 0) return false;
        int before = this.scroll;
        this.scroll = Mth.clamp(this.scroll + (scrollY > 0.0D ? -1 : 1), 0, this.maxScroll);
        return this.scroll != before;
    }

    /**
     * 处理鼠标点击（点击轨道翻页，或开始拖拽滑块）。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param barX   滚动条轨道左上角 X
     * @param barY   滚动条轨道左上角 Y
     * @param barH   滚动条轨道高度
     * @return 是否点击到滚动条区域
     */
    public boolean handleClick(double mouseX, double mouseY, int barX, int barY, int barH) {
        if (this.maxScroll <= 0) return false;
        if (!isInsideBar(mouseX, mouseY, barX, barY, barH)) return false;

        int thumbH = computeThumbH(barH);
        int thumbY = computeThumbY(barY, barH, thumbH);

        if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
            // 点击到滑块：开始拖拽
            this.dragging = true;
            this.dragStartY = (int) mouseY;
            this.dragStartScroll = this.scroll;
        } else {
            // 点击轨道：翻页（滚动约 3/4 可见区域）
            int pageStep = Math.max(1, (this.visibleContent * 3) / 4);
            this.scroll = Mth.clamp(
                    mouseY < thumbY ? this.scroll - pageStep : this.scroll + pageStep,
                    0, this.maxScroll);
        }
        return true;
    }

    /**
     * 处理鼠标拖拽（滑块跟随鼠标移动）。
     *
     * @param mouseY 鼠标 Y
     * @param barY   滚动条轨道左上角 Y
     * @param barH   滚动条轨道高度
     * @return 滚动位置是否发生变化
     */
    public boolean handleDrag(double mouseY, int barY, int barH) {
        if (!this.dragging || this.maxScroll <= 0) return false;

        int thumbH = computeThumbH(barH);
        int availableTrack = barH - thumbH;
        if (availableTrack <= 0) return false;

        int before = this.scroll;
        int deltaY = (int) mouseY - this.dragStartY;
        this.scroll = Mth.clamp(
                this.dragStartScroll + (deltaY * this.maxScroll + availableTrack / 2) / availableTrack,
                0, this.maxScroll);
        return this.scroll != before;
    }

    /**
     * 结束拖拽。
     */
    public void endDrag() {
        this.dragging = false;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染垂直滚动条。
     * <p>使用 {@link RtsClientUiUtil#drawNineSliceRegion} 以九宫格方式绘制滑条和滑块贴图。
     *
     * @param g     GuiGraphics
     * @param barX  滚动条轨道左上角 X
     * @param barY  滚动条轨道左上角 Y
     * @param barH  滚动条轨道高度
     */
    public void render(GuiGraphics g, int barX, int barY, int barH) {
        if (this.maxScroll <= 0) return;

        int thumbH = computeThumbH(barH);
        int thumbY = computeThumbY(barY, barH, thumbH);

        // 激活状态下切换为下层贴图（拖拽或悬停时显示高亮态）
        boolean active = this.dragging || this.hovering;
        int trackSrcY = active ? TRACK_SRC_H : 0;
        int thumbSrcY = active ? THUMB_SRC_H : 0;

        // 滑条（4px 宽，垂直平铺填充）
        RtsClientUiUtil.drawNineSliceRegion(g, SCROLLBAR_TEXTURE,
                barX, barY, TRACK_SRC_W, barH, SCROLLBAR_BORDER,
                SCROLLBAR_TEX_W, SCROLLBAR_TEX_H,
                0, trackSrcY, TRACK_SRC_W, TRACK_SRC_H);

        // 滑块（6px 宽，以滑条为中心左右各凸出 1px）
        RtsClientUiUtil.drawNineSliceRegion(g, SCROLLBAR_TEXTURE,
                barX - 1, thumbY, THUMB_SRC_W, thumbH, SCROLLBAR_BORDER,
                SCROLLBAR_TEX_W, SCROLLBAR_TEX_H,
                5, thumbSrcY, THUMB_SRC_W, THUMB_SRC_H);
    }

    // ======================== 交互区域检测 ========================

    /**
     * 判断鼠标是否在滚动条区域内（匹配贴图渲染宽度 6px）。
     */
    public boolean isInsideBar(double mouseX, double mouseY, int barX, int barY, int barH) {
        return mouseX >= barX - 1
                && mouseX < barX + 5
                && mouseY >= barY
                && mouseY < barY + barH;
    }

    // ======================== 内部计算 ========================

    /**
     * 计算滑块高度：比例与可见/总内容之比成正比，且不小于最小尺寸。
     */
    private int computeThumbH(int barH) {
        int thumbH = barH * this.visibleContent / this.totalContent;
        return Math.max(this.minThumbSize, Math.min(thumbH, barH));
    }

    /**
     * 计算滑块 Y 位置：基于当前滚动比例。
     */
    private int computeThumbY(int barY, int barH, int thumbH) {
        if (this.maxScroll <= 0) return barY;
        return barY + (barH - thumbH) * this.scroll / this.maxScroll;
    }

    // ======================== 外观配置方法 ========================

    public ScrollBar withTrackColor(int color) {
        this.trackColor = color;
        return this;
    }

    public ScrollBar withThumbColor(int color) {
        this.thumbColor = color;
        return this;
    }

    public ScrollBar withThumbHoverColor(int color) {
        this.thumbHoverColor = color;
        return this;
    }

    public ScrollBar withTrackWidth(int width) {
        this.trackWidth = width;
        return this;
    }

    public ScrollBar withMinThumbSize(int size) {
        this.minThumbSize = size;
        return this;
    }

    public int getTrackColor() {
        return trackColor;
    }

    public int getThumbColor() {
        return thumbColor;
    }

    public int getThumbHoverColor() {
        return thumbHoverColor;
    }

    public int getTrackWidth() {
        return trackWidth;
    }
}


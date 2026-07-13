package com.rtsbuilding.rtsbuilding.client.screen.panel.base.component;

import com.rtsbuilding.rtsbuilding.client.util.render.SliderTextureConstants;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 滚动条组件——支持垂直和水平方向，管理滚动状态、渲染轨道/滑块、处理鼠标交互。
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
 * // 设置方向（默认垂直）
 * scrollBar.withOrientation(ScrollBar.Orientation.HORIZONTAL);
 *
 * // 渲染
 * scrollBar.render(g, barX, barY, barLength);
 *
 * // 事件分发
 * scrollBar.handleScroll(scrollX/Y);
 * scrollBar.handleClick(mouseX, mouseY, barX, barY, barLength);
 * scrollBar.handleDrag(mousePos, barStart, barLength);
 * scrollBar.endDrag();
 * }</pre>
 */
public class ScrollBar {

    // ======================== 默认外观常量 ========================

    /** 每次滚轮滚动像素数（默认 4px，手感较快） */
    private static final int DEFAULT_SCROLL_STEP = 4;
    private static final int DEFAULT_TRACK_WIDTH = 2;
    // 滑块比轨道左右各宽 1px（视觉凸出），由 THUMB_W - TRACK_W 推算
    private static final int MIN_THUMB_SIZE = 12;

    /** 渲染时轨道厚度（竖向=宽度，横向=高度，固定 7px） */
    private static final int TRACK_THICKNESS = 7;
    /** 渲染时滑块厚度（竖向=宽度，横向=高度，永远比滑条宽 2px） */
    private static final int THUMB_THICKNESS = TRACK_THICKNESS + 2;
    /** 上边缘往下缩小像素数 */
    private static final int TOP_SHRINK = 1;

    /** 方向枚举 */
    public enum Orientation {
        VERTICAL,
        HORIZONTAL
    }

    // ======================== 状态字段 ========================

    private int scroll;
    private int maxScroll;
    private int totalContent;
    private int visibleContent;

    /** 拖拽滑块中 */
    private boolean dragging;
    private int dragStartPos;
    private int dragStartScroll;

    private Orientation orientation = Orientation.VERTICAL;

    /** 上一帧鼠标是否悬浮在滑块上，用于切换下层高亮贴图 */
    private boolean hovering;

    // ======================== 外观配置 ========================

    private static final int DEFAULT_TRACK_COLOR     = 0x662E3B4C;
    private static final int DEFAULT_THUMB_COLOR     = 0xFF586A80;
    private static final int DEFAULT_THUMB_HOVER_COLOR = 0xFF6A7E96;

    private int scrollStep = DEFAULT_SCROLL_STEP;
    private int trackColor = DEFAULT_TRACK_COLOR;
    private int thumbColor = DEFAULT_THUMB_COLOR;
    private int thumbHoverColor = DEFAULT_THUMB_HOVER_COLOR;
    private int trackWidth = DEFAULT_TRACK_WIDTH;
    private int minThumbSize = MIN_THUMB_SIZE;

    // ======================== 构造 ========================

    public ScrollBar() {
    }

    // ======================== 方向配置 ========================

    /**
     * 设置滚动条方向。
     *
     * @param orientation 方向（VERTICAL=纵向，HORIZONTAL=横向）
     * @return this，便于链式调用
     */
    public ScrollBar withOrientation(Orientation orientation) {
        this.orientation = orientation;
        return this;
    }

    /**
     * 返回当前方向。
     */
    public Orientation getOrientation() {
        return this.orientation;
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
        int step = scrollY > 0.0D ? -this.scrollStep : this.scrollStep;
        this.scroll = Mth.clamp(this.scroll + step, 0, this.maxScroll);
        return this.scroll != before;
    }

    /**
     * 处理鼠标点击（点击轨道翻页，或开始拖拽滑块）。
     *
     * @param mouseX    鼠标 X
     * @param mouseY    鼠标 Y
     * @param barX      轨道左上角 X
     * @param barY      轨道左上角 Y
     * @param barLength 轨道总长度（纵向=高度，横向=宽度）
     * @return 是否点击到滚动条区域
     */
    public boolean handleClick(double mouseX, double mouseY, int barX, int barY, int barLength) {
        if (this.maxScroll <= 0) return false;
        int barYOff = barY + TOP_SHRINK;
        int barLenOff = barLength - TOP_SHRINK;
        if (!isInsideBar(mouseX, mouseY, barX, barYOff, barLenOff)) return false;

        int thumbLen = computeThumbLength(barLenOff);
        int thumbPos = computeThumbPos(barX, barYOff, barLenOff, thumbLen);

        // 根据方向选择使用的鼠标坐标
        double mouseAlong = orientation == Orientation.VERTICAL ? mouseY : mouseX;

        if (mouseAlong >= thumbPos && mouseAlong < thumbPos + thumbLen) {
            // 点击到滑块：开始拖拽
            this.dragging = true;
            this.dragStartPos = (int) mouseAlong;
            this.dragStartScroll = this.scroll;
        } else {
            // 点击轨道：翻页（滚动约 3/4 可见区域）
            int pageStep = Math.max(1, (this.visibleContent * 3) / 4);
            this.scroll = Mth.clamp(
                    mouseAlong < thumbPos ? this.scroll - pageStep : this.scroll + pageStep,
                    0, this.maxScroll);
        }
        return true;
    }

    /**
     * 处理鼠标拖拽（滑块跟随鼠标移动）。
     *
     * @param mousePos  鼠标位置（纵向=鼠标 Y，横向=鼠标 X）
     * @param barPos    轨道起始位置（纵向=barY，横向=barX）
     * @param barLength 轨道总长度（纵向=高度，横向=宽度）
     * @return 滚动位置是否发生变化
     */
    public boolean handleDrag(double mousePos, int barPos, int barLength) {
        if (!this.dragging || this.maxScroll <= 0) return false;

        int thumbLen = computeThumbLength(barLength);
        int availableTrack = barLength - thumbLen;
        if (availableTrack <= 0) return false;

        int before = this.scroll;
        int delta = (int) mousePos - this.dragStartPos;
        this.scroll = Mth.clamp(
                this.dragStartScroll + (delta * this.maxScroll + availableTrack / 2) / availableTrack,
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
     * 渲染滚动条。
     *
     * @param g         GuiGraphics
     * @param barX      轨道左上角 X
     * @param barY      轨道左上角 Y
     * @param barLength 轨道总长度（纵向=高度，横向=宽度）
     */
    public void render(GuiGraphics g, int barX, int barY, int barLength) {
        if (this.maxScroll <= 0) return;

        int renderY = barY + TOP_SHRINK;
        int renderLen = barLength - TOP_SHRINK;
        if (renderLen <= 0) return;
        int thumbLen = computeThumbLength(renderLen);
        int thumbPos = computeThumbPos(barX, renderY, renderLen, thumbLen);

        // 激活状态下切换为下层贴图（拖拽或悬停时显示高亮态）
        boolean active = this.dragging || this.hovering;

        if (orientation == Orientation.VERTICAL) {
            // 纵向滑条（TRACK_THICKNESS px 宽，垂直平铺填充）
            NineSliceRegion track = active ? SliderTextureConstants.TRACK_NINE_SLICE.withVOffset(SliderTextureConstants.STATE_OFFSET) : SliderTextureConstants.TRACK_NINE_SLICE;
            SpriteRenderer.drawNineSlice(g, track.withTheme(), barX, renderY, TRACK_THICKNESS, renderLen);
            // 纵向滑块（THUMB_THICKNESS px 宽，以滑条为中心左右各凸出 1px）
            NineSliceRegion thumb = active ? SliderTextureConstants.THUMB_NINE_SLICE.withVOffset(SliderTextureConstants.STATE_OFFSET) : SliderTextureConstants.THUMB_NINE_SLICE;
            SpriteRenderer.drawNineSlice(g, thumb.withTheme(), barX - 1, thumbPos, THUMB_THICKNESS, thumbLen);
        } else {
            // 横向滑条（TRACK_THICKNESS px 高，水平平铺填充）
            NineSliceRegion track = active ? SliderTextureConstants.TRACK_NINE_SLICE.withVOffset(SliderTextureConstants.STATE_OFFSET) : SliderTextureConstants.TRACK_NINE_SLICE;
            SpriteRenderer.drawNineSlice(g, track.withTheme(), barX, renderY, renderLen, TRACK_THICKNESS);
            // 横向滑块（THUMB_THICKNESS px 高，以滑条为中心上下各凸出 1px）
            NineSliceRegion thumb = active ? SliderTextureConstants.THUMB_NINE_SLICE.withVOffset(SliderTextureConstants.STATE_OFFSET) : SliderTextureConstants.THUMB_NINE_SLICE;
            SpriteRenderer.drawNineSlice(g, thumb.withTheme(), thumbPos, renderY - 1, thumbLen, THUMB_THICKNESS);
        }
    }

    // ======================== 交互区域检测 ========================

    /**
     * 判断鼠标是否在滚动条区域内。
     */
    public boolean isInsideBar(double mouseX, double mouseY, int barX, int barY, int barLength) {
        if (orientation == Orientation.VERTICAL) {
            return mouseX >= barX - 1
                    && mouseX < barX + TRACK_THICKNESS + 1
                    && mouseY >= barY
                    && mouseY < barY + barLength;
        } else {
            return mouseY >= barY - 1
                    && mouseY < barY + TRACK_THICKNESS + 1
                    && mouseX >= barX
                    && mouseX < barX + barLength;
        }
    }

    // ======================== 内部计算 ========================

    /**
     * 计算滑块长度：比例与可见/总内容之比成正比，且不小于最小尺寸。
     */
    private int computeThumbLength(int barLength) {
        int thumbLen = barLength * this.visibleContent / this.totalContent;
        return Math.max(this.minThumbSize, Math.min(thumbLen, barLength));
    }

    /**
     * 计算滑块起始位置：基于当前滚动比例。
     *
     * @param barX      轨道左上角 X（纵向用不到，横向使用）
     * @param barY      轨道左上角 Y（纵向使用，横向用不到）
     * @param barLength 轨道总长度
     * @param thumbLen  滑块长度
     * @return 滑块起始坐标（纵向=Y，横向=X）
     */
    private int computeThumbPos(int barX, int barY, int barLength, int thumbLen) {
        if (this.maxScroll <= 0) return orientation == Orientation.VERTICAL ? barY : barX;
        int barStart = orientation == Orientation.VERTICAL ? barY : barX;
        return barStart + (barLength - thumbLen) * this.scroll / this.maxScroll;
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

    public ScrollBar withScrollStep(int step) {
        this.scrollStep = Math.max(1, step);
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



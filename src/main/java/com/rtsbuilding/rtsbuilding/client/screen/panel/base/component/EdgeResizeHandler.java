package com.rtsbuilding.rtsbuilding.client.screen.panel.base.component;

/**
 * 通用边缘拖拽缩放处理器——统一管理侧边栏/底栏的边缘拖拽缩放状态机。
 *
 * <p>将 {@code RightSidebarResizeHandler}（水平缩放）和
 * {@code DownSidebarResizeHandler}（垂直缩放）的重复代码收归一处。
 * 通过 {@link Orientation} 区分水平/垂直缩放，使用 {@link Side}
 * 区分对边锚定方向。</p>
 *
 * <p>状态机：{@link #tryBegin} → {@link #computeNewSize}（可重复调用）→ {@link #end}。</p>
 *
 * <p>纯逻辑计算，无任何 Minecraft 依赖，可独立单元测试。</p>
 */
public final class EdgeResizeHandler {

    /** 缩放方向 */
    public enum Orientation {
        /** 水平缩放（改变宽度） */
        HORIZONTAL,
        /** 垂直缩放（改变高度） */
        VERTICAL
    }

    /** 拖拽边所在侧（决定 delta 符号） */
    public enum Side {
        /** 左边缘/上边缘：鼠标向正方向移动 → 尺寸减小 */
        LEADING,
        /** 右边缘/下边缘：鼠标向正方向移动 → 尺寸增大 */
        TRAILING
    }

    /** 边缘可点击区域像素宽度/高度 */
    public static final int EDGE_THICKNESS = 5;

    private final Orientation orientation;
    private final Side side;

    /** 是否正在拖拽缩放 */
    private boolean active;
    /** 拖拽起始位置（鼠标 X 或 Y） */
    private int startPos;
    /** 拖拽起始时的目标尺寸（宽度或高度） */
    private int startSize;
    /** 拖拽起始时的屏幕尺寸（宽度或高度，用于限制最大尺寸） */
    private int screenSize;
    /** 最小尺寸限制 */
    private int minSize;
    /** 屏幕比例限制（最大尺寸 = screenSize / maxScreenRatio） */
    private int maxScreenRatio = 4;

    public EdgeResizeHandler(Orientation orientation, Side side) {
        this.orientation = orientation;
        this.side = side;
    }

    public EdgeResizeHandler(Orientation orientation, Side side, int minSize) {
        this(orientation, side);
        this.minSize = minSize;
    }

    // ======================== 配置 ========================

    /** 设置最小尺寸 */
    public EdgeResizeHandler withMinSize(int minSize) {
        this.minSize = minSize;
        return this;
    }

    /** 设置最大尺寸为 screenSize / ratio */
    public EdgeResizeHandler withMaxScreenRatio(int ratio) {
        this.maxScreenRatio = ratio;
        return this;
    }

    // ======================== 状态查询 ========================

    public boolean isActive() { return this.active; }

    public Orientation getOrientation() { return orientation; }

    public Side getSide() { return side; }

    // ======================== 状态机 ========================

    /**
     * 尝试在边缘区域开始缩放。
     *
     * @param mousePos    鼠标在缩放方向上的坐标（mouseX 或 mouseY）
     * @param mouseCross  鼠标在非缩放方向上的坐标
     * @param edgeStart   边缘起始坐标（sidebarX 或 barTop）
     * @param crossStart  交叉方向起始坐标（sidebarY 或 barLeft）
     * @param edgeLength  边缘长度（sidebarH 或 barW）
     * @param currentSize 当前目标尺寸（currentWidth 或 currentHeight）
     * @param screenSize  屏幕尺寸（screenWidth 或 screenHeight）
     * @return true 如果鼠标位于边缘区域且缩放已启动
     */
    public boolean tryBegin(double mousePos, double mouseCross,
                            int edgeStart, int crossStart, int edgeLength,
                            int currentSize, int screenSize) {
        if (mousePos < edgeStart || mousePos >= edgeStart + EDGE_THICKNESS
                || mouseCross < crossStart || mouseCross >= crossStart + edgeLength) {
            return false;
        }
        this.active = true;
        this.startPos = (int) mousePos;
        this.startSize = currentSize;
        this.screenSize = screenSize;
        return true;
    }

    /**
     * 根据鼠标当前位置计算新尺寸（delta 算法）。
     * <p>LEADING 边（左/上）：鼠标向正方向移动 → 尺寸减小。
     * TRAILING 边（右/下）：鼠标向正方向移动 → 尺寸增大。</p>
     *
     * @param mousePos 鼠标当前在缩放方向上的坐标
     * @return 计算后的新尺寸，或在未激活时返回 -1
     */
    public int computeNewSize(double mousePos) {
        if (!this.active) return -1;
        int delta = (int) mousePos - this.startPos;
        int newSize = (this.side == Side.LEADING)
                ? this.startSize - delta
                : this.startSize + delta;
        int maxSize = this.screenSize / this.maxScreenRatio;
        return Math.max(this.minSize, Math.min(maxSize, newSize));
    }

    /** 结束缩放，重置状态 */
    public void end() {
        this.active = false;
    }

    // ======================== 命中检测 ========================

    /**
     * 检测坐标是否在边缘可点击区域上（纯检测，不修改状态）。
     *
     * @param pos        鼠标在缩放方向上的坐标
     * @param cross      鼠标在非缩放方向上的坐标
     * @param edgeStart  边缘起始坐标
     * @param crossStart 交叉方向起始坐标
     * @param edgeLength 边缘长度
     */
    public boolean isOverEdge(int pos, int cross, int edgeStart, int crossStart, int edgeLength) {
        if (this.active) return true;
        return pos >= edgeStart && pos < edgeStart + EDGE_THICKNESS
                && cross >= crossStart && cross < crossStart + edgeLength;
    }
}

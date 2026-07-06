package com.rtsbuilding.rtsbuilding.client.screen.panel.base.util;

/**
 * 面板边界管理器——独立管理窗口位置、尺寸、脏标记和初始化状态。
 *
 * <p>从 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel} 提取，
 * 职责单一：仅维护原始边界数据，不涉及业务约束（如最小/最大尺寸限制）。
 * 约束逻辑由 {@code RtsPanel} 在委托时自行处理。</p>
 */
public final class PanelBounds {

    private int x;
    private int y;
    private int width;
    private int height;
    private int defaultWidth;
    private int defaultHeight;
    private boolean initialized;
    private boolean boundsDirty;
    private boolean userBoundsPreference;

    public PanelBounds(int defaultWidth, int defaultHeight) {
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    // ======================== 位置/尺寸 ========================

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    /** 同时设置位置和尺寸。 */
    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ======================== 初始化状态 ========================

    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean v) { this.initialized = v; }

    /** 尺寸是否未初始化（<=0 表示还未设置过有效尺寸）。 */
    public boolean needsSizeInit() {
        return width <= 0 || height <= 0;
    }

    /** 整体边界是否未初始化。 */
    public boolean needsInit() {
        return !initialized;
    }

    // ======================== 默认值 ========================

    public int getDefaultWidth() { return defaultWidth; }
    public int getDefaultHeight() { return defaultHeight; }

    public void setDefaults(int defaultWidth, int defaultHeight) {
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    /** 将尺寸重置为默认值。 */
    public void resetToDefaults() {
        this.width = this.defaultWidth;
        this.height = this.defaultHeight;
    }

    // ======================== 脏标记 ========================

    /**
     * 消费边界变更标记。
     * @return true 自上次调用后边界发生过变更
     */
    public boolean consumeDirty() {
        boolean dirty = this.boundsDirty;
        this.boundsDirty = false;
        return dirty;
    }

    /** 标记边界为脏（同时标记用户偏好）。 */
    public void markDirty() {
        this.boundsDirty = true;
        this.userBoundsPreference = true;
    }

    /**
     * 标记边界为脏但不标记用户偏好。
     * <p>用于代码自动恢复布局等场景，避免持久化系统覆写用户偏好。</p>
     */
    public void markDirtyTransient() {
        this.boundsDirty = true;
        this.userBoundsPreference = false;
    }

    public boolean hasUserPreference() { return userBoundsPreference; }
    public void clearUserPreference() { this.userBoundsPreference = false; }

    /** 清除脏标记（供外部消费方使用）。 */
    public void clearDirty() { this.boundsDirty = false; }
}

package com.rtsbuilding.rtsbuilding.uicore.topbar;

/** 单个正式顶栏按钮的纯 Java 可见状态。 */
public final class TopBarUiButton {
    public final TopBarUiButtonId id;
    public final boolean visible;
    public final boolean active;

    public TopBarUiButton(TopBarUiButtonId id, boolean visible, boolean active) {
        if (id == null) throw new IllegalArgumentException("id");
        this.id = id;
        this.visible = visible;
        this.active = active;
    }

    public TopBarUiButton withActive(boolean nextActive) {
        return new TopBarUiButton(id, visible, nextActive);
    }
}

package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

/**
 * 单个颜色条目——将颜色源与显示名称关联，用于 {@link ColorGroup}。
 *
 * <p>每个条目包含一个名称（如"主色""边框色"）和对应的 {@link ColorSource}，
 * 在调色盘面板中以色块形式展示，点击即可切换编辑目标。</p>
 */
public class ColorSlot {

    private final String displayName;
    private final ColorSource source;

    public ColorSlot(String displayName, ColorSource source) {
        this.displayName = displayName;
        this.source = source;
    }

    /** 条目的显示名称（如"主色""边框色"） */
    public String displayName() { return displayName; }

    /** 条目对应的颜色源 */
    public ColorSource source() { return source; }
}

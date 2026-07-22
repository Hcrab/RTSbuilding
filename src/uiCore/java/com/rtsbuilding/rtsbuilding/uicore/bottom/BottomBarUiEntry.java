package com.rtsbuilding.rtsbuilding.uicore.bottom;

/**
 * 底部终端里一个可见物品、流体、最近使用项或合成结果。
 *
 * <p>Core 只保存稳定 ID、显示文字和数量；生产适配器仍负责把
 * {@link #sourceIndex} 映射回真实 ItemStack，避免复制或丢失组件数据。</p>
 */
public final class BottomBarUiEntry {
    public enum Kind { STORAGE, CREATIVE, RECENT_ITEM, RECENT_FLUID, FLUID, CRAFTABLE }

    public final Kind kind;
    public final int sourceIndex;
    public final String id;
    public final String label;
    public final long amount;
    public final long capacity;
    public final boolean selected;
    public final boolean available;

    public BottomBarUiEntry(Kind kind, int sourceIndex, String id, String label,
                            long amount, long capacity, boolean selected, boolean available) {
        if (kind == null) throw new IllegalArgumentException("kind");
        this.kind = kind;
        this.sourceIndex = Math.max(0, sourceIndex);
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.amount = Math.max(0L, amount);
        this.capacity = Math.max(0L, capacity);
        this.selected = selected;
        this.available = available;
    }
}

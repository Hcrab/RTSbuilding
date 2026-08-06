package com.rtsbuilding.rtsbuilding.uicore.bottom;

/** 正式工具栏、空手槽、固定槽或 GUI 绑定槽。 */
public final class BottomBarUiToolSlot {
    public enum Kind { HOTBAR, EMPTY_HAND, PINNED, PIN_PAGER, GUI_BINDING }

    public final Kind kind;
    public final int sourceIndex;
    public final String itemId;
    public final String label;
    public final long amount;
    public final boolean selected;
    public final boolean bound;
    public final boolean pending;

    public BottomBarUiToolSlot(Kind kind, int sourceIndex, String itemId,
                               String label, long amount, boolean selected,
                               boolean bound, boolean pending) {
        if (kind == null) throw new IllegalArgumentException("kind");
        this.kind = kind;
        this.sourceIndex = Math.max(0, sourceIndex);
        this.itemId = itemId == null ? "" : itemId;
        this.label = label == null ? "" : label;
        this.amount = Math.max(0L, amount);
        this.selected = selected;
        this.bound = bound;
        this.pending = pending;
    }
}

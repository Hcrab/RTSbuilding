package com.rtsbuilding.rtsbuilding.uicore.funnel;

/** 漏斗缓存中的一条纯显示快照。 */
public final class FunnelUiEntry {
    public final int sourceIndex;
    public final String itemId;
    public final String label;
    public final long count;

    public FunnelUiEntry(int sourceIndex, String itemId, String label, long count) {
        this.sourceIndex = Math.max(0, sourceIndex);
        this.itemId = itemId == null ? "" : itemId;
        this.label = label == null ? "" : label;
        this.count = Math.max(0L, count);
    }
}

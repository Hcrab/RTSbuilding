package com.rtsbuilding.rtsbuilding.uicore.funnel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 漏斗缓存面板纯状态；只携带当前能绘制的有界行。 */
public final class FunnelUiState {
    public final boolean modeActive;
    public final boolean panelVisible;
    public final int totalEntries;
    public final int visibleCapacity;
    public final int hoveredSourceIndex;
    public final List<FunnelUiEntry> visibleEntries;

    public FunnelUiState(boolean modeActive, boolean panelVisible, int totalEntries,
                         int visibleCapacity, int hoveredSourceIndex,
                         List<FunnelUiEntry> visibleEntries) {
        this.modeActive = modeActive;
        this.panelVisible = panelVisible;
        this.totalEntries = Math.max(0, totalEntries);
        this.visibleCapacity = Math.max(1, visibleCapacity);
        this.hoveredSourceIndex = hoveredSourceIndex;
        List<FunnelUiEntry> safe = visibleEntries == null
                ? Collections.<FunnelUiEntry>emptyList() : visibleEntries;
        this.visibleEntries = Collections.unmodifiableList(new ArrayList<FunnelUiEntry>(safe));
    }

    public boolean shouldRender() {
        return modeActive || totalEntries > 0;
    }

    FunnelUiState with(boolean visible, int hovered) {
        return new FunnelUiState(modeActive, visible, totalEntries,
                visibleCapacity, hovered, visibleEntries);
    }
}

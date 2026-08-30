package com.rtsbuilding.rtsbuilding.uicore.funnel;

/** 漏斗缓存的折叠与悬停动作。 */
public final class FunnelUiAction {
    public enum Type { TOGGLE_PANEL, HOVER_ENTRY, CLEAR_HOVER }
    public final Type type;
    public final int sourceIndex;

    private FunnelUiAction(Type type, int sourceIndex) {
        this.type = type;
        this.sourceIndex = sourceIndex;
    }

    public static FunnelUiAction toggle() { return new FunnelUiAction(Type.TOGGLE_PANEL, -1); }
    public static FunnelUiAction hover(int sourceIndex) { return new FunnelUiAction(Type.HOVER_ENTRY, sourceIndex); }
    public static FunnelUiAction clearHover() { return new FunnelUiAction(Type.CLEAR_HOVER, -1); }
}

package com.rtsbuilding.rtsbuilding.uicore.bottom;

/** 底部终端分类树的一行，字段与生产 CategoryRow 一一对应。 */
public final class BottomBarUiCategory {
    public final String token;
    public final String label;
    public final int depth;
    public final boolean expandable;
    public final boolean expanded;
    public final String modNamespace;
    public final boolean selected;

    public BottomBarUiCategory(String token, String label, int depth,
                               boolean expandable, boolean expanded,
                               String modNamespace, boolean selected) {
        this.token = token == null ? "" : token;
        this.label = label == null ? "" : label;
        this.depth = Math.max(0, depth);
        this.expandable = expandable;
        this.expanded = expanded;
        this.modNamespace = modNamespace == null ? "" : modNamespace;
        this.selected = selected;
    }

    public BottomBarUiCategory withExpanded(boolean value) {
        return new BottomBarUiCategory(token, label, depth, expandable, value,
                modNamespace, selected);
    }
}

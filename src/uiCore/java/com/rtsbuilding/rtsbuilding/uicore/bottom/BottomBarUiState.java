package com.rtsbuilding.rtsbuilding.uicore.bottom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 底部终端全部玩家可见状态的纯 Java 快照。
 *
 * <p>它明确区分服务器储存页、客户端创造目录、最近使用、流体、工具槽和
 * 合成结果。预览器不得再自行制造另一套列表；生产端每帧从真实控制器生成
 * 此快照，输入 reducer 也只针对此快照决定命令。</p>
 */
public final class BottomBarUiState {
    public final BottomBarUiTab requestedTab;
    public final BottomBarUiTab activeTab;
    public final boolean creativeAccess;
    public final boolean blueprintAccess;
    public final boolean pluginButtonVisible;
    public final boolean storageLinked;
    public final boolean storageScanning;
    public final boolean refreshHighlighted;
    public final String selectedStatus;
    public final String search;
    public final boolean searchFocused;
    public final int page;
    public final int pageCount;
    public final String sortLabel;
    public final boolean sortAscending;
    public final int panelHeight;
    public final int categoryScroll;
    public final int craftScroll;
    public final int pinPage;
    public final String craftSearchDraft;
    public final String craftSearchApplied;
    public final boolean craftShowUnavailable;
    public final boolean hasMoreCraftables;
    public final List<BottomBarUiCategory> categories;
    public final List<BottomBarUiEntry> storageEntries;
    public final List<BottomBarUiEntry> creativeEntries;
    public final List<BottomBarUiEntry> recentEntries;
    public final List<BottomBarUiEntry> fluidEntries;
    public final List<BottomBarUiEntry> craftableEntries;
    public final List<BottomBarUiToolSlot> toolSlots;
    public final List<BottomBarUiToolSlot> guiBindings;

    private BottomBarUiState(Builder b) {
        this.requestedTab = b.requestedTab;
        this.creativeAccess = b.creativeAccess;
        this.blueprintAccess = b.blueprintAccess;
        this.activeTab = resolveActiveTab(b.requestedTab, b.creativeAccess, b.blueprintAccess);
        this.pluginButtonVisible = b.pluginButtonVisible;
        this.storageLinked = b.storageLinked;
        this.storageScanning = b.storageScanning;
        this.refreshHighlighted = b.refreshHighlighted;
        this.selectedStatus = safe(b.selectedStatus);
        this.search = safe(b.search);
        this.searchFocused = b.searchFocused;
        this.pageCount = Math.max(1, b.pageCount);
        this.page = clamp(b.page, 0, this.pageCount - 1);
        this.sortLabel = safe(b.sortLabel);
        this.sortAscending = b.sortAscending;
        this.panelHeight = Math.max(1, b.panelHeight);
        this.categoryScroll = Math.max(0, b.categoryScroll);
        this.craftScroll = Math.max(0, b.craftScroll);
        this.pinPage = Math.max(0, b.pinPage);
        this.craftSearchDraft = safe(b.craftSearchDraft);
        this.craftSearchApplied = safe(b.craftSearchApplied);
        this.craftShowUnavailable = b.craftShowUnavailable;
        this.hasMoreCraftables = b.hasMoreCraftables;
        this.categories = immutable(b.categories);
        this.storageEntries = immutable(b.storageEntries);
        this.creativeEntries = immutable(b.creativeEntries);
        this.recentEntries = immutable(b.recentEntries);
        this.fluidEntries = immutable(b.fluidEntries);
        this.craftableEntries = immutable(b.craftableEntries);
        this.toolSlots = immutable(b.toolSlots);
        this.guiBindings = immutable(b.guiBindings);
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public boolean craftSearchDirty() {
        return !craftSearchDraft.trim().equals(craftSearchApplied.trim());
    }

    public List<BottomBarUiCategory> visibleCategories(int count) {
        int from = clamp(categoryScroll, 0, Math.max(0, categories.size()));
        int to = Math.min(categories.size(), from + Math.max(0, count));
        return categories.subList(from, to);
    }

    private static BottomBarUiTab resolveActiveTab(BottomBarUiTab tab,
                                                    boolean creative, boolean blueprints) {
        BottomBarUiTab requested = tab == null ? BottomBarUiTab.STORAGE : tab;
        if (requested == BottomBarUiTab.CREATIVE && !creative) return BottomBarUiTab.STORAGE;
        if (requested == BottomBarUiTab.BLUEPRINTS && !blueprints) return BottomBarUiTab.STORAGE;
        return requested;
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null
                ? Collections.<T>emptyList() : values));
    }

    public static final class Builder {
        private BottomBarUiTab requestedTab = BottomBarUiTab.STORAGE;
        private boolean creativeAccess, blueprintAccess, pluginButtonVisible;
        private boolean storageLinked, storageScanning, refreshHighlighted;
        private String selectedStatus = "", search = "", sortLabel = "Qty";
        private boolean searchFocused, sortAscending = true;
        private int page, pageCount = 1, panelHeight = 110;
        private int categoryScroll, craftScroll, pinPage;
        private String craftSearchDraft = "", craftSearchApplied = "";
        private boolean craftShowUnavailable, hasMoreCraftables;
        private List<BottomBarUiCategory> categories = Collections.emptyList();
        private List<BottomBarUiEntry> storageEntries = Collections.emptyList();
        private List<BottomBarUiEntry> creativeEntries = Collections.emptyList();
        private List<BottomBarUiEntry> recentEntries = Collections.emptyList();
        private List<BottomBarUiEntry> fluidEntries = Collections.emptyList();
        private List<BottomBarUiEntry> craftableEntries = Collections.emptyList();
        private List<BottomBarUiToolSlot> toolSlots = Collections.emptyList();
        private List<BottomBarUiToolSlot> guiBindings = Collections.emptyList();

        private Builder() {}
        private Builder(BottomBarUiState s) {
            requestedTab=s.requestedTab; creativeAccess=s.creativeAccess; blueprintAccess=s.blueprintAccess;
            pluginButtonVisible=s.pluginButtonVisible; storageLinked=s.storageLinked;
            storageScanning=s.storageScanning; refreshHighlighted=s.refreshHighlighted;
            selectedStatus=s.selectedStatus; search=s.search; searchFocused=s.searchFocused;
            page=s.page; pageCount=s.pageCount; sortLabel=s.sortLabel; sortAscending=s.sortAscending;
            panelHeight=s.panelHeight; categoryScroll=s.categoryScroll; craftScroll=s.craftScroll;
            pinPage=s.pinPage; craftSearchDraft=s.craftSearchDraft; craftSearchApplied=s.craftSearchApplied;
            craftShowUnavailable=s.craftShowUnavailable; hasMoreCraftables=s.hasMoreCraftables;
            categories=s.categories; storageEntries=s.storageEntries; creativeEntries=s.creativeEntries;
            recentEntries=s.recentEntries; fluidEntries=s.fluidEntries;
            craftableEntries=s.craftableEntries; toolSlots=s.toolSlots; guiBindings=s.guiBindings;
        }

        public Builder requestedTab(BottomBarUiTab v){requestedTab=v;return this;}
        public Builder access(boolean creative,boolean blueprints){creativeAccess=creative;blueprintAccess=blueprints;return this;}
        public Builder pluginButtonVisible(boolean v){pluginButtonVisible=v;return this;}
        public Builder storageStatus(boolean linked,boolean scanning,boolean refresh){storageLinked=linked;storageScanning=scanning;refreshHighlighted=refresh;return this;}
        public Builder selectedStatus(String v){selectedStatus=v;return this;}
        public Builder search(String v,boolean focused){search=v;searchFocused=focused;return this;}
        public Builder page(int v,int count){page=v;pageCount=count;return this;}
        public Builder sort(String label,boolean ascending){sortLabel=label;sortAscending=ascending;return this;}
        public Builder panelHeight(int v){panelHeight=v;return this;}
        public Builder viewScroll(int category,int craft,int pin){categoryScroll=category;craftScroll=craft;pinPage=pin;return this;}
        public Builder craftSearch(String draft,String applied){craftSearchDraft=draft;craftSearchApplied=applied;return this;}
        public Builder craftFlags(boolean unavailable,boolean more){craftShowUnavailable=unavailable;hasMoreCraftables=more;return this;}
        public Builder categories(List<BottomBarUiCategory> v){categories=v;return this;}
        public Builder storageEntries(List<BottomBarUiEntry> v){storageEntries=v;return this;}
        public Builder creativeEntries(List<BottomBarUiEntry> v){creativeEntries=v;return this;}
        public Builder recentEntries(List<BottomBarUiEntry> v){recentEntries=v;return this;}
        public Builder fluidEntries(List<BottomBarUiEntry> v){fluidEntries=v;return this;}
        public Builder craftableEntries(List<BottomBarUiEntry> v){craftableEntries=v;return this;}
        public Builder toolSlots(List<BottomBarUiToolSlot> v){toolSlots=v;return this;}
        public Builder guiBindings(List<BottomBarUiToolSlot> v){guiBindings=v;return this;}
        public BottomBarUiState build(){return new BottomBarUiState(this);}
    }
}

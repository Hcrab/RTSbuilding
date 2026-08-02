package com.rtsbuilding.rtsbuilding.uicore.craft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 合成数量窗的纯 UI 状态；物品堆与网络请求不进入 Core。 */
public final class CraftQuantityState {
    public static final int MAX_COUNT = 999;
    public final boolean open;
    public final String itemLabel;
    public final String itemId;
    public final List<CraftQuantityOption> options;
    public final int selectedIndex;
    public final int scroll;
    public final int visibleRows;
    public final int quantity;
    public final boolean replaceOnNextDigit;

    public CraftQuantityState(boolean open, String itemLabel, String itemId,
                              List<CraftQuantityOption> options, int selectedIndex,
                              int scroll, int visibleRows, int quantity,
                              boolean replaceOnNextDigit) {
        this.open = open;
        this.itemLabel = safe(itemLabel);
        this.itemId = safe(itemId);
        List<CraftQuantityOption> safeOptions = options == null
                ? Collections.<CraftQuantityOption>emptyList() : options;
        this.options = Collections.unmodifiableList(new ArrayList<CraftQuantityOption>(safeOptions));
        this.visibleRows = Math.max(1, visibleRows);
        this.selectedIndex = clamp(selectedIndex, 0, Math.max(0, this.options.size() - 1));
        this.scroll = ensureVisible(this.selectedIndex, scroll, this.visibleRows, this.options.size());
        this.quantity = clamp(quantity, 1, MAX_COUNT);
        this.replaceOnNextDigit = replaceOnNextDigit;
    }

    public CraftQuantityOption selected() {
        return options.isEmpty() ? null : options.get(selectedIndex);
    }

    public boolean canConfirm() {
        CraftQuantityOption selected = selected();
        return open && selected != null && selected.craftable
                && !selected.recipeId.trim().isEmpty() && quantity > 0;
    }

    public int maxScroll() {
        return Math.max(0, options.size() - visibleRows);
    }

    CraftQuantityState with(int nextSelected, int nextScroll, int nextQuantity,
                            boolean nextReplace, boolean nextOpen) {
        return new CraftQuantityState(nextOpen, itemLabel, itemId, options,
                nextSelected, nextScroll, visibleRows, nextQuantity, nextReplace);
    }

    private static int ensureVisible(int selected, int requested, int visible, int total) {
        int next = clamp(requested, 0, Math.max(0, total - visible));
        if (selected < next) next = selected;
        else if (selected >= next + visible) next = selected - visible + 1;
        return clamp(next, 0, Math.max(0, total - visible));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

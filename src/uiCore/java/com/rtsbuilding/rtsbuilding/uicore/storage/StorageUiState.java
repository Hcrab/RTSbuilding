package com.rtsbuilding.rtsbuilding.uicore.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 储存绑定详情的有界可见窗口快照。 */
public final class StorageUiState {
    public final boolean open;
    public final StorageUiStatus status;
    public final int totalRows;
    public final int scroll;
    public final int visibleRowCapacity;
    public final List<StorageUiEntry> visibleEntries;
    public final String errorMessage;

    public StorageUiState(boolean open, StorageUiStatus status, int totalRows, int scroll,
            int visibleRowCapacity, List<StorageUiEntry> visibleEntries, String errorMessage) {
        this.open = open;
        this.status = status == null ? StorageUiStatus.EMPTY : status;
        this.totalRows = Math.max(0, totalRows);
        this.visibleRowCapacity = Math.max(1, visibleRowCapacity);
        this.scroll = Math.max(0, Math.min(scroll, maxScroll()));
        this.visibleEntries = Collections.unmodifiableList(new ArrayList<StorageUiEntry>(
                visibleEntries == null ? Collections.<StorageUiEntry>emptyList() : visibleEntries));
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public int maxScroll() { return Math.max(0, totalRows - visibleRowCapacity); }
    public boolean hasScrollbar() { return maxScroll() > 0; }
    public StorageUiEntry visibleEntry(int row) {
        return row < 0 || row >= visibleEntries.size() ? null : visibleEntries.get(row);
    }

    StorageUiState withScroll(int next) {
        return new StorageUiState(open, status, totalRows, next, visibleRowCapacity,
                visibleEntries, errorMessage);
    }
    StorageUiState withEntry(String key, Integer priority, boolean toggleExtract, boolean remove) {
        List<StorageUiEntry> next = new ArrayList<StorageUiEntry>(visibleEntries);
        for (int i = 0; i < next.size(); i++) {
            StorageUiEntry entry = next.get(i);
            if (!entry.stableKey.equals(key)) continue;
            if (remove) next.remove(i);
            else if (priority != null) next.set(i, entry.withPriority(priority.intValue()));
            else if (toggleExtract) next.set(i, entry.toggledExtract());
            break;
        }
        int count = remove ? Math.max(0, totalRows - 1) : totalRows;
        StorageUiStatus nextStatus = count == 0 ? StorageUiStatus.EMPTY : status;
        return new StorageUiState(open, nextStatus, count, scroll, visibleRowCapacity,
                next, errorMessage);
    }
}

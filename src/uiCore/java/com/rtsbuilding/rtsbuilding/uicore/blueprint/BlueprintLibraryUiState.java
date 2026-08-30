package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 底部“蓝图空间”的列表、搜索、选中、捕获锁定和状态行快照。 */
public final class BlueprintLibraryUiState {
    public final List<BlueprintLibraryUiEntry> entries;
    public final String query;
    public final boolean searchFocused;
    public final int scrollRows;
    public final String selectedFileName;
    public final boolean captureLocked;
    public final boolean captureSaving;
    public final String status;
    public final int statusColor;

    public BlueprintLibraryUiState(List<BlueprintLibraryUiEntry> entries, String query,
                                   boolean searchFocused, int scrollRows, String selectedFileName,
                                   boolean captureLocked, boolean captureSaving,
                                   String status, int statusColor) {
        this.entries = Collections.unmodifiableList(new ArrayList<BlueprintLibraryUiEntry>(
                entries == null ? Collections.<BlueprintLibraryUiEntry>emptyList() : entries));
        this.query = safe(query);
        this.searchFocused = searchFocused;
        this.scrollRows = Math.max(0, scrollRows);
        this.selectedFileName = safe(selectedFileName);
        this.captureLocked = captureLocked;
        this.captureSaving = captureSaving;
        this.status = safe(status);
        this.statusColor = statusColor;
    }

    public List<BlueprintLibraryUiEntry> filteredEntries() {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return entries;
        List<BlueprintLibraryUiEntry> filtered = new ArrayList<BlueprintLibraryUiEntry>();
        for (BlueprintLibraryUiEntry entry : entries) {
            if (entry.name.toLowerCase(Locale.ROOT).contains(normalized)
                    || entry.fileName.toLowerCase(Locale.ROOT).contains(normalized)
                    || entry.format.toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(entry);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public BlueprintLibraryUiEntry selectedEntry() {
        for (BlueprintLibraryUiEntry entry : entries) {
            if (entry.fileName.equals(selectedFileName)) return entry;
        }
        return null;
    }

    public BlueprintLibraryUiState withQuery(String next) {
        return copy(next, searchFocused, 0, selectedFileName, captureLocked, captureSaving);
    }

    public BlueprintLibraryUiState withSearchFocused(boolean focused) {
        return copy(query, focused, scrollRows, selectedFileName, captureLocked, captureSaving);
    }

    public BlueprintLibraryUiState withScrollRows(int rows) {
        return copy(query, searchFocused, Math.max(0, rows), selectedFileName, captureLocked, captureSaving);
    }

    public BlueprintLibraryUiState withSelectedFileName(String fileName) {
        return copy(query, false, scrollRows, fileName, captureLocked, captureSaving);
    }

    public BlueprintLibraryUiState withCaptureLocked(boolean locked) {
        return copy(query, false, scrollRows, selectedFileName, locked, captureSaving && locked);
    }

    private BlueprintLibraryUiState copy(String nextQuery, boolean focused, int scroll,
                                         String selected, boolean locked, boolean saving) {
        return new BlueprintLibraryUiState(entries, nextQuery, focused, scroll, selected,
                locked, saving, status, statusColor);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

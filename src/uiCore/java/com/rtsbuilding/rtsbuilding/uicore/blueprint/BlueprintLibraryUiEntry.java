package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 底部“蓝图空间”列表的一行纯 Java 快照。 */
public final class BlueprintLibraryUiEntry {
    public final String fileName;
    public final String name;
    public final String format;
    public final String size;
    public final int blockCount;
    public final int buildPercent;
    public final String materialSummary;
    public final String error;
    public final List<String> previewItemIds;

    public BlueprintLibraryUiEntry(String fileName, String name, String format, String size,
                                   int blockCount, int buildPercent, String materialSummary,
                                   String error, List<String> previewItemIds) {
        this.fileName = safe(fileName);
        this.name = safe(name);
        this.format = safe(format);
        this.size = safe(size);
        this.blockCount = Math.max(0, blockCount);
        this.buildPercent = Math.max(0, Math.min(100, buildPercent));
        this.materialSummary = safe(materialSummary);
        this.error = safe(error);
        this.previewItemIds = Collections.unmodifiableList(new ArrayList<String>(
                previewItemIds == null ? Collections.<String>emptyList() : previewItemIds));
    }

    public boolean valid() {
        return error.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

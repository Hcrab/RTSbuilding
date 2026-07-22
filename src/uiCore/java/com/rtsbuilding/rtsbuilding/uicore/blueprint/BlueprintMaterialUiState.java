package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 蓝图材料详情窗的纯 Java 快照；图标只保存正式注册表 id，不持有 Minecraft 对象。 */
public final class BlueprintMaterialUiState {
    public static final BlueprintMaterialUiState EMPTY = new BlueprintMaterialUiState(
            "", 100, 0, 0, 0, 0, 0, Collections.<Row>emptyList());

    public final String blueprintName;
    public final int percent;
    public final int buildable;
    public final int total;
    public final int missingTypes;
    public final int unsupportedTypes;
    public final int missingBlockTypes;
    public final List<Row> rows;

    public BlueprintMaterialUiState(String blueprintName, int percent, int buildable, int total,
                                    int missingTypes, int unsupportedTypes, int missingBlockTypes,
                                    List<Row> rows) {
        this.blueprintName = blueprintName == null ? "" : blueprintName;
        this.percent = Math.max(0, Math.min(100, percent));
        this.buildable = Math.max(0, buildable);
        this.total = Math.max(0, total);
        this.missingTypes = Math.max(0, missingTypes);
        this.unsupportedTypes = Math.max(0, unsupportedTypes);
        this.missingBlockTypes = Math.max(0, missingBlockTypes);
        this.rows = Collections.unmodifiableList(new ArrayList<Row>(
                rows == null ? Collections.<Row>emptyList() : rows));
    }

    public boolean allReady() {
        return rows.isEmpty() || percent >= 100;
    }

    public static final class Row {
        public final String iconId;
        public final String label;
        public final String detail;
        public final int color;

        public Row(String iconId, String label, String detail, int color) {
            this.iconId = iconId == null ? "" : iconId;
            this.label = label == null ? "" : label;
            this.detail = detail == null ? "" : detail;
            this.color = color;
        }
    }
}

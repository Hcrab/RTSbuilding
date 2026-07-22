package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiRow;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiSection;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 设置窗正式行高、分类间距和滚动范围的共享布局。 */
public final class SettingsWindowLayout {
    public static final int CONTENT_TOP_PADDING = 8;
    public static final int SECTION_HEADER_H = 22;
    public static final int SECTION_GAP = 6;
    public static final int SENSITIVITY_ROW_H = 46;
    public static final int SCALE_ROW_H = 34;
    public static final int SOUND_LIMIT_ROW_H = 38;
    public static final int SIMPLE_TOGGLE_ROW_H = 28;
    public static final int HINT_TOGGLE_ROW_H = 34;
    public static final int HINT_LINE_H = 10;
    public static final int HINT_EXPAND_BUTTON_SIZE = 12;

    private SettingsWindowLayout() {
    }

    public interface HintLineCounter {
        int lineCount(SettingsUiRow row);
    }

    public static Layout layout(SettingsUiState state, int contentX, int contentY,
                                int contentWidth, HintLineCounter hintLines) {
        List<Node> nodes = new ArrayList<Node>();
        int y = contentY + CONTENT_TOP_PADDING;
        for (int sectionIndex = 0; sectionIndex < state.sections.size(); sectionIndex++) {
            SettingsUiSection section = state.sections.get(sectionIndex);
            nodes.add(Node.section(section, contentX, y, contentWidth, SECTION_HEADER_H));
            y += SECTION_HEADER_H;
            if (section.expanded) {
                for (SettingsUiRow row : section.rows) {
                    int lines = hintLines == null ? 1 : Math.max(1, hintLines.lineCount(row));
                    int height = rowHeight(row, lines);
                    nodes.add(Node.row(row, contentX, y, contentWidth, height));
                    y += height;
                }
            }
            if (sectionIndex + 1 < state.sections.size()) y += SECTION_GAP;
        }
        return new Layout(nodes, Math.max(0, y - contentY));
    }

    public static int rowHeight(SettingsUiRow row, int expandedHintLines) {
        if (row.id.kind == SettingsRowKind.SENSITIVITY) return SENSITIVITY_ROW_H;
        if (row.id.kind == SettingsRowKind.SIMPLE_TOGGLE) return SIMPLE_TOGGLE_ROW_H;
        if (row.id.kind == SettingsRowKind.STEP_VALUE) {
            return row.id == SettingsId.BLOCK_SOUNDS_PER_TICK
                    ? SOUND_LIMIT_ROW_H : SCALE_ROW_H;
        }
        if (row.hintExpanded) {
            return Math.max(HINT_TOGGLE_ROW_H,
                    18 + Math.max(1, expandedHintLines) * HINT_LINE_H);
        }
        return HINT_TOGGLE_ROW_H;
    }

    public static int maxScroll(Layout layout, int viewportHeight) {
        return Math.max(0, layout.contentHeight + CONTENT_TOP_PADDING
                - Math.max(0, viewportHeight));
    }

    public static final class Layout {
        public final List<Node> nodes;
        public final int contentHeight;

        private Layout(List<Node> nodes, int contentHeight) {
            this.nodes = Collections.unmodifiableList(new ArrayList<Node>(nodes));
            this.contentHeight = contentHeight;
        }
    }

    public static final class Node {
        public final SettingsUiSection section;
        public final SettingsUiRow row;
        public final int x, y, width, height;

        private Node(SettingsUiSection section, SettingsUiRow row,
                     int x, int y, int width, int height) {
            this.section = section;
            this.row = row;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private static Node section(SettingsUiSection section, int x, int y, int width, int height) {
            return new Node(section, null, x, y, width, height);
        }

        private static Node row(SettingsUiRow row, int x, int y, int width, int height) {
            return new Node(null, row, x, y, width, height);
        }

        public boolean isSection() {
            return section != null;
        }
    }
}

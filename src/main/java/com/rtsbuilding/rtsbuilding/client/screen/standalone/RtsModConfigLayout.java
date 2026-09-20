package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 设置页的纯布局结果。
 *
 * <p>控件、标签和裁剪区域必须消费同一组行坐标；这里不持有 Minecraft 控件，也不
 * 负责滚动或保存，因此可以在屏幕重建和离线行为测试中复用同一计算。</p>
 */
final class RtsModConfigLayout {
    static final int OPTION_ROW_H = 40;
    static final int GROUP_H = 24;
    static final int GROUP_GAP = 6;

    private RtsModConfigLayout() {
    }

    static WorldLayout world(int startY) {
        List<GroupLayout> groups = new ArrayList<>();
        int y = startY;
        for (RtsServerConfigGroup group : RtsServerConfigGroup.values()) {
            int headerY = y;
            y += GROUP_H;
            List<RowLayout> rows = new ArrayList<>();
            for (RtsServerConfigField field : RtsServerConfigField.values()) {
                if (field.group != group) {
                    continue;
                }
                rows.add(new RowLayout(field, y));
                y += OPTION_ROW_H;
            }
            groups.add(new GroupLayout(group, headerY, rows));
            y += GROUP_GAP;
        }
        return new WorldLayout(groups, y - startY);
    }

    static int personalContentHeight(int fieldCount) {
        return GROUP_H + OPTION_ROW_H * Math.max(0, fieldCount);
    }

    static final class WorldLayout {
        final List<GroupLayout> groups;
        final int contentHeight;

        private WorldLayout(List<GroupLayout> groups, int contentHeight) {
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
            this.contentHeight = contentHeight;
        }
    }

    static final class GroupLayout {
        final RtsServerConfigGroup group;
        final int headerY;
        final List<RowLayout> rows;

        private GroupLayout(RtsServerConfigGroup group, int headerY, List<RowLayout> rows) {
            this.group = group;
            this.headerY = headerY;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }
    }

    static final class RowLayout {
        final RtsServerConfigField field;
        final int y;

        private RowLayout(RtsServerConfigField field, int y) {
            this.field = field;
            this.y = y;
        }
    }
}

package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 设置页生产布局的逐组/逐行坐标回归；不启动 Minecraft 客户端。 */
final class RtsModConfigLayoutTest {
    @Test
    void controlsAndLabelsShareEveryGroupRowCoordinate() {
        int startY = 100;
        RtsModConfigLayout.WorldLayout layout = RtsModConfigLayout.world(startY);
        int expectedY = startY;
        int rowCount = 0;
        for (RtsModConfigLayout.GroupLayout group : layout.groups) {
            assertEquals(expectedY, group.headerY);
            expectedY += RtsModConfigLayout.GROUP_H;
            for (RtsModConfigLayout.RowLayout row : group.rows) {
                assertEquals(expectedY, row.y);
                expectedY += RtsModConfigLayout.OPTION_ROW_H;
                rowCount++;
            }
            expectedY += RtsModConfigLayout.GROUP_GAP;
        }
        assertEquals(RtsServerConfigGroup.values().length, layout.groups.size());
        assertEquals(RtsServerConfigField.values().length, rowCount);
        assertEquals(expectedY - startY, layout.contentHeight);
    }

    @Test
    void scrollingMovesTheSharedLayoutWithoutChangingRowOrder() {
        RtsModConfigLayout.WorldLayout top = RtsModConfigLayout.world(100);
        RtsModConfigLayout.WorldLayout scrolled = RtsModConfigLayout.world(76);
        assertEquals(top.groups.get(1).headerY - 24, scrolled.groups.get(1).headerY);
        assertEquals(top.groups.get(1).rows.get(0).y - 24,
                scrolled.groups.get(1).rows.get(0).y);
        assertEquals(top.contentHeight, scrolled.contentHeight);
    }
}

package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class CraftQuantityStyleTest {
    @Test
    void 配方行徽章与详情按业务状态解析() {
        assertSame(CraftQuantityStyle.CRAFTABLE_ROW,
                CraftQuantityStyle.rowBackground(true, false));
        assertSame(CraftQuantityStyle.MISSING_ROW_SELECTED,
                CraftQuantityStyle.rowBackground(false, true));
        assertSame(CraftQuantityStyle.CRAFTABLE_BADGE,
                CraftQuantityStyle.badge(true));
        assertSame(CraftQuantityStyle.DETAIL_MISSING,
                CraftQuantityStyle.detail(true));
    }
}

package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.layout.CategoryTypes;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelCategoryBuilderTest {
    @Test
    void 选中的子分类会自动展开父模组且保持深度() {
        Set<String> expanded = new HashSet<>();
        List<RtsCreativeItemCatalog.CreativeCategory> categories = Arrays.asList(
                new RtsCreativeItemCatalog.CreativeCategory("all", "All", 0, false, ""),
                new RtsCreativeItemCatalog.CreativeCategory("mod|demo", "Demo", 0, true, "demo"),
                new RtsCreativeItemCatalog.CreativeCategory(
                        "tab|demo|demo:building", "Building", 1, false, "demo"));

        List<CategoryTypes.CategoryRow> rows = BottomPanelCategoryBuilder.creativeRows(
                "tab|demo|demo:building", expanded, "全部", categories);

        assertTrue(expanded.contains("demo"));
        assertEquals(3, rows.size());
        assertEquals("全部", rows.get(0).label());
        assertEquals(1, rows.get(2).depth());
    }

    @Test
    void 储存分类去除坏token并让原版模组排在最前() {
        Set<String> expanded = new HashSet<>();
        List<CategoryTypes.CategoryRow> rows = BottomPanelCategoryBuilder.storageRows(
                Arrays.asList(
                        "mod|other_mod",
                        "mod|minecraft",
                        "tab|broken",
                        "all"),
                "all",
                expanded,
                "All");

        assertEquals("all", rows.get(0).token());
        assertEquals("mod|minecraft", rows.get(1).token());
        assertEquals("mod|other_mod", rows.get(2).token());
    }

    @Test
    void 回退标签只处理显示字符串不接触面板状态() {
        assertEquals("all", BottomPanelCategoryBuilder.normalizeToken("  ALL "));
        assertEquals("Other Mod", BottomPanelCategoryBuilder.humanizeToken("other_mod"));
        assertEquals("", BottomPanelCategoryBuilder.humanizeToken(null));
    }
}

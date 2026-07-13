package com.rtsbuilding.rtsbuilding.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPinyinSearchTest {
    @Test
    void dictionaryResourceIsPackagedForRuntimeSearch() throws Exception {
        try (var in = RtsPinyinSearch.class.getResourceAsStream("/assets/rtsbuilding/pinyin/data.txt")) {
            assertNotNull(in, "拼音字典资源缺失会导致中文物品名无法用拼音搜索");
            assertTrue(in.readAllBytes().length > 100_000, "拼音字典资源异常偏小，可能被误删或截断");
        }
    }

    @Test
    void matchesFullPinyinAndInitialsForChineseLabels() {
        assertTrue(RtsPinyinSearch.contains("橡木木板", "xiangmu"));
        assertTrue(RtsPinyinSearch.contains("橡木木板", "muban"));
        assertTrue(RtsPinyinSearch.contains("橡木木板", "xmmb"));
        assertFalse(RtsPinyinSearch.contains("橡木木板", "stone"));
    }

    @Test
    void ignoresPinyinModeForPureEnglishLabels() {
        assertFalse(RtsPinyinSearch.contains("Oak Planks", "oak"));
        assertFalse(RtsPinyinSearch.contains("", "xiangmu"));
        assertFalse(RtsPinyinSearch.contains("橡木木板", ""));
    }
}

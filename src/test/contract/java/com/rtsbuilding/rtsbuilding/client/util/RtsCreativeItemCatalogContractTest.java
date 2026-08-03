package com.rtsbuilding.rtsbuilding.client.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCreativeItemCatalogContractTest {

    @Test
    void creativeTabsAreBuiltBeforeAnyVisibilityDecision() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/util/RtsCreativeItemCatalog.java"));
        int loop = source.indexOf("for (CreativeModeTab tab : RtsBuiltInRegistries.CREATIVE_MODE_TAB)");
        int build = source.indexOf("Collection<ItemStack> displayItems = safeDisplayItems(tab)", loop);
        int shouldDisplay = source.indexOf("tab.shouldDisplay()", loop);

        assertTrue(loop >= 0 && build > loop);
        assertTrue(shouldDisplay < 0 || shouldDisplay > build,
                "创造标签必须先装填内容，不能在装填前按可见性过滤");
        assertTrue(source.contains("tab.fillItemList(items)"),
                "1.19.2 必须通过 fillItemList 装填创造标签内容");
    }
}

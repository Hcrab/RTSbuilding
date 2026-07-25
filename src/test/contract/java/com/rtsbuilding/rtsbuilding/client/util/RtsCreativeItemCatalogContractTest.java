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
        int loop = source.indexOf("for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB)");
        int build = source.indexOf("buildContentsIfPossible(tab, parameters)", loop);
        int shouldDisplay = source.indexOf("tab.shouldDisplay()", loop);

        assertTrue(loop >= 0 && build > loop);
        assertTrue(shouldDisplay < 0 || shouldDisplay > build,
                "1.21.1 的 shouldDisplay 依赖已装填内容，不能在 buildContents 之前过滤创造标签");
    }
}

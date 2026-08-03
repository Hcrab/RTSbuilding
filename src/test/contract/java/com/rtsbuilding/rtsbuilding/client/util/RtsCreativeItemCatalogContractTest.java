package com.rtsbuilding.rtsbuilding.client.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCreativeItemCatalogContractTest {

    @Test
    void 一点十二创造标签必须真实收集物品且保留子类型数据() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/util/RtsCreativeItemCatalog.java")),
                StandardCharsets.UTF_8);
        int loop = source.indexOf("for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY)");
        int collect = source.indexOf("safeDisplayItems(tab)", loop);
        int metadata = source.indexOf("preview.getMetadata()", collect);
        int nbt = source.indexOf("preview.getTagCompound()", metadata);

        assertTrue(loop >= 0 && collect > loop,
                "1.12 必须通过 CreativeTabs 正式入口收集每个标签的物品");
        assertTrue(source.contains("tab.displayAllRelevantItems(stacks)"));
        assertTrue(metadata > collect && nbt > metadata,
                "创造目录去重必须保留同注册名物品的 metadata 与 NBT 子类型");
    }
}

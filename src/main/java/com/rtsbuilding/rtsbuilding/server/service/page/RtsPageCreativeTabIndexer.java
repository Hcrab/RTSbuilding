package com.rtsbuilding.rtsbuilding.server.service.page;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages the creative-mode-tab index used to populate storage-browser category chips.
 */
public final class RtsPageCreativeTabIndexer {

    private static final ConcurrentMap<String, java.util.Set<String>> ITEM_CREATIVE_TAB_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean creativeTabCacheWarmNormal;
    private static volatile boolean creativeTabCacheWarmOperator;

    private RtsPageCreativeTabIndexer() {
    }

    public static void warmCreativeTabCacheMode(ServerLevel level, boolean operatorTabs) {
        if (isCreativeTabCacheWarm(operatorTabs)) {
            return;
        }
        indexAvailableCreativeTabContents(operatorTabs);
        setCreativeTabCacheWarm(operatorTabs);
    }

    public static void clearCreativeTabCacheState() {
        ITEM_CREATIVE_TAB_CACHE.clear();
        creativeTabCacheWarmNormal = false;
        creativeTabCacheWarmOperator = false;
    }

    static boolean ensureCreativeTabContents(ServerPlayer player) {
        boolean operatorTabs = player.canUseGameMasterBlocks();
        if (isCreativeTabCacheWarm(operatorTabs)) {
            return true;
        }
        synchronized (RtsPageCreativeTabIndexer.class) {
            if (isCreativeTabCacheWarm(operatorTabs)) {
                return true;
            }
            warmCreativeTabCacheMode(player.serverLevel(), operatorTabs);
            return true;
        }
    }

    static java.util.Set<String> resolveCreativeTabKeys(String itemId, Item item, boolean operatorTabs) {
        java.util.Set<String> tabKeys = ITEM_CREATIVE_TAB_CACHE.get(creativeTabItemCacheKey(itemId, operatorTabs));
        return tabKeys == null ? java.util.Set.of() : tabKeys;
    }

    static void buildItemTabMapping(
            Map<String, Long> counts,
            Map<String, java.util.Set<String>> itemTabKeys,
            Map<String, java.util.Set<String>> modTabKeys,
            boolean operatorTabs) {
        if (counts.isEmpty()) {
            return;
        }
        for (String itemId : counts.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            java.util.Set<String> tabs = resolveCreativeTabKeys(itemId, item, operatorTabs);
            if (tabs.isEmpty()) {
                continue;
            }
            java.util.Set<String> copied = new java.util.HashSet<>(tabs);
            itemTabKeys.put(itemId, copied);
            modTabKeys.computeIfAbsent(rl.getNamespace(), ignored -> new java.util.HashSet<>()).addAll(copied);
        }
    }

    // ---- internals -------------------------------------------------------------

    private static boolean isCreativeTabCacheWarm(boolean operatorTabs) {
        return operatorTabs ? creativeTabCacheWarmOperator : creativeTabCacheWarmNormal;
    }

    private static void setCreativeTabCacheWarm(boolean operatorTabs) {
        if (operatorTabs) {
            creativeTabCacheWarmOperator = true;
        } else {
            creativeTabCacheWarmNormal = true;
        }
    }

    /**
     * 只索引游戏已经构建好的创造栏快照，不在服务端重新派发客户端创造栏事件。
     */
    private static void indexAvailableCreativeTabContents(boolean operatorTabs) {
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            indexCreativeTabContents(tab, key, operatorTabs);
        }
    }

    private static void indexCreativeTabContents(CreativeModeTab tab, ResourceLocation key, boolean operatorTabs) {
        if (key == null || !tab.shouldDisplay()) {
            return;
        }
        String tabKey = key.toString();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) {
                continue;
            }
            ITEM_CREATIVE_TAB_CACHE.compute(
                    creativeTabItemCacheKey(itemId.toString(), operatorTabs),
                    (ignored, existing) -> {
                        java.util.Set<String> tabs = existing == null ? ConcurrentHashMap.newKeySet() : existing;
                        tabs.add(tabKey);
                        return tabs;
                    });
        }
    }

    private static String creativeTabItemCacheKey(String itemId, boolean operatorTabs) {
        return (operatorTabs ? "op|" : "normal|") + itemId;
    }
}

package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
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
    private static final java.util.Set<String> BROKEN_CREATIVE_TAB_CACHE = ConcurrentHashMap.newKeySet();
    private static volatile boolean creativeTabCacheWarmNormal;
    private static volatile boolean creativeTabCacheWarmOperator;

    private RtsPageCreativeTabIndexer() {
    }

    public static void warmCreativeTabCacheMode(ServerLevel level, boolean operatorTabs) {
        if (isCreativeTabCacheWarm(operatorTabs)) {
            return;
        }
        rebuildCreativeTabContentsSafely(level, operatorTabs);
        setCreativeTabCacheWarm(operatorTabs);
    }

    public static void clearCreativeTabCacheState() {
        ITEM_CREATIVE_TAB_CACHE.clear();
        BROKEN_CREATIVE_TAB_CACHE.clear();
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

    private static void rebuildCreativeTabContentsSafely(ServerLevel level, boolean operatorTabs) {
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), operatorTabs, level.registryAccess());
        rebuildCreativeTabContentsSafely(parameters, operatorTabs, true);
        rebuildCreativeTabContentsSafely(parameters, operatorTabs, false);
    }

    private static void rebuildCreativeTabContentsSafely(
            CreativeModeTab.ItemDisplayParameters parameters, boolean operatorTabs, boolean categoryTabs) {
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null) {
                continue;
            }
            boolean category = tab.getType() == CreativeModeTab.Type.CATEGORY;
            if (category != categoryTabs) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (isBrokenCreativeTab(key, operatorTabs)) {
                continue;
            }
            try {
                tab.buildContents(parameters);
                if (category) {
                    indexCreativeTabContents(tab, key, operatorTabs);
                }
            } catch (RuntimeException | LinkageError ex) {
                markBrokenCreativeTab(key, operatorTabs, ex);
            }
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

    private static boolean isBrokenCreativeTab(ResourceLocation key, boolean operatorTabs) {
        return BROKEN_CREATIVE_TAB_CACHE.contains(creativeTabModeKey(key, operatorTabs));
    }

    private static void markBrokenCreativeTab(ResourceLocation key, boolean operatorTabs, Throwable ex) {
        String tabKey = key == null ? "unknown" : key.toString();
        if (!BROKEN_CREATIVE_TAB_CACHE.add(creativeTabModeKey(tabKey, operatorTabs))) {
            return;
        }
        RtsbuildingMod.LOGGER.warn(
                "Skipping RTS creative tab {} for {} cache because it failed to build. "
                        + "The RTS storage browser will continue without this tab.",
                tabKey, operatorTabs ? "operator" : "normal", ex);
    }

    private static String creativeTabModeKey(ResourceLocation key, boolean operatorTabs) {
        return creativeTabModeKey(key == null ? "unknown" : key.toString(), operatorTabs);
    }

    private static String creativeTabModeKey(String key, boolean operatorTabs) {
        return (operatorTabs ? "op|" : "normal|") + key;
    }

    private static String creativeTabItemCacheKey(String itemId, boolean operatorTabs) {
        return (operatorTabs ? "op|" : "normal|") + itemId;
    }
}

package com.rtsbuilding.rtsbuilding.server.service.page;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 创造模式标签页索引器，管理用于填充储存浏览器类别芯片的标签页索引。
 *
 * <p>扫描所有已注册的 {@link CreativeModeTab}，为每个物品建立"物品 ID → 所属标签页列表"的映射关系。
 * 使得储存浏览器可以按创造模式标签页（如"建筑方块"、"红石"）来过滤显示物品。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>{@link #ensureCreativeTabContents} — 从已有创造栏快照懒加载缓存（双重检查锁定）</li>
 *   <li>{@link #buildItemTabMapping} — 为给定物品计数映射构建物品→标签页和模组→标签页的映射</li>
 *   <li>{@link #clearCreativeTabCacheState} — 清除所有缓存（世界重载时调用）</li>
 * </ul>
 *
 * <p>使用两级缓存：{"normal/op|itemId" → Set<tabKey>} 的 {@link ConcurrentHashMap}。
 */
public final class RtsPageCreativeTabIndexer {

    private static final ConcurrentMap<String, java.util.Set<String>> ITEM_CREATIVE_TAB_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean creativeTabCacheWarmNormal;
    private static volatile boolean creativeTabCacheWarmOperator;

    private RtsPageCreativeTabIndexer() {
    }

    private static void warmCreativeTabCacheMode(boolean operatorTabs) {
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
            warmCreativeTabCacheMode(operatorTabs);
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
            Identifier rl = Identifier.tryParse(itemId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(rl);
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
     * 只索引游戏已经构建好的创造栏快照。
     *
     * <p>服务端主动调用 {@code buildContents} 会重新派发其他模组的客户端创造栏事件，可能读取尚未加载的
     * 客户端配置或触发网络发送。专用服务器没有快照时允许分类为空，物品浏览和搜索仍可继续工作。
     */
    private static void indexAvailableCreativeTabContents(boolean operatorTabs) {
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            indexCreativeTabContents(tab, key, operatorTabs);
        }
    }

    private static void indexCreativeTabContents(CreativeModeTab tab, Identifier key, boolean operatorTabs) {
        if (key == null || !tab.shouldDisplay()) {
            return;
        }
        String tabKey = key.toString();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 服务端创造栏索引。
 *
 * <p>1.7.10 的创造栏枚举、物品变体填充和标签访问都被标记为客户端专用，
 * 因此这里绝不能像现代版本一样在服务端调用 {@code displayAllReleventItems}。
 * 我们只读取 Item 与 CreativeTabs 的两个普通字段，建立“物品 ID -> 主创造栏”的
 * 保守映射。多标签物品可能少显示一个筛选入口，但专用服务器不会因为加载客户端
 * 方法而崩溃，且“全部/模组”筛选仍然完整可用。
 */
public final class RtsPageCreativeTabIndexer {
    private static final Field ITEM_PRIMARY_TAB = ReflectionHelper.findField(
            Item.class, "tabToDisplayOn", "field_77701_a");
    private static final Field TAB_LABEL = ReflectionHelper.findField(
            CreativeTabs.class, "tabLabel", "field_78034_o");
    private static final ConcurrentMap<String, Set<String>> CACHE =
            new ConcurrentHashMap<String, Set<String>>();
    private static volatile boolean warmNormal;
    private static volatile boolean warmOp;

    private RtsPageCreativeTabIndexer() {}

    public static void clearCreativeTabCacheState() {
        CACHE.clear();
        warmNormal = false;
        warmOp = false;
    }

    static boolean ensureCreativeTabContents(EntityPlayerMP player) {
        boolean op = player != null && com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.canUseCommand(player, 2, "");
        if (op ? warmOp : warmNormal) return true;
        synchronized (RtsPageCreativeTabIndexer.class) {
            if (!(op ? warmOp : warmNormal)) {
                index(op);
                if (op) warmOp = true; else warmNormal = true;
            }
        }
        return true;
    }

    static Set<String> resolveCreativeTabKeys(String itemId, Item item, boolean op) {
        Set<String> result = CACHE.get(key(itemId, op));
        return result == null ? Collections.<String>emptySet() : result;
    }

    static void buildItemTabMapping(Map<String, Long> counts,
            Map<String, Set<String>> itemTabs, Map<String, Set<String>> modTabs, boolean op) {
        if (counts == null) return;
        for (String id : counts.keySet()) {
            ResourceLocation itemId = parse(id);
            if (itemId == null || RtsRegistries.ITEMS.getValue(itemId) == null) continue;
            Set<String> tabs = resolveCreativeTabKeys(id, RtsRegistries.ITEMS.getValue(itemId), op);
            if (tabs.isEmpty()) continue;
            itemTabs.put(id, new HashSet<String>(tabs));
            Set<String> mod = modTabs.get(itemId.getResourceDomain());
            if (mod == null) {
                mod = new HashSet<String>();
                modTabs.put(itemId.getResourceDomain(), mod);
            }
            mod.addAll(tabs);
        }
    }

    private static void index(boolean op) {
        for (Item item : RtsRegistries.ITEMS) {
            ResourceLocation id = RtsRegistries.ITEMS.getKey(item);
            String tabKey = primaryTabKey(item);
            if (id == null || tabKey == null || tabKey.isEmpty()) continue;
            String cacheKey = key(id.toString(), op);
            Set<String> tabs = CACHE.get(cacheKey);
            if (tabs == null) {
                Set<String> fresh = Collections.newSetFromMap(
                        new ConcurrentHashMap<String, Boolean>());
                Set<String> prior = CACHE.putIfAbsent(cacheKey, fresh);
                tabs = prior == null ? fresh : prior;
            }
            tabs.add(tabKey);
        }
    }

    private static String primaryTabKey(Item item) {
        try {
            CreativeTabs tab = (CreativeTabs) ITEM_PRIMARY_TAB.get(item);
            return tab == null ? null : (String) TAB_LABEL.get(tab);
        } catch (IllegalAccessException | RuntimeException ignored) {
            // 第三方物品或反射映射异常只会让该物品缺少标签筛选，不能影响储存页。
            return null;
        }
    }

    private static String key(String id, boolean op) {
        return (op ? "op|" : "normal|") + id;
    }

    private static ResourceLocation parse(String id) {
        try {
            return new ResourceLocation(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

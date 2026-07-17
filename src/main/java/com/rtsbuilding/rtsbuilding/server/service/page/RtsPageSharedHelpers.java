package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 储存页面构建的共享工具方法集合。
 *
 * <p>提供在页面构建流程中多处复用的静态辅助方法，涵盖：
 * <ul>
 *   <li><b>搜索过滤</b>（{@link #matchesSearchQuery}）— 支持物品 ID、显示名称、拼音搜索、
 *   {@code @modid} 命名空间过滤、本地化搜索匹配</li>
 *   <li><b>排序</b>（{@link #entryComparator} / {@link #fluidComparator}）— 按模组、名称、数量排序，
 *   支持升序/降序</li>
 *   <li><b>类别解析</b>（{@link #parseCategorySelection} / {@link #encodeModCategory} / {@link #encodeTabCategory}）— 
 *   处理 "all"、"mod|namespace"、"tab|namespace|tabKey" 三种类别格式</li>
 *   <li><b>命名空间排序</b>（{@link #compareNamespace}）— "minecraft" 优先，其余按字母序</li>
 *   <li><b>页面大小</b>（{@link #sanitizePageSize}）— 限制在服务端配置允许的范围内</li>
 *   <li><b>玩家背包边界</b>— 确定主背包物品在储存视图中的包含范围</li>
 * </ul>
 */
public final class RtsPageSharedHelpers {

    static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    static final String CATEGORY_ALL = "all";
    static final String CATEGORY_MOD_PREFIX = "mod|";
    static final String CATEGORY_TAB_PREFIX = "tab|";

    private RtsPageSharedHelpers() {
    }

    // ---- page size ---------------------------------------------------------------

    public static int defaultPageSize() {
        return Config.defaultStoragePageSize();
    }

    public static int maxPageSize() {
        return Config.maxStoragePageSize();
    }

    public static int sanitizePageSize(int pageSize) {
        return Math.max(1, Math.min(maxPageSize(), pageSize));
    }

    // ---- search ------------------------------------------------------------------

    static boolean matchesSearchQuery(Identifier id, String rawId, String label, String query,
            boolean pinyinSearchEnabled, Set<String> localizedSearchMatches) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String normalizedId = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (localizedSearchMatches != null && localizedSearchMatches.contains(normalizedId)) {
            return true;
        }
        if (query.startsWith("@")) {
            String modQuery = query.substring(1).trim();
            if (modQuery.isEmpty()) {
                return true;
            }
            String namespace = id == null ? "" : id.getNamespace().toLowerCase(Locale.ROOT);
            return namespace.contains(modQuery);
        }
        if (normalizedId.contains(query)) {
            return true;
        }
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return normalizedLabel.contains(query) || (pinyinSearchEnabled && RtsPinyinSearch.contains(label, query));
    }

    public static Set<String> sanitizeLocalizedSearchMatches(List<String> localizedSearchMatches) {
        if (localizedSearchMatches == null || localizedSearchMatches.isEmpty()) {
            return Set.of();
        }
        java.util.Set<String> sanitized = new java.util.HashSet<>();
        for (String itemId : localizedSearchMatches) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            Identifier key = Identifier.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            sanitized.add(key.toString());
            if (sanitized.size() >= C2SRtsRequestStoragePagePayload.MAX_LOCALIZED_SEARCH_MATCHES) {
                break;
            }
        }
        return sanitized;
    }

    // ---- sorting ----------------------------------------------------------------

    static java.util.Comparator<Entry> entryComparator(RtsStorageSort sort, boolean ascending) {
        var comparator = switch (sort) {
            case MOD -> java.util.Comparator.comparing((Entry e) -> e.namespace(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
            case NAME -> java.util.Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::namespace, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
            case QUANTITY -> java.util.Comparator.<Entry, Long>comparing(Entry::count)
                    .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
        };
        if (sort == RtsStorageSort.QUANTITY && !ascending) {
            comparator = comparator.reversed();
        } else if (sort != RtsStorageSort.QUANTITY && !ascending) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    static java.util.Comparator<FluidEntry> fluidComparator(RtsStorageSort sort, boolean ascending) {
        var comparator = switch (sort) {
            case MOD -> java.util.Comparator.comparing(FluidEntry::namespace).thenComparing(FluidEntry::path);
            case NAME -> java.util.Comparator.comparing(FluidEntry::path).thenComparing(FluidEntry::namespace);
            case QUANTITY -> java.util.Comparator.<FluidEntry, Long>comparing(FluidEntry::amount).thenComparing(FluidEntry::path);
        };
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    // ---- namespace ordering -----------------------------------------------------

    static int compareNamespace(String a, String b) {
        if ("minecraft".equals(a)) {
            return "minecraft".equals(b) ? 0 : -1;
        }
        if ("minecraft".equals(b)) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    static int compareTabKey(String a, String b) {
        Identifier aId = Identifier.tryParse(a);
        Identifier bId = Identifier.tryParse(b);
        String aName = aId == null ? a : aId.getPath();
        String bName = bId == null ? b : bId.getPath();
        int byName = aName.compareToIgnoreCase(bName);
        return byName != 0 ? byName : a.compareToIgnoreCase(b);
    }

    // ---- category encoding/parsing ----------------------------------------------

    static String encodeModCategory(String namespace) {
        return CATEGORY_MOD_PREFIX + namespace;
    }

    static String encodeTabCategory(String namespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + namespace + "|" + tabKey;
    }

    public static String normalizeCategory(String category) {
        if (category == null) {
            return CATEGORY_ALL;
        }
        String value = category.toLowerCase(Locale.ROOT).trim();
        if (value.isEmpty() || CATEGORY_ALL.equals(value)) {
            return CATEGORY_ALL;
        }
        if (value.startsWith(CATEGORY_MOD_PREFIX) || value.startsWith(CATEGORY_TAB_PREFIX)) {
            return value;
        }
        return encodeModCategory(value);
    }

    static CategorySelection parseCategorySelection(String normalizedCategory) {
        if (normalizedCategory == null || CATEGORY_ALL.equals(normalizedCategory)) {
            return CategorySelection.all();
        }
        if (normalizedCategory.startsWith(CATEGORY_MOD_PREFIX)) {
            String namespace = normalizedCategory.substring(CATEGORY_MOD_PREFIX.length());
            if (namespace.isBlank()) {
                return CategorySelection.all();
            }
            return CategorySelection.mod(namespace);
        }
        if (normalizedCategory.startsWith(CATEGORY_TAB_PREFIX)) {
            String payload = normalizedCategory.substring(CATEGORY_TAB_PREFIX.length());
            int split = payload.indexOf('|');
            if (split <= 0 || split >= payload.length() - 1) {
                return CategorySelection.all();
            }
            String namespace = payload.substring(0, split);
            String tabKey = payload.substring(split + 1);
            if (namespace.isBlank() || tabKey.isBlank()) {
                return CategorySelection.all();
            }
            return CategorySelection.tab(namespace, tabKey);
        }
        return CategorySelection.all();
    }

    static boolean isValidCategorySelection(CategorySelection selection, List<String> categories) {
        if (selection == null || selection.type() == CategorySelectionType.ALL) {
            return true;
        }
        String token = switch (selection.type()) {
            case MOD -> encodeModCategory(selection.namespace());
            case TAB -> encodeTabCategory(selection.namespace(), selection.tabKey());
            case ALL -> CATEGORY_ALL;
        };
        return categories.contains(token);
    }

    // ---- player inventory bounds -----------------------------------------------

    public static int getPlayerMainInventoryStart(ServerPlayer player) {
        return 0;
    }

    public static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.min(PLAYER_MAIN_INVENTORY_END_EXCLUSIVE, player.getInventory().getContainerSize());
    }

    public static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, RtsStorageSession session) {
        if (player == null || player.containerMenu instanceof com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu) {
            return false;
        }
        if (session != null && session.linkedStorageInfo.isEmpty() && !com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat.hasPrimaryNetwork(player)) {
            return true;
        }
        return player.containerMenu == player.inventoryMenu;
    }
}

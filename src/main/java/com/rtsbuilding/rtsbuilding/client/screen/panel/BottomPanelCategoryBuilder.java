package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.layout.CategoryTypes;
import com.rtsbuilding.rtsbuilding.client.util.RtsCreativeItemCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.CATEGORY_ALL;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.CATEGORY_MOD_PREFIX;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.CATEGORY_TAB_PREFIX;

/**
 * 灏嗙敓浜у垱閫犵洰褰曞拰鏈嶅姟绔偍瀛樺垎绫昏浆鎹㈡垚搴曟爮鍙琛屻€? *
 * <p>鏈被鎷ユ湁鍒嗙被 token 瑙ｆ瀽銆佺ǔ瀹氭帓搴忓拰骞冲彴鏄剧ず鍚嶅洖閫€锛屼笉璐熻矗缁樺埗銆佹粴鍔ㄥ懡涓€? * 缃戠粶璇锋眰鎴栭〉绛剧姸鎬併€傛妸杩欎簺骞冲彴鏁版嵁鏁寸悊鑱岃矗浠?{@link BottomPanel} 鎶藉嚭鍚庯紝
 * 搴曟爮缂栨帓绫讳笉鍐嶅悓鏃舵壙鎷呮敞鍐岃〃鏌ヨ涓庡瓧绗︿覆鏍煎紡鍖栥€?/p>
 */
final class BottomPanelCategoryBuilder {
    static List<CategoryTypes.CategoryRow> creativeRows(
            String selectedCategory,
            Set<String> expandedMods,
            String allLabel,
            Collection<RtsCreativeItemCatalog.CreativeCategory> categories) {
        expandSelectedMod(selectedCategory, expandedMods);
        List<CategoryTypes.CategoryRow> rows = new ArrayList<>();
        for (RtsCreativeItemCatalog.CreativeCategory category : categories) {
            if (category.depth() > 0 && !expandedMods.contains(category.modNamespace())) {
                continue;
            }
            boolean expanded = category.expandable()
                    && expandedMods.contains(category.modNamespace());
            rows.add(new CategoryTypes.CategoryRow(
                    category.token(),
                    CATEGORY_ALL.equals(category.token()) ? allLabel : category.label(),
                    category.depth(),
                    category.expandable(),
                    expanded,
                    category.modNamespace()));
        }
        return rows;
    }

    static List<CategoryTypes.CategoryRow> storageRows(
            Collection<String> rawCategories,
            String selectedCategory,
            Set<String> expandedMods,
            String allLabel) {
        List<CategoryTypes.CategoryRow> rows = new ArrayList<>();
        rows.add(new CategoryTypes.CategoryRow(
                CATEGORY_ALL, allLabel, 0, false, false, ""));

        Map<String, Set<String>> modToTabs = new HashMap<>();
        Set<String> mods = new HashSet<>();
        for (String raw : rawCategories) {
            collectStorageCategory(raw, mods, modToTabs);
        }
        expandSelectedMod(selectedCategory, expandedMods);

        List<String> orderedMods = new ArrayList<>(mods);
        orderedMods.sort(BottomPanelCategoryBuilder::compareNamespace);
        for (String mod : orderedMods) {
            List<String> tabs = new ArrayList<>(
                    modToTabs.getOrDefault(mod, java.util.Collections.emptySet()));
            tabs.sort(BottomPanelCategoryBuilder::compareTabKey);
            boolean expandable = !tabs.isEmpty();
            boolean expanded = expandable && expandedMods.contains(mod);
            rows.add(new CategoryTypes.CategoryRow(
                    encodeModCategory(mod), formatModLabel(mod),
                    0, expandable, expanded, mod));
            if (!expanded) {
                continue;
            }
            for (String tab : tabs) {
                rows.add(new CategoryTypes.CategoryRow(
                        encodeTabCategory(mod, tab), formatTabLabel(tab),
                        1, false, false, mod));
            }
        }
        return rows;
    }

    static String normalizeToken(String token) {
        if (token == null) {
            return CATEGORY_ALL;
        }
        String value = token.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? CATEGORY_ALL : value;
    }

    static String humanizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String normalized = token.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(normalized.length());
        boolean upper = true;
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (character == ' ') {
                result.append(character);
                upper = true;
            } else if (upper) {
                result.append(Character.toUpperCase(character));
                upper = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static void collectStorageCategory(
            String raw,
            Set<String> mods,
            Map<String, Set<String>> modToTabs) {
        String category = normalizeToken(raw);
        if (category.isEmpty() || CATEGORY_ALL.equals(category)) {
            return;
        }
        if (category.startsWith(CATEGORY_MOD_PREFIX)) {
            String mod = category.substring(CATEGORY_MOD_PREFIX.length());
            if (!mod.isBlank()) {
                mods.add(mod);
                modToTabs.computeIfAbsent(mod, ignored -> new HashSet<>());
            }
            return;
        }
        if (category.startsWith(CATEGORY_TAB_PREFIX)) {
            String payload = category.substring(CATEGORY_TAB_PREFIX.length());
            int split = payload.indexOf('|');
            if (split <= 0 || split >= payload.length() - 1) {
                return;
            }
            String mod = payload.substring(0, split);
            String tab = payload.substring(split + 1);
            if (mod.isBlank() || tab.isBlank()) {
                return;
            }
            mods.add(mod);
            modToTabs.computeIfAbsent(mod, ignored -> new HashSet<>()).add(tab);
            return;
        }
        mods.add(category);
        modToTabs.computeIfAbsent(category, ignored -> new HashSet<>());
    }

    private static void expandSelectedMod(String selectedCategory, Set<String> expandedMods) {
        String selected = normalizeToken(selectedCategory);
        if (!selected.startsWith(CATEGORY_TAB_PREFIX)) {
            return;
        }
        String payload = selected.substring(CATEGORY_TAB_PREFIX.length());
        int split = payload.indexOf('|');
        if (split > 0) {
            expandedMods.add(payload.substring(0, split));
        }
    }

    private static String encodeModCategory(String modNamespace) {
        return CATEGORY_MOD_PREFIX + modNamespace;
    }

    private static String encodeTabCategory(String modNamespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + modNamespace + "|" + tabKey;
    }

    private static int compareNamespace(String first, String second) {
        if ("minecraft".equals(first)) {
            return "minecraft".equals(second) ? 0 : -1;
        }
        if ("minecraft".equals(second)) {
            return 1;
        }
        return first.compareToIgnoreCase(second);
    }

    private static int compareTabKey(String first, String second) {
        String firstName = formatTabLabel(first);
        String secondName = formatTabLabel(second);
        int byLabel = firstName.compareToIgnoreCase(secondName);
        return byLabel != 0 ? byLabel : first.compareToIgnoreCase(second);
    }

    private static String formatModLabel(String modNamespace) {
        try {
            return ModList.get().getModContainerById(modNamespace)
                    .map(container -> container.getModInfo().getDisplayName())
                    .filter(label -> label != null && !label.isBlank())
                    .orElseGet(() -> humanizeToken(modNamespace));
        } catch (RuntimeException | LinkageError ignored) {
            return humanizeToken(modNamespace);
        }
    }

    private static String formatTabLabel(String tabKey) {
        Identifier key = Identifier.tryParse(tabKey);
        if (key != null) {
            try {
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key)
                        .map(reference -> reference.value())
                        .orElse(null);
                if (tab != null) {
                    String label = tab.getDisplayName().getString();
                    if (label != null && !label.isBlank()) {
                        return label;
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // 妯＄粍鍒涢€犳爣绛捐鍙栧け璐ユ椂鍥為€€鍒扮ǔ瀹氥€佸彲璇荤殑 token銆?            }
        }
        }
        return humanizeToken(key == null ? tabKey : key.getPath());
    }

    private BottomPanelCategoryBuilder() {
    }
}

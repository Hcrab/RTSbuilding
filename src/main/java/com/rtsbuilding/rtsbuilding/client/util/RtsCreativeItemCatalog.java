package com.rtsbuilding.rtsbuilding.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.*;

/**
 * Client-side cache for the lightweight RTS creative picker.
 * <p>
 * This class only reads creative tabs when the RTS creative tab is rendered.
 * It deliberately treats modded creative tabs as optional data: if a tab throws
 * while exposing its icon, label, or items, that tab is skipped so a broken
 * modded creative tab cannot take down the RTS screen.
 */
public final class RtsCreativeItemCatalog {
    private static final String ALL_TOKEN = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";
    private static final RtsCreativeItemCatalog INSTANCE = new RtsCreativeItemCatalog();

    private final List<CreativeCategory> categories = new ArrayList<>();
    private final List<CreativeEntry> entries = new ArrayList<>();
    private final RtsCreativeSearchCache<CreativeEntry> searchCache =
            new RtsCreativeSearchCache<>(CreativeEntry::searchIndex);
    private long entriesVersion;
    private String lastContextKey = "";
    private boolean initialized;

    private RtsCreativeItemCatalog() {
    }

    public static RtsCreativeItemCatalog get() {
        return INSTANCE;
    }

    public List<CreativeCategory> categories() {
        refreshIfNeeded();
        return this.categories;
    }

    public List<CreativeEntry> entries(String categoryToken, String search) {
        refreshIfNeeded();
        return this.searchCache.filter(this.entries, this.entriesVersion, categoryToken, search);
    }

    public void forceRefresh() {
        rebuild(currentContextKey());
    }

    private void refreshIfNeeded() {
        String contextKey = currentContextKey();
        if (this.initialized && contextKey.equals(this.lastContextKey)) {
            return;
        }
        rebuild(contextKey);
    }

    private void rebuild(String contextKey) {
        this.categories.clear();
        this.entries.clear();
        this.searchCache.invalidate();
        this.categories.add(new CreativeCategory(ALL_TOKEN, "All", 0, false, ""));
        this.lastContextKey = contextKey;
        this.initialized = true;
        this.entriesVersion++;

        CreativeModeTab.ItemDisplayParameters parameters = resolveItemDisplayParameters();
        rebuildVanillaCreativeTabs(parameters);
        Map<String, Set<String>> modToTabs = new LinkedHashMap<>();
        Map<String, String> tabLabels = new LinkedHashMap<>();
        Map<String, String> modLabels = new LinkedHashMap<>();
        Set<String> seenItems = new HashSet<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY || !tab.shouldDisplay()) {
                continue;
            }
            Identifier tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (tabId == null) {
                continue;
            }
            String namespace = tabId.getNamespace();
            String tabKey = tabId.toString();
            String token = encodeTabCategory(namespace, tabKey);
            String label = safeTabLabel(tab, tabId);
            Collection<ItemStack> displayItems = safeDisplayItems(tab);
            if (displayItems.isEmpty()) {
                continue;
            }
            tabLabels.putIfAbsent(token, label);
            modToTabs.computeIfAbsent(namespace, ignored -> new HashSet<>()).add(tabKey);
            rememberBestModLabel(modLabels, namespace, label);
            for (ItemStack stack : displayItems) {
                addEntry(token, stack, seenItems);
            }
        }
        List<String> namespaces = new ArrayList<>(modToTabs.keySet());
        namespaces.sort(RtsCreativeItemCatalog::compareNamespace);
        for (String namespace : namespaces) {
            List<String> tabs = new ArrayList<>(modToTabs.getOrDefault(namespace, Set.of()));
            tabs.sort((a, b) -> compareTabLabel(a, b, namespace, tabLabels));
            String modLabel = resolveModLabel(namespace, modLabels.getOrDefault(namespace, humanizeToken(namespace)));
            this.categories.add(new CreativeCategory(encodeModCategory(namespace), modLabel, 0, !tabs.isEmpty(), namespace));
            for (String tabKey : tabs) {
                String tabToken = encodeTabCategory(namespace, tabKey);
                this.categories.add(new CreativeCategory(tabToken, tabLabels.getOrDefault(tabToken, humanizeTabKey(tabKey)), 1, false, namespace));
            }
        }
    }

    private static String currentContextKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return "no-level";
        }
        String dimension = String.valueOf(mc.level.dimension().identifier());
        boolean operatorTabs = mc.player != null && mc.player.canUseGameMasterBlocks();
        boolean creative = mc.player != null && mc.player.isCreative();
        return dimension + "|op=" + operatorTabs + "|creative=" + creative;
    }

    private static CreativeModeTab.ItemDisplayParameters resolveItemDisplayParameters() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        boolean operatorTabs = mc.player != null && mc.player.canUseGameMasterBlocks();
        return new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(), operatorTabs, mc.level.registryAccess());
    }

    private static void rebuildVanillaCreativeTabs(CreativeModeTab.ItemDisplayParameters parameters) {
        if (parameters == null) {
            return;
        }
        try {
            // 26.1 由 CreativeModeTabs 统一重建所有分类；这条入口同时保持搜索页和
            // NeoForge 扩展内容一致，不能再逐个 tab 私自 buildContents。
            CreativeModeTabs.tryRebuildTabContents(
                    parameters.enabledFeatures(),
                    parameters.hasPermissions(),
                    parameters.holders());
        } catch (RuntimeException | LinkageError ignored) {
            // Bad modded creative tabs should disappear from the RTS picker instead of crashing the screen.
        }
    }

    private void addEntry(String categoryToken, ItemStack stack, Set<String> seenItems) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack preview = stack.copy();
        preview.setCount(1);
        Identifier itemId = BuiltInRegistries.ITEM.getKey(preview.getItem());
        if (itemId == null) {
            return;
        }
        String itemKey = itemId.toString();
        String label;
        try {
            label = preview.getHoverName().getString();
        } catch (RuntimeException ex) {
            label = itemKey;
        }
        String uniqueKey = categoryToken + "|" + itemKey + "|" + label;
        if (!seenItems.add(uniqueKey)) {
            return;
        }
        String mod = itemId.getNamespace();
        String name = itemId.getPath();
        this.entries.add(new CreativeEntry(preview, itemKey, categoryToken, label, mod, name,
                RtsCreativeSearchCache.index(categoryToken, itemKey, label, mod, name)));
    }

    private static Collection<ItemStack> safeDisplayItems(CreativeModeTab tab) {
        try {
            return tab.getDisplayItems();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String safeTabLabel(CreativeModeTab tab, Identifier fallback) {
        try {
            String label = tab.getDisplayName().getString();
            return label == null || label.isBlank() ? fallback.toString() : label;
        } catch (RuntimeException ex) {
            return fallback.toString();
        }
    }

    private static String encodeModCategory(String namespace) {
        return CATEGORY_MOD_PREFIX + namespace;
    }

    private static String encodeTabCategory(String namespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + namespace + "|" + tabKey;
    }

    private static void rememberBestModLabel(Map<String, String> modLabels, String namespace, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String current = modLabels.get(namespace);
        if (current == null || candidate.length() < current.length()) {
            modLabels.put(namespace, candidate);
        }
    }

    private static String resolveModLabel(String namespace, String fallback) {
        try {
            return ModList.get().getModContainerById(namespace)
                    .map(container -> container.getModInfo().getDisplayName())
                    .filter(label -> label != null && !label.isBlank())
                    .orElse(fallback);
        } catch (RuntimeException | LinkageError ignored) {
            return fallback;
        }
    }

    private static int compareNamespace(String a, String b) {
        if ("minecraft".equals(a)) {
            return "minecraft".equals(b) ? 0 : -1;
        }
        if ("minecraft".equals(b)) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static int compareTabLabel(String a, String b, String namespace, Map<String, String> tabLabels) {
        String aLabel = tabLabels.getOrDefault(encodeTabCategory(namespace, a), humanizeTabKey(a));
        String bLabel = tabLabels.getOrDefault(encodeTabCategory(namespace, b), humanizeTabKey(b));
        int byLabel = aLabel.compareToIgnoreCase(bLabel);
        return byLabel != 0 ? byLabel : a.compareToIgnoreCase(b);
    }

    private static String humanizeTabKey(String tabKey) {
        Identifier key = Identifier.tryParse(tabKey);
        return humanizeToken(key == null ? tabKey : key.getPath());
    }

    private static String humanizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String normalized = token.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(normalized.length());
        boolean upper = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == ' ') {
                sb.append(c);
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public record CreativeCategory(String token, String label, int depth, boolean expandable, String modNamespace) {
    }

    public record CreativeEntry(ItemStack stack, String itemId, String categoryToken, String label, String mod, String name,
                                RtsCreativeSearchCache.IndexedEntry searchIndex) {
    }
}

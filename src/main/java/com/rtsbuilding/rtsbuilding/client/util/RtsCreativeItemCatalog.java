package com.rtsbuilding.rtsbuilding.client.util;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side cache for the lightweight RTS creative picker.
 * <p>
 * This class only reads creative tabs when the RTS creative tab is rendered.
 * Modded creative tabs are treated as optional data: if a tab throws while
 * exposing its label, icon, or items, that tab is skipped so the RTS screen can
 * stay usable.
 */
public final class RtsCreativeItemCatalog {
    private static final String ALL_TOKEN = "all";
    private static final RtsCreativeItemCatalog INSTANCE = new RtsCreativeItemCatalog();

    private final List<CreativeCategory> categories = new ArrayList<>();
    private final List<CreativeEntry> entries = new ArrayList<>();
    private long lastRefreshMs;

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
        String normalizedCategory = normalizeToken(categoryToken);
        String normalizedSearch = normalizeSearch(search);
        List<CreativeEntry> filtered = new ArrayList<>();
        for (CreativeEntry entry : this.entries) {
            if (!ALL_TOKEN.equals(normalizedCategory) && !entry.categoryToken().equals(normalizedCategory)) {
                continue;
            }
            if (!matchesSearch(entry, normalizedSearch)) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (!this.entries.isEmpty() && now - this.lastRefreshMs < 5_000L) {
            return;
        }
        rebuild();
        this.lastRefreshMs = now;
    }

    private void rebuild() {
        this.categories.clear();
        this.entries.clear();
        this.categories.add(new CreativeCategory(ALL_TOKEN, "All"));

        CreativeModeTab.ItemDisplayParameters parameters = resolveItemDisplayParameters();
        Map<String, CreativeCategory> categoryMap = new LinkedHashMap<>();
        Set<String> seenItems = new HashSet<>();
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY || !tab.shouldDisplay()) {
                continue;
            }
            ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (tabId == null) {
                continue;
            }
            String token = "tab:" + tabId;
            String label = safeTabLabel(tab, tabId);
            buildContentsIfPossible(tab, parameters);
            Collection<ItemStack> displayItems = safeDisplayItems(tab);
            if (displayItems.isEmpty()) {
                continue;
            }
            categoryMap.putIfAbsent(token, new CreativeCategory(token, label));
            for (ItemStack stack : displayItems) {
                addEntry(token, stack, seenItems);
            }
        }
        this.categories.addAll(categoryMap.values());
    }

    private static CreativeModeTab.ItemDisplayParameters resolveItemDisplayParameters() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        boolean operatorTabs = mc.player != null && mc.player.canUseGameMasterBlocks();
        return new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(), operatorTabs, mc.level.registryAccess());
    }

    private static void buildContentsIfPossible(CreativeModeTab tab, CreativeModeTab.ItemDisplayParameters parameters) {
        if (parameters == null) {
            return;
        }
        try {
            tab.buildContents(parameters);
        } catch (RuntimeException | LinkageError ignored) {
            // Broken modded creative tabs should disappear from the RTS picker instead of crashing the screen.
        }
    }

    private void addEntry(String categoryToken, ItemStack stack, Set<String> seenItems) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack preview = stack.copy();
        preview.setCount(1);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(preview.getItem());
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
        this.entries.add(new CreativeEntry(preview, itemKey, categoryToken, label, itemId.getNamespace(), itemId.getPath()));
    }

    private static Collection<ItemStack> safeDisplayItems(CreativeModeTab tab) {
        try {
            return tab.getDisplayItems();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String safeTabLabel(CreativeModeTab tab, ResourceLocation fallback) {
        try {
            String label = tab.getDisplayName().getString();
            return label == null || label.isBlank() ? fallback.toString() : label;
        } catch (RuntimeException ex) {
            return fallback.toString();
        }
    }

    private static boolean matchesSearch(CreativeEntry entry, String search) {
        if (search.isBlank()) {
            return true;
        }
        String label = entry.label().toLowerCase(Locale.ROOT);
        String itemId = entry.itemId().toLowerCase(Locale.ROOT);
        String mod = entry.mod().toLowerCase(Locale.ROOT);
        String name = entry.name().toLowerCase(Locale.ROOT);
        for (String token : search.split("\\s+")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.startsWith("@")) {
                String modQuery = token.substring(1).trim();
                if (!modQuery.isEmpty() && !mod.contains(modQuery)) {
                    return false;
                }
                continue;
            }
            if (!label.contains(token)
                    && !itemId.contains(token)
                    && !mod.contains(token)
                    && !name.contains(token)
                    && !RtsPinyinSearch.contains(entry.label(), token)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return ALL_TOKEN;
        }
        return token.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    public record CreativeCategory(String token, String label) {
    }

    public record CreativeEntry(ItemStack stack, String itemId, String categoryToken, String label, String mod, String name) {
    }
}

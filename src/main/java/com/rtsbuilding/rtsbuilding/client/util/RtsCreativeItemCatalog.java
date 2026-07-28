package com.rtsbuilding.rtsbuilding.client.util;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

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
    private long lastRebuildMs;

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
        long now = System.currentTimeMillis();
        // 创造页可能在客户端创造标签尚未完成装填时首次打开。空结果不能像正常
        // catalog 一样永久缓存，否则玩家在本次进服期间会一直看到 0 个物品。
        boolean emptyRetryDue = this.entries.isEmpty() && now - this.lastRebuildMs >= 1_000L;
        if (this.initialized && contextKey.equals(this.lastContextKey) && !emptyRetryDue) {
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
        this.lastRebuildMs = System.currentTimeMillis();
        this.entriesVersion++;

        Map<String, Set<String>> modToTabs = new LinkedHashMap<>();
        Map<String, String> tabLabels = new LinkedHashMap<>();
        Map<String, String> modLabels = new LinkedHashMap<>();
        Set<String> seenItems = new HashSet<>();
        for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
            if (tab == null) continue;
            String tabLabel = tab.getTabLabel();
            String namespace = namespaceForTab(tab);
            String tabKey = namespace + ":" + sanitizePath(tabLabel);
            String token = encodeTabCategory(namespace, tabKey);
            String label = safeTabLabel(tab, tabKey);
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
            List<String> tabs = new ArrayList<>(modToTabs.containsKey(namespace)
                    ? modToTabs.get(namespace) : Collections.<String>emptySet());
            tabs.sort((a, b) -> compareTabLabel(a, b, namespace, tabLabels));
            String modLabel = resolveModLabel(namespace, modLabels.getOrDefault(namespace, humanizeToken(namespace)));
            this.categories.add(new CreativeCategory(encodeModCategory(namespace), modLabel, 0, !tabs.isEmpty(), namespace));
            for (String tabKey : tabs) {
                String tabToken = encodeTabCategory(namespace, tabKey);
                this.categories.add(new CreativeCategory(tabToken, tabLabels.getOrDefault(tabToken, humanizeTabKey(tabKey)), 1, false, namespace));
            }
        }
        RtsbuildingMod.LOGGER.debug(
                "RTS creative catalog rebuilt: context={}, categories={}, entries={}",
                contextKey, this.categories.size(), this.entries.size());
    }

    private static String currentContextKey() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return "no-level";
        }
        String dimension = String.valueOf(mc.world.provider.getDimension());
        boolean operatorTabs = mc.player != null && mc.player.canUseCommand(2, "gamemode");
        return dimension + "|op=" + operatorTabs;
    }

    private void addEntry(String categoryToken, ItemStack stack, Set<String> seenItems) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack preview = stack.copy();
        preview.setCount(1);
        ResourceLocation itemId = Item.REGISTRY.getNameForObject(preview.getItem());
        if (itemId == null) {
            return;
        }
        String itemKey = itemId.toString();
        String label;
        try {
            label = preview.getDisplayName();
        } catch (RuntimeException ex) {
            label = itemKey;
        }
        String uniqueKey = categoryToken + "|" + itemKey + "|" + preview.getMetadata()
                + "|" + String.valueOf(preview.getTagCompound());
        if (!seenItems.add(uniqueKey)) {
            return;
        }
        String mod = itemId.getNamespace();
        String name = itemId.getPath();
        this.entries.add(new CreativeEntry(preview, itemKey, categoryToken, label, mod, name,
                RtsCreativeSearchCache.index(categoryToken, itemKey, label, mod, name)));
    }

    private static Collection<ItemStack> safeDisplayItems(CreativeTabs tab) {
        try {
            NonNullList<ItemStack> stacks = NonNullList.create();
            tab.displayAllRelevantItems(stacks);
            return stacks;
        } catch (RuntimeException | LinkageError ex) {
            return Collections.emptyList();
        }
    }

    private static String safeTabLabel(CreativeTabs tab, String fallback) {
        try {
            String label = I18n.format(tab.getTranslationKey());
            return isBlank(label) ? fallback : label;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static String namespaceForTab(CreativeTabs tab) {
        try {
            ItemStack icon = tab.getIcon();
            ResourceLocation id = icon.isEmpty() ? null : Item.REGISTRY.getNameForObject(icon.getItem());
            return id == null ? "minecraft" : id.getNamespace();
        } catch (RuntimeException | LinkageError ignored) {
            return "minecraft";
        }
    }

    private static String sanitizePath(String value) {
        String safe = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        safe = safe.replaceAll("[^a-z0-9_./-]", "_");
        return safe.isEmpty() ? "unknown" : safe;
    }

    private static String encodeModCategory(String namespace) {
        return CATEGORY_MOD_PREFIX + namespace;
    }

    private static String encodeTabCategory(String namespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + namespace + "|" + tabKey;
    }

    private static void rememberBestModLabel(Map<String, String> modLabels, String namespace, String candidate) {
        if (isBlank(candidate)) {
            return;
        }
        String current = modLabels.get(namespace);
        if (current == null || candidate.length() < current.length()) {
            modLabels.put(namespace, candidate);
        }
    }

    private static String resolveModLabel(String namespace, String fallback) {
        try {
            ModContainer container = Loader.instance().getIndexedModList().get(namespace);
            String label = container == null ? null : container.getName();
            return isBlank(label) ? fallback : label;
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
        ResourceLocation key;
        try {
            key = new ResourceLocation(tabKey);
        } catch (RuntimeException ex) {
            key = null;
        }
        return humanizeToken(key == null ? tabKey : key.getPath());
    }

    private static String humanizeToken(String token) {
        if (isBlank(token)) {
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class CreativeCategory {
        private final String token;
        private final String label;
        private final int depth;
        private final boolean expandable;
        private final String modNamespace;

        public CreativeCategory(String token, String label, int depth, boolean expandable, String modNamespace) {
            this.token = token;
            this.label = label;
            this.depth = depth;
            this.expandable = expandable;
            this.modNamespace = modNamespace;
        }

        public String token() { return token; }
        public String label() { return label; }
        public int depth() { return depth; }
        public boolean expandable() { return expandable; }
        public String modNamespace() { return modNamespace; }
    }

    public static final class CreativeEntry {
        private final ItemStack stack;
        private final String itemId;
        private final String categoryToken;
        private final String label;
        private final String mod;
        private final String name;
        private final RtsCreativeSearchCache.IndexedEntry searchIndex;

        public CreativeEntry(ItemStack stack, String itemId, String categoryToken, String label,
                             String mod, String name, RtsCreativeSearchCache.IndexedEntry searchIndex) {
            this.stack = stack;
            this.itemId = itemId;
            this.categoryToken = categoryToken;
            this.label = label;
            this.mod = mod;
            this.name = name;
            this.searchIndex = searchIndex;
        }

        public ItemStack stack() { return stack; }
        public String itemId() { return itemId; }
        public String categoryToken() { return categoryToken; }
        public String label() { return label; }
        public String mod() { return mod; }
        public String name() { return name; }
        public RtsCreativeSearchCache.IndexedEntry searchIndex() { return searchIndex; }
    }
}

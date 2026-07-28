package com.rtsbuilding.rtsbuilding.client.util;

import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * 创造物品栏搜索的小型索引缓存。
 * <p>
 * 这个类只负责“同一批条目 + 同一分类 + 同一搜索词”的过滤缓存，不读取
 * Minecraft 注册表，也不持有 UI 状态。这样 BottomPanel 可以在渲染、tooltip
 * 和点击命中测试之间复用同一份结果，避免大整合包里每帧反复扫描完整创造栏。
 */
public final class RtsCreativeSearchCache<T> {
    static final String ALL_TOKEN = "all";
    static final String CATEGORY_MOD_PREFIX = "mod|";

    private final Function<T, IndexedEntry> indexer;
    private long cachedVersion = Long.MIN_VALUE;
    private String cachedCategory = "";
    private String cachedSearch = "";
    private List<T> cachedResult = Collections.emptyList();
    private int lastScanCount;
    private int lastPinyinCheckCount;

    RtsCreativeSearchCache(Function<T, IndexedEntry> indexer) {
        this.indexer = Objects.requireNonNull(indexer);
    }

    List<T> filter(List<T> entries, long sourceVersion, String categoryToken, String search) {
        String normalizedCategory = normalizeToken(categoryToken);
        String normalizedSearch = normalizeSearch(search);
        if (sourceVersion == this.cachedVersion
                && normalizedCategory.equals(this.cachedCategory)
                && normalizedSearch.equals(this.cachedSearch)) {
            this.lastScanCount = 0;
            this.lastPinyinCheckCount = 0;
            return this.cachedResult;
        }

        SearchToken[] tokens = parseSearchTokens(normalizedSearch);
        List<T> result = new ArrayList<>();
        int scans = 0;
        int pinyinChecks = 0;
        for (T entry : entries) {
            scans++;
            IndexedEntry indexed = this.indexer.apply(entry);
            MatchResult match = matches(indexed, normalizedCategory, tokens);
            pinyinChecks += match.pinyinChecks();
            if (match.matched()) {
                result.add(entry);
            }
        }

        this.cachedVersion = sourceVersion;
        this.cachedCategory = normalizedCategory;
        this.cachedSearch = normalizedSearch;
        this.cachedResult = Collections.unmodifiableList(new ArrayList<T>(result));
        this.lastScanCount = scans;
        this.lastPinyinCheckCount = pinyinChecks;
        return this.cachedResult;
    }

    void invalidate() {
        this.cachedVersion = Long.MIN_VALUE;
        this.cachedCategory = "";
        this.cachedSearch = "";
        this.cachedResult = Collections.emptyList();
        this.lastScanCount = 0;
        this.lastPinyinCheckCount = 0;
    }

    int lastScanCountForDiagnostics() {
        return this.lastScanCount;
    }

    int lastPinyinCheckCountForDiagnostics() {
        return this.lastPinyinCheckCount;
    }

    static IndexedEntry index(String categoryToken, String itemId, String label, String mod, String name) {
        String normalizedItemId = lower(itemId);
        String normalizedLabel = lower(label);
        String normalizedMod = lower(mod);
        String normalizedName = lower(name);
        String searchText = normalizedLabel + "\n" + normalizedItemId + "\n" + normalizedMod + "\n" + normalizedName;
        return new IndexedEntry(
                normalizeToken(categoryToken),
                normalizedItemId,
                label == null ? "" : label,
                normalizedMod,
                normalizedName,
                searchText,
                containsHan(label));
    }

    private static MatchResult matches(IndexedEntry entry, String category, SearchToken[] tokens) {
        if (!matchesCategory(entry, category)) {
            return MatchResult.NO;
        }
        if (tokens.length == 0) {
            return MatchResult.YES;
        }
        int pinyinChecks = 0;
        for (SearchToken token : tokens) {
            if (token.modOnly()) {
                if (!token.value().isEmpty() && !entry.mod().contains(token.value())) {
                    return new MatchResult(false, pinyinChecks);
                }
                continue;
            }
            if (entry.searchText().contains(token.value())) {
                continue;
            }
            if (entry.hasHanLabel()) {
                pinyinChecks++;
                if (RtsPinyinSearch.contains(entry.label(), token.value())) {
                    continue;
                }
            }
            return new MatchResult(false, pinyinChecks);
        }
        return new MatchResult(true, pinyinChecks);
    }

    private static boolean matchesCategory(IndexedEntry entry, String category) {
        if (category.trim().isEmpty() || ALL_TOKEN.equals(category)) {
            return true;
        }
        if (category.startsWith(CATEGORY_MOD_PREFIX)) {
            return entry.mod().equals(category.substring(CATEGORY_MOD_PREFIX.length()));
        }
        return entry.categoryToken().equals(category);
    }

    private static SearchToken[] parseSearchTokens(String search) {
        if (search.trim().isEmpty()) {
            return new SearchToken[0];
        }
        String[] rawTokens = search.split("\\s+");
        List<SearchToken> tokens = new ArrayList<>(rawTokens.length);
        for (String raw : rawTokens) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            if (raw.startsWith("@")) {
                tokens.add(new SearchToken(true, raw.substring(1).trim()));
            } else {
                tokens.add(new SearchToken(false, raw));
            }
        }
        return tokens.toArray(new SearchToken[0]);
    }

    private static String normalizeToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return ALL_TOKEN;
        }
        return lower(token.trim());
    }

    private static String normalizeSearch(String search) {
        return search == null ? "" : lower(search.trim());
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsHan(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    public static final class IndexedEntry {
        private final String categoryToken, itemId, label, mod, name, searchText;
        private final boolean hasHanLabel;
        public IndexedEntry(String categoryToken, String itemId, String label, String mod, String name,
                String searchText, boolean hasHanLabel) {
            this.categoryToken = categoryToken; this.itemId = itemId; this.label = label;
            this.mod = mod; this.name = name; this.searchText = searchText; this.hasHanLabel = hasHanLabel;
        }
        public String categoryToken() { return categoryToken; }
        public String itemId() { return itemId; }
        public String label() { return label; }
        public String mod() { return mod; }
        public String name() { return name; }
        public String searchText() { return searchText; }
        public boolean hasHanLabel() { return hasHanLabel; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IndexedEntry)) return false;
            IndexedEntry that = (IndexedEntry) other;
            return hasHanLabel == that.hasHanLabel && Objects.equals(categoryToken, that.categoryToken)
                    && Objects.equals(itemId, that.itemId) && Objects.equals(label, that.label)
                    && Objects.equals(mod, that.mod) && Objects.equals(name, that.name)
                    && Objects.equals(searchText, that.searchText);
        }
        @Override public int hashCode() {
            return Objects.hash(categoryToken, itemId, label, mod, name, searchText, hasHanLabel);
        }
        @Override public String toString() {
            return "IndexedEntry[categoryToken=" + categoryToken + ", itemId=" + itemId
                    + ", label=" + label + ", mod=" + mod + ", name=" + name
                    + ", searchText=" + searchText + ", hasHanLabel=" + hasHanLabel + "]";
        }
    }

    private static final class SearchToken {
        private final boolean modOnly;
        private final String value;
        private SearchToken(boolean modOnly, String value) { this.modOnly = modOnly; this.value = value; }
        boolean modOnly() { return modOnly; }
        String value() { return value; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SearchToken)) return false;
            SearchToken that = (SearchToken) other;
            return modOnly == that.modOnly && Objects.equals(value, that.value);
        }
        @Override public int hashCode() { return Objects.hash(modOnly, value); }
        @Override public String toString() { return "SearchToken[modOnly=" + modOnly + ", value=" + value + "]"; }
    }

    private static final class MatchResult {
        private final boolean matched;
        private final int pinyinChecks;
        private static final MatchResult YES = new MatchResult(true, 0);
        private static final MatchResult NO = new MatchResult(false, 0);

        private MatchResult(boolean matched, int pinyinChecks) {
            this.matched = matched;
            this.pinyinChecks = pinyinChecks;
        }
        boolean matched() { return matched; }
        int pinyinChecks() { return pinyinChecks; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MatchResult)) return false;
            MatchResult that = (MatchResult) other;
            return matched == that.matched && pinyinChecks == that.pinyinChecks;
        }
        @Override public int hashCode() { return Objects.hash(matched, pinyinChecks); }
        @Override public String toString() {
            return "MatchResult[matched=" + matched + ", pinyinChecks=" + pinyinChecks + "]";
        }
    }
}

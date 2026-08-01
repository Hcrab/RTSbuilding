package com.rtsbuilding.rtsbuilding.client.util;

import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;

import java.util.ArrayList;
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
    private List<T> cachedResult = List.of();
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
        this.cachedResult = List.copyOf(result);
        this.lastScanCount = scans;
        this.lastPinyinCheckCount = pinyinChecks;
        return this.cachedResult;
    }

    void invalidate() {
        this.cachedVersion = Long.MIN_VALUE;
        this.cachedCategory = "";
        this.cachedSearch = "";
        this.cachedResult = List.of();
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
        if (category.isBlank() || ALL_TOKEN.equals(category)) {
            return true;
        }
        if (category.startsWith(CATEGORY_MOD_PREFIX)) {
            return entry.mod().equals(category.substring(CATEGORY_MOD_PREFIX.length()));
        }
        return entry.categoryToken().equals(category);
    }

    private static SearchToken[] parseSearchTokens(String search) {
        if (search.isBlank()) {
            return new SearchToken[0];
        }
        String[] rawTokens = search.split("\\s+");
        List<SearchToken> tokens = new ArrayList<>(rawTokens.length);
        for (String raw : rawTokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (raw.startsWith("@")) {
                tokens.add(new SearchToken(true, raw.substring(1).trim()));
            } else {
                tokens.add(new SearchToken(false, raw));
            }
        }
        return tokens.toArray(SearchToken[]::new);
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
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
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    public record IndexedEntry(String categoryToken, String itemId, String label, String mod, String name,
                               String searchText, boolean hasHanLabel) {
    }

    private record SearchToken(boolean modOnly, String value) {
    }

    private record MatchResult(boolean matched, int pinyinChecks) {
        private static final MatchResult YES = new MatchResult(true, 0);
        private static final MatchResult NO = new MatchResult(false, 0);
    }
}

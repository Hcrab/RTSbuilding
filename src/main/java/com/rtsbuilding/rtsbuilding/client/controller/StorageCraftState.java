package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.CraftFeedbackIngredient;
import com.rtsbuilding.rtsbuilding.client.record.CraftRecipeOption;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端合成目录与合成反馈的唯一状态 owner。
 *
 * <p>它负责查询条件、增量分页、防重复请求、服务端载荷解码和短时反馈合并；
 * 不负责储存页、快捷槽、GUI 绑定或面板生命周期。拆出后，储存门面只编排跨域清理，
 * 合成列表的所有不变量仍集中在一个可独立验证的边界内。</p>
 */
final class StorageCraftState {
    private static final int BATCH_SIZE = 12;

    private String search = "";
    private boolean showUnavailable;
    private final List<CraftableEntry> entries = new ArrayList<>();
    private int revision;
    private boolean hasMore;
    private final Set<Integer> pendingOffsets = new HashSet<>();
    private String feedbackItemId = "";
    private int feedbackCount;
    private long feedbackExpiryMs;
    private final List<CraftFeedbackIngredient> feedbackIngredients = new ArrayList<>();

    String search() { return search; }
    boolean showUnavailable() { return showUnavailable; }
    List<CraftableEntry> entries() { return immutable(entries); }
    int revision() { return revision; }
    boolean hasMore() { return hasMore; }
    String feedbackItemId() { return feedbackItemId; }
    int feedbackCount() { return feedbackCount; }
    long feedbackExpiryMs() { return feedbackExpiryMs; }
    List<CraftFeedbackIngredient> feedbackIngredients() { return immutable(feedbackIngredients); }

    void setSearch(String value) {
        String normalized = normalizeSearch(value);
        if (search.equals(normalized)) return;
        search = normalized;
        requestFirstPage();
    }

    void setShowUnavailable(boolean value) {
        if (showUnavailable == value) return;
        showUnavailable = value;
        requestFirstPage();
    }

    void requestFirstPage() {
        search = normalizeSearch(search);
        clear();
        if (!search.trim().isEmpty()) requestPage(0, BATCH_SIZE);
    }

    void requestMore() {
        if (!search.trim().isEmpty() && hasMore) requestPage(entries.size(), BATCH_SIZE);
    }

    void craft(String recipeId, int craftCount) {
        if (recipeId != null && !recipeId.trim().isEmpty()) {
            RtsClientPacketGateway.sendCraftRecipe(recipeId, craftCount);
        }
    }

    void apply(S2CRtsCraftablesPayload payload) {
        String payloadSearch = normalizeSearch(payload.search());
        if (!search.equals(payloadSearch) || showUnavailable != payload.showUnavailable()) return;

        int offset = Math.max(0, payload.offset());
        pendingOffsets.remove(offset);
        if (!payload.append() || offset == 0) entries.clear();
        else if (offset != entries.size()) return;

        int size = Math.min(payload.recipeIds().size(), Math.min(payload.resultItemIds().size(),
                Math.min(payload.resultCounts().size(), Math.min(payload.craftable().size(), payload.missingSummaries().size()))));
        int optionFlatIndex = 0;
        for (int i = 0; i < size; i++) {
            ResourceLocation id = parseId(payload.resultItemIds().get(i));
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null) {
                optionFlatIndex += i < payload.recipeOptionCounts().size() ? Math.max(0, payload.recipeOptionCounts().get(i)) : 0;
                continue;
            }
            ItemStack stack = new ItemStack(item);
            int resultCount = Math.max(1, payload.resultCounts().get(i));
            stack.setCount(Math.min(resultCount, stack.getMaxStackSize()));
            int optionCount = i < payload.recipeOptionCounts().size() ? Math.max(0, payload.recipeOptionCounts().get(i)) : 0;
            List<CraftRecipeOption> options = new ArrayList<>(optionCount);
            for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                if (optionFlatIndex >= payload.optionRecipeIds().size()
                        || optionFlatIndex >= payload.optionResultCounts().size()
                        || optionFlatIndex >= payload.optionCraftable().size()
                        || optionFlatIndex >= payload.optionSummaries().size()
                        || optionFlatIndex >= payload.optionMissingSummaries().size()) break;
                options.add(new CraftRecipeOption(payload.optionRecipeIds().get(optionFlatIndex),
                        Math.max(1, payload.optionResultCounts().get(optionFlatIndex)),
                        payload.optionCraftable().get(optionFlatIndex), payload.optionSummaries().get(optionFlatIndex),
                        payload.optionMissingSummaries().get(optionFlatIndex)));
                optionFlatIndex++;
            }
            if (options.isEmpty()) {
                options.add(new CraftRecipeOption(payload.recipeIds().get(i), resultCount, payload.craftable().get(i),
                        stack.getDisplayName(), payload.missingSummaries().get(i)));
            }
            entries.add(new CraftableEntry(stack, payload.recipeIds().get(i), payload.resultItemIds().get(i), resultCount,
                    payload.craftable().get(i), payload.missingSummaries().get(i),
                    id.getResourceDomain(), id.getResourcePath(), immutable(options)));
        }
        search = payloadSearch;
        showUnavailable = payload.showUnavailable();
        hasMore = payload.hasMore();
        revision++;
    }

    void applyFeedback(S2CRtsCraftFeedbackPayload payload) {
        String itemId = payload.itemId() == null ? "" : payload.itemId();
        int craftedCount = Math.max(0, payload.craftedCount());
        if (itemId.trim().isEmpty() || craftedCount <= 0) return;
        List<CraftFeedbackIngredient> decoded = new ArrayList<>();
        int size = Math.min(payload.consumedItemIds().size(), payload.consumedCounts().size());
        for (int i = 0; i < size; i++) {
            String consumedId = payload.consumedItemIds().get(i);
            ResourceLocation key = parseId(consumedId);
            Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
            if (item == null) continue;
            ItemStack preview = new ItemStack(item);
            decoded.add(new CraftFeedbackIngredient(consumedId, preview.getDisplayName(), preview,
                    Math.max(0, payload.consumedCounts().get(i))));
        }
        long now = System.currentTimeMillis();
        boolean merge = itemId.equals(feedbackItemId) && now <= feedbackExpiryMs;
        if (merge) feedbackCount += craftedCount;
        else { feedbackItemId = itemId; feedbackCount = craftedCount; }
        if (merge) mergeIngredients(decoded);
        else { feedbackIngredients.clear(); feedbackIngredients.addAll(decoded); }
        feedbackExpiryMs = now + 2200L;
    }

    void clear() {
        boolean changed = !entries.isEmpty() || hasMore || !pendingOffsets.isEmpty();
        entries.clear();
        hasMore = false;
        pendingOffsets.clear();
        if (changed) revision++;
    }

    private void requestPage(int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        if (!pendingOffsets.add(safeOffset)) return;
        RtsClientPacketGateway.sendRequestCraftables(search, showUnavailable, safeOffset, Math.max(1, limit));
    }

    private void mergeIngredients(List<CraftFeedbackIngredient> added) {
        Map<String, CraftFeedbackIngredient> merged = new LinkedHashMap<>();
        for (CraftFeedbackIngredient ingredient : feedbackIngredients) {
            if (ingredient != null && ingredient.itemId() != null && !ingredient.itemId().trim().isEmpty()) {
                merged.put(ingredient.itemId(), ingredient);
            }
        }
        for (CraftFeedbackIngredient ingredient : added) {
            if (ingredient == null || ingredient.itemId() == null || ingredient.itemId().trim().isEmpty()) continue;
            CraftFeedbackIngredient existing = merged.get(ingredient.itemId());
            merged.put(ingredient.itemId(), existing == null ? ingredient : new CraftFeedbackIngredient(
                    ingredient.itemId(), ingredient.label(), ingredient.preview().copy(), existing.count() + ingredient.count()));
        }
        feedbackIngredients.clear();
        feedbackIngredients.addAll(merged.values());
    }

    private static String normalizeSearch(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResourceLocation parseId(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : new ResourceLocation(text);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}

package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class S2CRtsCraftablesPayload implements IMessage {
    private static final int MAX_RESULTS = 8192;
    private static final int MAX_OPTIONS = 8192;
    private static final int MAX_OFFSET = 1_000_000;
    private static final int MAX_RESULT_COUNT = 1_000_000;
    private static final int MAX_SEARCH_CHARS = 128;
    private static final int MAX_RECIPE_ID_CHARS = 256;
    private static final int MAX_ITEM_ID_CHARS = 128;
    private static final int MAX_SUMMARY_CHARS = 512;

    private String search = "";
    private boolean showUnavailable;
    private int offset;
    private boolean append;
    private boolean hasMore;
    private List<String> recipeIds = Collections.emptyList();
    private List<String> resultItemIds = Collections.emptyList();
    private List<Integer> resultCounts = Collections.emptyList();
    private List<Boolean> craftable = Collections.emptyList();
    private List<String> missingSummaries = Collections.emptyList();
    private List<Integer> recipeOptionCounts = Collections.emptyList();
    private List<String> optionRecipeIds = Collections.emptyList();
    private List<Integer> optionResultCounts = Collections.emptyList();
    private List<Boolean> optionCraftable = Collections.emptyList();
    private List<String> optionSummaries = Collections.emptyList();
    private List<String> optionMissingSummaries = Collections.emptyList();

    public S2CRtsCraftablesPayload() {
    }

    public S2CRtsCraftablesPayload(String search, boolean showUnavailable, int offset,
            boolean append, boolean hasMore, List<String> recipeIds,
            List<String> resultItemIds, List<Integer> resultCounts,
            List<Boolean> craftable, List<String> missingSummaries,
            List<Integer> recipeOptionCounts, List<String> optionRecipeIds,
            List<Integer> optionResultCounts, List<Boolean> optionCraftable,
            List<String> optionSummaries, List<String> optionMissingSummaries) {
        this.search = safe(search);
        this.showUnavailable = showUnavailable;
        this.offset = offset;
        this.append = append;
        this.hasMore = hasMore;
        this.recipeIds = immutable(recipeIds);
        this.resultItemIds = immutable(resultItemIds);
        this.resultCounts = immutable(resultCounts);
        this.craftable = immutable(craftable);
        this.missingSummaries = immutable(missingSummaries);
        this.recipeOptionCounts = immutable(recipeOptionCounts);
        this.optionRecipeIds = immutable(optionRecipeIds);
        this.optionResultCounts = immutable(optionResultCounts);
        this.optionCraftable = immutable(optionCraftable);
        this.optionSummaries = immutable(optionSummaries);
        this.optionMissingSummaries = immutable(optionMissingSummaries);
    }

    public String search() { return search; }
    public boolean showUnavailable() { return showUnavailable; }
    public int offset() { return offset; }
    public boolean append() { return append; }
    public boolean hasMore() { return hasMore; }
    public List<String> recipeIds() { return recipeIds; }
    public List<String> resultItemIds() { return resultItemIds; }
    public List<Integer> resultCounts() { return resultCounts; }
    public List<Boolean> craftable() { return craftable; }
    public List<String> missingSummaries() { return missingSummaries; }
    public List<Integer> recipeOptionCounts() { return recipeOptionCounts; }
    public List<String> optionRecipeIds() { return optionRecipeIds; }
    public List<Integer> optionResultCounts() { return optionResultCounts; }
    public List<Boolean> optionCraftable() { return optionCraftable; }
    public List<String> optionSummaries() { return optionSummaries; }
    public List<String> optionMissingSummaries() { return optionMissingSummaries; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        search = RtsPacketBuffer.readString(buffer, MAX_SEARCH_CHARS, "craftables search");
        showUnavailable = buffer.readBoolean();
        offset = RtsPacketBuffer.readBoundedCount(buffer, MAX_OFFSET, "craftables offset");
        append = buffer.readBoolean();
        hasMore = buffer.readBoolean();

        int size = RtsPacketBuffer.readBoundedCount(buffer, MAX_RESULTS, "craftable result count");
        List<String> decodedRecipeIds = new ArrayList<>(size);
        List<String> decodedResultIds = new ArrayList<>(size);
        List<Integer> decodedCounts = new ArrayList<>(size);
        List<Boolean> decodedCraftable = new ArrayList<>(size);
        List<String> decodedMissing = new ArrayList<>(size);
        List<Integer> decodedOptionCounts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            decodedRecipeIds.add(RtsPacketBuffer.readString(buffer, MAX_RECIPE_ID_CHARS, "recipe id"));
            decodedResultIds.add(RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "result item id"));
            decodedCounts.add(RtsPacketBuffer.readBoundedCount(buffer, MAX_RESULT_COUNT, "result count"));
            decodedCraftable.add(buffer.readBoolean());
            decodedMissing.add(RtsPacketBuffer.readString(buffer, MAX_SUMMARY_CHARS, "missing summary"));
            decodedOptionCounts.add(RtsPacketBuffer.readBoundedCount(buffer, MAX_OPTIONS, "recipe option count"));
        }

        int optionSize = RtsPacketBuffer.readBoundedCount(buffer, MAX_OPTIONS,
                "flattened recipe option count");
        List<String> decodedOptionIds = new ArrayList<>(optionSize);
        List<Integer> decodedOptionResultCounts = new ArrayList<>(optionSize);
        List<Boolean> decodedOptionCraftable = new ArrayList<>(optionSize);
        List<String> decodedOptionSummaries = new ArrayList<>(optionSize);
        List<String> decodedOptionMissing = new ArrayList<>(optionSize);
        for (int i = 0; i < optionSize; i++) {
            decodedOptionIds.add(RtsPacketBuffer.readString(buffer, MAX_RECIPE_ID_CHARS,
                    "option recipe id"));
            decodedOptionResultCounts.add(RtsPacketBuffer.readBoundedCount(buffer,
                    MAX_RESULT_COUNT, "option result count"));
            decodedOptionCraftable.add(buffer.readBoolean());
            decodedOptionSummaries.add(RtsPacketBuffer.readString(buffer,
                    MAX_SUMMARY_CHARS, "option summary"));
            decodedOptionMissing.add(RtsPacketBuffer.readString(buffer,
                    MAX_SUMMARY_CHARS, "option missing summary"));
        }

        recipeIds = immutable(decodedRecipeIds);
        resultItemIds = immutable(decodedResultIds);
        resultCounts = immutable(decodedCounts);
        craftable = immutable(decodedCraftable);
        missingSummaries = immutable(decodedMissing);
        recipeOptionCounts = immutable(decodedOptionCounts);
        optionRecipeIds = immutable(decodedOptionIds);
        optionResultCounts = immutable(decodedOptionResultCounts);
        optionCraftable = immutable(decodedOptionCraftable);
        optionSummaries = immutable(decodedOptionSummaries);
        optionMissingSummaries = immutable(decodedOptionMissing);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeString(buffer, safe(search), MAX_SEARCH_CHARS, "craftables search");
        buffer.writeBoolean(showUnavailable);
        RtsPacketBuffer.writeVarInt(buffer, bounded(offset, MAX_OFFSET, "craftables offset"));
        buffer.writeBoolean(append);
        buffer.writeBoolean(hasMore);

        int size = Math.min(MAX_RESULTS, Math.min(recipeIds.size(),
                Math.min(resultItemIds.size(), Math.min(resultCounts.size(),
                        Math.min(craftable.size(), Math.min(missingSummaries.size(),
                                recipeOptionCounts.size()))))));
        RtsPacketBuffer.writeVarInt(buffer, size);
        for (int i = 0; i < size; i++) {
            RtsPacketBuffer.writeString(buffer, safe(recipeIds.get(i)), MAX_RECIPE_ID_CHARS, "recipe id");
            RtsPacketBuffer.writeString(buffer, safe(resultItemIds.get(i)), MAX_ITEM_ID_CHARS, "result item id");
            RtsPacketBuffer.writeVarInt(buffer, bounded(integer(resultCounts.get(i)), MAX_RESULT_COUNT,
                    "result count"));
            buffer.writeBoolean(Boolean.TRUE.equals(craftable.get(i)));
            RtsPacketBuffer.writeString(buffer, safe(missingSummaries.get(i)), MAX_SUMMARY_CHARS,
                    "missing summary");
            RtsPacketBuffer.writeVarInt(buffer, bounded(integer(recipeOptionCounts.get(i)), MAX_OPTIONS,
                    "recipe option count"));
        }

        int optionSize = Math.min(MAX_OPTIONS, Math.min(optionRecipeIds.size(),
                Math.min(optionResultCounts.size(), Math.min(optionCraftable.size(),
                        Math.min(optionSummaries.size(), optionMissingSummaries.size())))));
        RtsPacketBuffer.writeVarInt(buffer, optionSize);
        for (int i = 0; i < optionSize; i++) {
            RtsPacketBuffer.writeString(buffer, safe(optionRecipeIds.get(i)), MAX_RECIPE_ID_CHARS,
                    "option recipe id");
            RtsPacketBuffer.writeVarInt(buffer, bounded(integer(optionResultCounts.get(i)),
                    MAX_RESULT_COUNT, "option result count"));
            buffer.writeBoolean(Boolean.TRUE.equals(optionCraftable.get(i)));
            RtsPacketBuffer.writeString(buffer, safe(optionSummaries.get(i)), MAX_SUMMARY_CHARS,
                    "option summary");
            RtsPacketBuffer.writeString(buffer, safe(optionMissingSummaries.get(i)), MAX_SUMMARY_CHARS,
                    "option missing summary");
        }
    }

    private static int bounded(int value, int maximum, String name) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException(name + " out of range");
        return value;
    }
    private static int integer(Integer value) { return value == null ? 0 : value.intValue(); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static <T> List<T> immutable(List<T> values) {
        return values == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}

package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2CRtsCraftablesPayload(
        String search,
        boolean showUnavailable,
        int offset,
        boolean append,
        boolean hasMore,
        List<String> recipeIds,
        List<String> resultItemIds,
        List<Integer> resultCounts,
        List<Boolean> craftable,
        List<String> missingSummaries,
        List<Integer> recipeOptionCounts,
        List<String> optionRecipeIds,
        List<Integer> optionResultCounts,
        List<Boolean> optionCraftable,
        List<String> optionSummaries,
        List<String> optionMissingSummaries) implements CustomPacketPayload {
    public static final Type<S2CRtsCraftablesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_craftables"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsCraftablesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.search() == null ? "" : payload.search(), 128);
                        buf.writeBoolean(payload.showUnavailable());
                        buf.writeVarInt(Math.max(0, payload.offset()));
                        buf.writeBoolean(payload.append());
                        buf.writeBoolean(payload.hasMore());
                        int size = Math.min(
                                payload.recipeIds().size(),
                                Math.min(
                                        payload.resultItemIds().size(),
                                        Math.min(
                                                payload.resultCounts().size(),
                                                Math.min(payload.craftable().size(), payload.missingSummaries().size()))));
                        buf.writeVarInt(size);
                        for (int i = 0; i < size; i++) {
                            buf.writeUtf(payload.recipeIds().get(i), 256);
                            buf.writeUtf(payload.resultItemIds().get(i), 128);
                            buf.writeVarInt(payload.resultCounts().get(i));
                            buf.writeBoolean(payload.craftable().get(i));
                            buf.writeUtf(payload.missingSummaries().get(i), 512);
                            int optionCount = i < payload.recipeOptionCounts().size() ? Math.max(0, payload.recipeOptionCounts().get(i)) : 0;
                            buf.writeVarInt(optionCount);
                        }
                        int flattenedOptionSize = Math.min(
                                payload.optionRecipeIds().size(),
                                Math.min(
                                        payload.optionResultCounts().size(),
                                        Math.min(
                                                payload.optionCraftable().size(),
                                                Math.min(payload.optionSummaries().size(), payload.optionMissingSummaries().size()))));
                        buf.writeVarInt(flattenedOptionSize);
                        for (int i = 0; i < flattenedOptionSize; i++) {
                            buf.writeUtf(payload.optionRecipeIds().get(i), 256);
                            buf.writeVarInt(payload.optionResultCounts().get(i));
                            buf.writeBoolean(payload.optionCraftable().get(i));
                            buf.writeUtf(payload.optionSummaries().get(i), 512);
                            buf.writeUtf(payload.optionMissingSummaries().get(i), 512);
                        }
                    },
                    (buf) -> {
                        String search = buf.readUtf(128);
                        boolean showUnavailable = buf.readBoolean();
                        int offset = buf.readVarInt();
                        boolean append = buf.readBoolean();
                        boolean hasMore = buf.readBoolean();
                        int size = buf.readVarInt();
                        List<String> recipeIds = new ArrayList<>(size);
                        List<String> resultItemIds = new ArrayList<>(size);
                        List<Integer> resultCounts = new ArrayList<>(size);
                        List<Boolean> craftable = new ArrayList<>(size);
                        List<String> missingSummaries = new ArrayList<>(size);
                        List<Integer> recipeOptionCounts = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            recipeIds.add(buf.readUtf(256));
                            resultItemIds.add(buf.readUtf(128));
                            resultCounts.add(buf.readVarInt());
                            craftable.add(buf.readBoolean());
                            missingSummaries.add(buf.readUtf(512));
                            recipeOptionCounts.add(buf.readVarInt());
                        }
                        int flattenedOptionSize = buf.readVarInt();
                        List<String> optionRecipeIds = new ArrayList<>(flattenedOptionSize);
                        List<Integer> optionResultCounts = new ArrayList<>(flattenedOptionSize);
                        List<Boolean> optionCraftable = new ArrayList<>(flattenedOptionSize);
                        List<String> optionSummaries = new ArrayList<>(flattenedOptionSize);
                        List<String> optionMissingSummaries = new ArrayList<>(flattenedOptionSize);
                        for (int i = 0; i < flattenedOptionSize; i++) {
                            optionRecipeIds.add(buf.readUtf(256));
                            optionResultCounts.add(buf.readVarInt());
                            optionCraftable.add(buf.readBoolean());
                            optionSummaries.add(buf.readUtf(512));
                            optionMissingSummaries.add(buf.readUtf(512));
                        }
                        return new S2CRtsCraftablesPayload(
                                search,
                                showUnavailable,
                                offset,
                                append,
                                hasMore,
                                recipeIds,
                                resultItemIds,
                                resultCounts,
                                craftable,
                                missingSummaries,
                                recipeOptionCounts,
                                optionRecipeIds,
                                optionResultCounts,
                                optionCraftable,
                                optionSummaries,
                                optionMissingSummaries);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

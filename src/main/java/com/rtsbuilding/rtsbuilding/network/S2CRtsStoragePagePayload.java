package com.rtsbuilding.rtsbuilding.network;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record S2CRtsStoragePagePayload(
        boolean linked,
        String linkedName,
        List<Long> linkedPositions,
        int page,
        int totalPages,
        int totalEntries,
        String search,
        String category,
        byte sort,
        boolean ascending,
        boolean autoStoreMinedDrops,
        List<String> categories,
        List<ItemStack> itemStacks,
        List<Long> counts,
        List<String> totalItemIds,
        List<Long> totalItemCounts,
        List<String> fluidIds,
        List<Long> fluidAmounts,
        List<Long> fluidCapacities,
        List<String> recentIds,
        List<Long> recentAmounts,
        List<Long> recentCapacities,
        List<Byte> recentKinds,
        List<String> quickSlotItemIds,
        List<ItemStack> quickSlotPreviews,
        List<String> guiBindingLabels,
        List<String> guiBindingItemIds,
        boolean funnelEnabled,
        List<String> funnelBufferItemIds,
        List<Long> funnelBufferCounts) implements CustomPacketPayload {
    public static final byte RECENT_ITEM_PLACED = 0;
    public static final byte RECENT_ITEM_USED = 1;
    public static final byte RECENT_ITEM_CRAFTED = 2;
    public static final byte RECENT_FLUID_PLACED = 3;
    public static final byte RECENT_FLUID_USED = 4;
    public static final byte RECENT_FLUID_CRAFTED = 5;

    public static final Type<S2CRtsStoragePagePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_storage_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsStoragePagePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.linked());
                buf.writeUtf(payload.linkedName(), 128);
                buf.writeVarInt(payload.linkedPositions().size());
                for (Long packedPos : payload.linkedPositions()) {
                    buf.writeLong(packedPos == null ? 0L : packedPos.longValue());
                }
                buf.writeVarInt(payload.page());
                buf.writeVarInt(payload.totalPages());
                buf.writeVarInt(payload.totalEntries());
                buf.writeUtf(payload.search(), 128);
                buf.writeUtf(payload.category(), 128);
                buf.writeByte(payload.sort());
                buf.writeBoolean(payload.ascending());
                buf.writeBoolean(payload.autoStoreMinedDrops());

                buf.writeVarInt(payload.categories().size());
                for (String category : payload.categories()) {
                    buf.writeUtf(category, 128);
                }

                int size = Math.min(payload.itemStacks().size(), payload.counts().size());
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeItem(payload.itemStacks().get(i));
                    buf.writeVarLong(payload.counts().get(i));
                }

                int totalItemSize = Math.min(payload.totalItemIds().size(), payload.totalItemCounts().size());
                buf.writeVarInt(totalItemSize);
                for (int i = 0; i < totalItemSize; i++) {
                    buf.writeUtf(payload.totalItemIds().get(i), 128);
                    buf.writeVarLong(payload.totalItemCounts().get(i));
                }

                int fluidSize = Math.min(payload.fluidIds().size(),
                        Math.min(payload.fluidAmounts().size(), payload.fluidCapacities().size()));
                buf.writeVarInt(fluidSize);
                for (int i = 0; i < fluidSize; i++) {
                    buf.writeUtf(payload.fluidIds().get(i), 128);
                    buf.writeVarLong(payload.fluidAmounts().get(i));
                    buf.writeVarLong(payload.fluidCapacities().get(i));
                }

                int recentSize = Math.min(
                        payload.recentIds().size(),
                        Math.min(
                                payload.recentAmounts().size(),
                                Math.min(payload.recentCapacities().size(), payload.recentKinds().size())));
                buf.writeVarInt(recentSize);
                for (int i = 0; i < recentSize; i++) {
                    buf.writeUtf(payload.recentIds().get(i), 128);
                    buf.writeVarLong(payload.recentAmounts().get(i));
                    buf.writeVarLong(payload.recentCapacities().get(i));
                    buf.writeByte(payload.recentKinds().get(i));
                }

                buf.writeVarInt(payload.quickSlotItemIds().size());
                for (String quickSlotItemId : payload.quickSlotItemIds()) {
                    buf.writeUtf(quickSlotItemId == null ? "" : quickSlotItemId, 128);
                }
                buf.writeVarInt(payload.quickSlotPreviews().size());
                for (ItemStack quickSlotPreview : payload.quickSlotPreviews()) {
                    ItemStack preview = quickSlotPreview == null ? ItemStack.EMPTY : quickSlotPreview;
                    buf.writeBoolean(!preview.isEmpty());
                    if (!preview.isEmpty()) {
                        buf.writeItem(preview.copyWithCount(1));
                    }
                }

                buf.writeVarInt(payload.guiBindingLabels().size());
                for (String guiBindingLabel : payload.guiBindingLabels()) {
                    buf.writeUtf(guiBindingLabel == null ? "" : guiBindingLabel, 128);
                }

                buf.writeVarInt(payload.guiBindingItemIds().size());
                for (String guiBindingItemId : payload.guiBindingItemIds()) {
                    buf.writeUtf(guiBindingItemId == null ? "" : guiBindingItemId, 128);
                }

                buf.writeBoolean(payload.funnelEnabled());
                int funnelBufferSize = Math.min(payload.funnelBufferItemIds().size(), payload.funnelBufferCounts().size());
                buf.writeVarInt(funnelBufferSize);
                for (int i = 0; i < funnelBufferSize; i++) {
                    buf.writeUtf(payload.funnelBufferItemIds().get(i), 128);
                    buf.writeVarLong(payload.funnelBufferCounts().get(i));
                }
            },
            (buf) -> {
                boolean linked = buf.readBoolean();
                String linkedName = buf.readUtf(128);
                int linkedPosSize = buf.readVarInt();
                List<Long> linkedPositions = new ArrayList<>(linkedPosSize);
                for (int i = 0; i < linkedPosSize; i++) {
                    linkedPositions.add(buf.readLong());
                }
                int page = buf.readVarInt();
                int totalPages = buf.readVarInt();
                int totalEntries = buf.readVarInt();
                String search = buf.readUtf(128);
                String category = buf.readUtf(128);
                byte sort = buf.readByte();
                boolean ascending = buf.readBoolean();
                boolean autoStoreMinedDrops = buf.readBoolean();
                int categorySize = buf.readVarInt();
                List<String> categories = new ArrayList<>(categorySize);
                for (int i = 0; i < categorySize; i++) {
                    categories.add(buf.readUtf(128));
                }
                int size = buf.readVarInt();
                List<ItemStack> itemStacks = new ArrayList<>(size);
                List<Long> counts = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    itemStacks.add(buf.readItem());
                    counts.add(buf.readVarLong());
                }
                int totalItemSize = buf.readVarInt();
                List<String> totalItemIds = new ArrayList<>(totalItemSize);
                List<Long> totalItemCounts = new ArrayList<>(totalItemSize);
                for (int i = 0; i < totalItemSize; i++) {
                    totalItemIds.add(buf.readUtf(128));
                    totalItemCounts.add(buf.readVarLong());
                }
                int fluidSize = buf.readVarInt();
                List<String> fluidIds = new ArrayList<>(fluidSize);
                List<Long> fluidAmounts = new ArrayList<>(fluidSize);
                List<Long> fluidCapacities = new ArrayList<>(fluidSize);
                for (int i = 0; i < fluidSize; i++) {
                    fluidIds.add(buf.readUtf(128));
                    fluidAmounts.add(buf.readVarLong());
                    fluidCapacities.add(buf.readVarLong());
                }
                int recentSize = buf.readVarInt();
                List<String> recentIds = new ArrayList<>(recentSize);
                List<Long> recentAmounts = new ArrayList<>(recentSize);
                List<Long> recentCapacities = new ArrayList<>(recentSize);
                List<Byte> recentKinds = new ArrayList<>(recentSize);
                for (int i = 0; i < recentSize; i++) {
                    recentIds.add(buf.readUtf(128));
                    recentAmounts.add(buf.readVarLong());
                    recentCapacities.add(buf.readVarLong());
                    recentKinds.add(buf.readByte());
                }
                int quickSlotSize = buf.readVarInt();
                List<String> quickSlotItemIds = new ArrayList<>(quickSlotSize);
                for (int i = 0; i < quickSlotSize; i++) {
                    quickSlotItemIds.add(buf.readUtf(128));
                }
                int quickSlotPreviewSize = buf.readVarInt();
                List<ItemStack> quickSlotPreviews = new ArrayList<>(quickSlotPreviewSize);
                for (int i = 0; i < quickSlotPreviewSize; i++) {
                    quickSlotPreviews.add(buf.readBoolean() ? buf.readItem() : ItemStack.EMPTY);
                }
                int guiBindingSize = buf.readVarInt();
                List<String> guiBindingLabels = new ArrayList<>(guiBindingSize);
                for (int i = 0; i < guiBindingSize; i++) {
                    guiBindingLabels.add(buf.readUtf(128));
                }
                int guiBindingItemIdSize = buf.readVarInt();
                List<String> guiBindingItemIds = new ArrayList<>(guiBindingItemIdSize);
                for (int i = 0; i < guiBindingItemIdSize; i++) {
                    guiBindingItemIds.add(buf.readUtf(128));
                }
                boolean funnelEnabled = buf.readBoolean();
                int funnelBufferSize = buf.readVarInt();
                List<String> funnelBufferItemIds = new ArrayList<>(funnelBufferSize);
                List<Long> funnelBufferCounts = new ArrayList<>(funnelBufferSize);
                for (int i = 0; i < funnelBufferSize; i++) {
                    funnelBufferItemIds.add(buf.readUtf(128));
                    funnelBufferCounts.add(buf.readVarLong());
                }
                return new S2CRtsStoragePagePayload(
                        linked,
                        linkedName,
                        linkedPositions,
                        page,
                        totalPages,
                        totalEntries,
                        search,
                        category,
                        sort,
                        ascending,
                        autoStoreMinedDrops,
                        categories,
                        itemStacks,
                        counts,
                        totalItemIds,
                        totalItemCounts,
                        fluidIds,
                        fluidAmounts,
                        fluidCapacities,
                        recentIds,
                        recentAmounts,
                        recentCapacities,
                        recentKinds,
                        quickSlotItemIds,
                        quickSlotPreviews,
                        guiBindingLabels,
                        guiBindingItemIds,
                        funnelEnabled,
                        funnelBufferItemIds,
                        funnelBufferCounts);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

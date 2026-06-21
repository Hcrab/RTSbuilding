package com.rtsbuilding.rtsbuilding.network.storage;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record S2CRtsStoragePagePayload(
        boolean linked,
        String linkedName,
        List<Long> linkedPositions,
        List<String> linkedNames,
        List<Byte> linkedModes,
        List<Integer> linkedPriorities,
        List<String> linkedIconItemIds,
        List<Boolean> linkedWorldAvailable,
        int page,
        int totalPages,
        int totalEntries,
        String search,
        String category,
        byte sort,
        boolean ascending,
        boolean autoStoreMinedDrops,
        boolean useBdNetwork,
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

    public static final Type<S2CRtsStoragePagePayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_storage_page"), S2CRtsStoragePagePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsStoragePagePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.linked());
                buf.writeUtf(payload.linkedName(), 128);
                buf.writeVarInt(payload.linkedPositions().size());
                for (Long packedPos : payload.linkedPositions()) {
                    buf.writeLong(packedPos == null ? 0L : packedPos.longValue());
                }
                int linkedDetailSize = Math.min(
                        payload.linkedPositions().size(),
                        Math.min(payload.linkedNames().size(),
                                Math.min(payload.linkedModes().size(),
                                        Math.min(payload.linkedPriorities().size(),
                                                Math.min(payload.linkedIconItemIds().size(),
                                                        payload.linkedWorldAvailable().size())))));
                buf.writeVarInt(linkedDetailSize);
                for (int i = 0; i < linkedDetailSize; i++) {
                    buf.writeUtf(payload.linkedNames().get(i) == null ? "" : payload.linkedNames().get(i), 128);
                    buf.writeByte(payload.linkedModes().get(i) == null ? 0 : payload.linkedModes().get(i));
                    buf.writeVarInt(payload.linkedPriorities().get(i) == null ? 0 : payload.linkedPriorities().get(i));
                    buf.writeUtf(payload.linkedIconItemIds().get(i) == null ? "" : payload.linkedIconItemIds().get(i), 128);
                    buf.writeBoolean(Boolean.TRUE.equals(payload.linkedWorldAvailable().get(i)));
                }
                buf.writeVarInt(payload.page());
                buf.writeVarInt(payload.totalPages());
                buf.writeVarInt(payload.totalEntries());
                buf.writeUtf(payload.search(), 128);
                buf.writeUtf(payload.category(), 128);
                buf.writeByte(payload.sort());
                buf.writeBoolean(payload.ascending());
                buf.writeBoolean(payload.autoStoreMinedDrops());
                buf.writeBoolean(payload.useBdNetwork());

                buf.writeVarInt(payload.categories().size());
                for (String category : payload.categories()) {
                    buf.writeUtf(category, 128);
                }

                int size = Math.min(payload.itemStacks().size(), payload.counts().size());
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, payload.itemStacks().get(i));
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
                        com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, preview.copyWithCount(1));
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
                int linkedDetailSize = buf.readVarInt();
                List<String> linkedNames = new ArrayList<>(linkedDetailSize);
                List<Byte> linkedModes = new ArrayList<>(linkedDetailSize);
                List<Integer> linkedPriorities = new ArrayList<>(linkedDetailSize);
                List<String> linkedIconItemIds = new ArrayList<>(linkedDetailSize);
                List<Boolean> linkedWorldAvailable = new ArrayList<>(linkedDetailSize);
                for (int i = 0; i < linkedDetailSize; i++) {
                    linkedNames.add(buf.readUtf(128));
                    linkedModes.add(buf.readByte());
                    linkedPriorities.add(buf.readVarInt());
                    linkedIconItemIds.add(buf.readUtf(128));
                    linkedWorldAvailable.add(buf.readBoolean());
                }
                int page = buf.readVarInt();
                int totalPages = buf.readVarInt();
                int totalEntries = buf.readVarInt();
                String search = buf.readUtf(128);
                String category = buf.readUtf(128);
                byte sort = buf.readByte();
                boolean ascending = buf.readBoolean();
                boolean autoStoreMinedDrops = buf.readBoolean();
                boolean useBdNetwork = buf.readBoolean();
                int categorySize = buf.readVarInt();
                List<String> categories = new ArrayList<>(categorySize);
                for (int i = 0; i < categorySize; i++) {
                    categories.add(buf.readUtf(128));
                }
                int size = buf.readVarInt();
                List<ItemStack> itemStacks = new ArrayList<>(size);
                List<Long> counts = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    itemStacks.add(com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf));
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
                    quickSlotPreviews.add(buf.readBoolean() ? com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf) : ItemStack.EMPTY);
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
                        linkedNames,
                        linkedModes,
                        linkedPriorities,
                        linkedIconItemIds,
                        linkedWorldAvailable,
                        page,
                        totalPages,
                        totalEntries,
                        search,
                        category,
                        sort,
                        ascending,
                        autoStoreMinedDrops,
                        useBdNetwork,
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
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

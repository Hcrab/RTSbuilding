package com.rtsbuilding.rtsbuilding.network.craft;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsCraftFeedbackPayload(
        String itemId,
        int craftedCount,
        List<String> consumedItemIds,
        List<Integer> consumedCounts) implements CustomPacketPayload {
    public static final Type<S2CRtsCraftFeedbackPayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_craft_feedback"), S2CRtsCraftFeedbackPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsCraftFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.itemId() == null ? "" : payload.itemId(), 128);
                        buf.writeVarInt(Math.max(0, payload.craftedCount()));
                        int size = Math.min(
                                payload.consumedItemIds() == null ? 0 : payload.consumedItemIds().size(),
                                payload.consumedCounts() == null ? 0 : payload.consumedCounts().size());
                        buf.writeVarInt(size);
                        for (int i = 0; i < size; i++) {
                            buf.writeUtf(payload.consumedItemIds().get(i), 128);
                            buf.writeVarInt(Math.max(0, payload.consumedCounts().get(i)));
                        }
                    },
                    (buf) -> {
                        String itemId = buf.readUtf(128);
                        int craftedCount = buf.readVarInt();
                        int size = buf.readVarInt();
                        List<String> consumedItemIds = new ArrayList<>(size);
                        List<Integer> consumedCounts = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            consumedItemIds.add(buf.readUtf(128));
                            consumedCounts.add(buf.readVarInt());
                        }
                        return new S2CRtsCraftFeedbackPayload(itemId, craftedCount, consumedItemIds, consumedCounts);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

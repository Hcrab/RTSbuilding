package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record S2CRtsCraftFeedbackPayload(
        String itemId,
        int craftedCount,
        List<String> consumedItemIds,
        List<Integer> consumedCounts) implements CustomPacketPayload {
    public static final Type<S2CRtsCraftFeedbackPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_craft_feedback"));

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

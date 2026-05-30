package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsSetQuickSlotPayload(
        byte slot,
        String itemId) implements CustomPacketPayload {
    public static final Type<C2SRtsSetQuickSlotPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_set_quick_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetQuickSlotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.slot());
                buf.writeUtf(payload.itemId() == null ? "" : payload.itemId(), 128);
            },
            (buf) -> new C2SRtsSetQuickSlotPayload(
                    buf.readByte(),
                    buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

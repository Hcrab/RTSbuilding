package com.rtsbuilding.rtsbuilding.network.storage;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsStoreHotbarSlotPayload(byte slot) implements CustomPacketPayload {
    public static final Type<C2SRtsStoreHotbarSlotPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_store_hotbar_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsStoreHotbarSlotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.slot()),
            (buf) -> new C2SRtsStoreHotbarSlotPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

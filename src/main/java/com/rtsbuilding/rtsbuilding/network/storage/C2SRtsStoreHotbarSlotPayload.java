package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsStoreHotbarSlotPayload(byte slot) implements CustomPacketPayload {
    public static final Type<C2SRtsStoreHotbarSlotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_store_hotbar_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsStoreHotbarSlotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.slot()),
            (buf) -> new C2SRtsStoreHotbarSlotPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

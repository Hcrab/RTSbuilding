package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsFillInventoryPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsFillInventoryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_fill_inventory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsFillInventoryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            (buf) -> new C2SRtsFillInventoryPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

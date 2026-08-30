package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsRequestCullingStatePayload() implements CustomPacketPayload {
    public static final Type<C2SRtsRequestCullingStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_request_culling_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRequestCullingStatePayload> STREAM_CODEC =
            StreamCodec.unit(new C2SRtsRequestCullingStatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

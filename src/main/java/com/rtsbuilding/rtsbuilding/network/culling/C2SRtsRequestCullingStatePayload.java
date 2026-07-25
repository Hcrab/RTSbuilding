package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsRequestCullingStatePayload() implements CustomPacketPayload {
    public static final Type<C2SRtsRequestCullingStatePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_request_culling_state"),
            C2SRtsRequestCullingStatePayload.class);
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRequestCullingStatePayload> STREAM_CODEC =
            StreamCodec.unit(new C2SRtsRequestCullingStatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

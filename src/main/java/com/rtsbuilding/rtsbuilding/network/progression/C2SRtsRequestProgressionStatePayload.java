package com.rtsbuilding.rtsbuilding.network.progression;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsRequestProgressionStatePayload() implements CustomPacketPayload {
    public static final Type<C2SRtsRequestProgressionStatePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_request_progression_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRequestProgressionStatePayload> STREAM_CODEC =
            StreamCodec.unit(new C2SRtsRequestProgressionStatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

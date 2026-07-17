package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsRequestProgressionStatePayload() implements CustomPacketPayload {
    public static final Type<C2SRtsRequestProgressionStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_request_progression_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRequestProgressionStatePayload> STREAM_CODEC =
            StreamCodec.unit(new C2SRtsRequestProgressionStatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

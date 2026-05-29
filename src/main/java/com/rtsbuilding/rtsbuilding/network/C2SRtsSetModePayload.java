package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsSetModePayload(byte mode) implements CustomPacketPayload {
    public static final Type<C2SRtsSetModePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_set_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetModePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.mode()),
            (buf) -> new C2SRtsSetModePayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


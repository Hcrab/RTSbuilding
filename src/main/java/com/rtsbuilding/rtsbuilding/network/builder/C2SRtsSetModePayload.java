package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsSetModePayload(byte mode) implements CustomPacketPayload {
    public static final Type<C2SRtsSetModePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_set_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetModePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.mode()),
            (buf) -> new C2SRtsSetModePayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


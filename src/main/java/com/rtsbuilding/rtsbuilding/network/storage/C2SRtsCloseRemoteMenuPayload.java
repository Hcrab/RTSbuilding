package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsCloseRemoteMenuPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsCloseRemoteMenuPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_close_remote_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCloseRemoteMenuPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            (buf) -> new C2SRtsCloseRemoteMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsRequestPluginsPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsRequestPluginsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_request_plugins"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRequestPluginsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            (buf) -> new C2SRtsRequestPluginsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

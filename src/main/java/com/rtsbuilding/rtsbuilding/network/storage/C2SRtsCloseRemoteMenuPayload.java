package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsCloseRemoteMenuPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsCloseRemoteMenuPayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_close_remote_menu"), C2SRtsCloseRemoteMenuPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCloseRemoteMenuPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            (buf) -> new C2SRtsCloseRemoteMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

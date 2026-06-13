package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsSetBdNetworkPayload(
        boolean enabled) implements CustomPacketPayload {
    public static final Type<C2SRtsSetBdNetworkPayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_set_bd_network"), C2SRtsSetBdNetworkPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetBdNetworkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.enabled()),
            (buf) -> new C2SRtsSetBdNetworkPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

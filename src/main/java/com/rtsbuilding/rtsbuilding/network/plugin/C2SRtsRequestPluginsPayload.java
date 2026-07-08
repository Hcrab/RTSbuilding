package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsRequestPluginsPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsRequestPluginsPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_request_plugins"),
            C2SRtsRequestPluginsPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRequestPluginsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            (buf) -> new C2SRtsRequestPluginsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

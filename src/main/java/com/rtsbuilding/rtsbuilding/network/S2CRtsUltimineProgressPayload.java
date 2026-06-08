package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsUltimineProgressPayload(int processed, int total) implements CustomPacketPayload {
    public static final Type<S2CRtsUltimineProgressPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_ultimine_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsUltimineProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.processed());
                buf.writeVarInt(payload.total());
            },
            (buf) -> new S2CRtsUltimineProgressPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsPlaceFluidPayload(
        BlockPos clickedPos,
        byte face,
        double hitX,
        double hitY,
        double hitZ,
        boolean forcePlace,
        String fluidId,
        double rayOriginX,
        double rayOriginY,
        double rayOriginZ,
        double rayDirX,
        double rayDirY,
        double rayDirZ) implements CustomPacketPayload {
    public static final Type<C2SRtsPlaceFluidPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_place_fluid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPlaceFluidPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.clickedPos());
                buf.writeByte(payload.face());
                buf.writeDouble(payload.hitX());
                buf.writeDouble(payload.hitY());
                buf.writeDouble(payload.hitZ());
                buf.writeBoolean(payload.forcePlace());
                buf.writeUtf(payload.fluidId(), 128);
                buf.writeDouble(payload.rayOriginX());
                buf.writeDouble(payload.rayOriginY());
                buf.writeDouble(payload.rayOriginZ());
                buf.writeDouble(payload.rayDirX());
                buf.writeDouble(payload.rayDirY());
                buf.writeDouble(payload.rayDirZ());
            },
            (buf) -> new C2SRtsPlaceFluidPayload(
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readUtf(128),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

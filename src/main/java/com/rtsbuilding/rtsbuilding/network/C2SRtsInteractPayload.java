package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsInteractPayload(
        int entityId,
        BlockPos clickedPos,
        byte face,
        double hitX,
        double hitY,
        double hitZ,
        byte sourceType,
        byte toolSlot,
        String itemId,
        double rayOriginX,
        double rayOriginY,
        double rayOriginZ,
        double rayDirX,
        double rayDirY,
        double rayDirZ) implements CustomPacketPayload {
    public static final byte SOURCE_TOOL_SLOT = 0;
    public static final byte SOURCE_PIN_ITEM = 1;
    public static final int NO_ENTITY = -1;

    public static final Type<C2SRtsInteractPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_interact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsInteractPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeBlockPos(payload.clickedPos());
                buf.writeByte(payload.face());
                buf.writeDouble(payload.hitX());
                buf.writeDouble(payload.hitY());
                buf.writeDouble(payload.hitZ());
                buf.writeByte(payload.sourceType());
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.itemId(), 128);
                buf.writeDouble(payload.rayOriginX());
                buf.writeDouble(payload.rayOriginY());
                buf.writeDouble(payload.rayOriginZ());
                buf.writeDouble(payload.rayDirX());
                buf.writeDouble(payload.rayDirY());
                buf.writeDouble(payload.rayDirZ());
            },
            (buf) -> new C2SRtsInteractPayload(
                    buf.readInt(),
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readByte(),
                    buf.readByte(),
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

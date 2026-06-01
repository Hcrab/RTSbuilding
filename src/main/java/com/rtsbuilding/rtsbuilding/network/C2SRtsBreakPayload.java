package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsBreakPayload(
        BlockPos pos,
        byte face,
        boolean allowAdjacentFallback) implements CustomPacketPayload {
    public static final Type<C2SRtsBreakPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_break"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsBreakPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.face());
                buf.writeBoolean(payload.allowAdjacentFallback());
            },
            (buf) -> new C2SRtsBreakPayload(
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsMineProgressPayload(
        BlockPos pos,
        byte stage) implements CustomPacketPayload {
    public static final Type<S2CRtsMineProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_mine_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsMineProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.stage());
            },
            (buf) -> new S2CRtsMineProgressPayload(
                    buf.readBlockPos(),
                    buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

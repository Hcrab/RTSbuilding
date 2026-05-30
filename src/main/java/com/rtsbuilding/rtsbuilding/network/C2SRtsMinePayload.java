package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsMinePayload(
        BlockPos pos,
        byte face,
        boolean start,
        byte toolSlot,
        String toolItemId,
        boolean allowPlacedBlockRecovery) implements CustomPacketPayload {
    public static final Type<C2SRtsMinePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_mine"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsMinePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.face());
                buf.writeBoolean(payload.start());
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.toolItemId() == null ? "" : payload.toolItemId(), 256);
                buf.writeBoolean(payload.allowPlacedBlockRecovery());
            },
            (buf) -> new C2SRtsMinePayload(
                    buf.readBlockPos(),
                    buf.readByte(),
                    buf.readBoolean(),
                    buf.readByte(),
                    buf.readUtf(256),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

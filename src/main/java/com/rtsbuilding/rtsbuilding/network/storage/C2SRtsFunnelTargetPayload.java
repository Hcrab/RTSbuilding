package com.rtsbuilding.rtsbuilding.network.storage;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsFunnelTargetPayload(BlockPos target) implements CustomPacketPayload {
    public static final Type<C2SRtsFunnelTargetPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_funnel_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsFunnelTargetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.target()),
            (buf) -> new C2SRtsFunnelTargetPayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

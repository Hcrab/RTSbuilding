package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsLinkStoragePayload(BlockPos pos, byte linkMode) implements CustomPacketPayload {
    public static final byte MODE_BIDIRECTIONAL = 0;
    public static final byte MODE_EXTRACT_ONLY = 1;

    public static final Type<C2SRtsLinkStoragePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_link_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsLinkStoragePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeByte(payload.linkMode());
            },
            (buf) -> new C2SRtsLinkStoragePayload(buf.readBlockPos(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

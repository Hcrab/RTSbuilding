package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsUnlinkStoragePayload(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
    public static final Type<C2SRtsUnlinkStoragePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_unlink_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUnlinkStoragePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeResourceLocation(payload.dimension());
                buf.writeBlockPos(payload.pos());
            },
            (buf) -> new C2SRtsUnlinkStoragePayload(buf.readResourceLocation(), buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

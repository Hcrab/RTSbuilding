package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 紧凑的批量链接意图；服务端会按选区重新发现可链接端点。 */
public record C2SRtsBatchLinkStoragePayload(
        BlockPos first, BlockPos second, byte linkMode) implements CustomPacketPayload {
    public static final Type<C2SRtsBatchLinkStoragePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_batch_link_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsBatchLinkStoragePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.first());
                        buf.writeBlockPos(payload.second());
                        buf.writeByte(payload.linkMode());
                    },
                    buf -> new C2SRtsBatchLinkStoragePayload(
                            buf.readBlockPos(), buf.readBlockPos(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

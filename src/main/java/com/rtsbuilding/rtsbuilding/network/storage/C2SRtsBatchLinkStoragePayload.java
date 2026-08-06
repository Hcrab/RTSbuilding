package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** 紧凑的批量链接意图；服务端会按范围重新发现储存端点。 */
public record C2SRtsBatchLinkStoragePayload(
        BlockPos first, BlockPos second, byte linkMode) implements CustomPacketPayload {
    public static final Type<C2SRtsBatchLinkStoragePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_batch_link_storage"),
            C2SRtsBatchLinkStoragePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsBatchLinkStoragePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.first());
                        buf.writeBlockPos(payload.second());
                        buf.writeByte(payload.linkMode());
                    },
                    buf -> new C2SRtsBatchLinkStoragePayload(
                            buf.readBlockPos().immutable(),
                            buf.readBlockPos().immutable(),
                            buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

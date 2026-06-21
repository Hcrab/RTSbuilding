package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端到客户端：挂起放置任务的扫描结果。
 */
public record S2CRtsResumePlacementScanPayload(
        String itemId,
        String itemLabel,
        int totalRemaining,
        int alreadyPlacedCount,
        int conflictCount,
        long availableItems,
        int neededItems,
        long missingItems,
        int workflowEntryId) implements CustomPacketPayload {
    public static final Type<S2CRtsResumePlacementScanPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_resume_placement_scan"),
            S2CRtsResumePlacementScanPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsResumePlacementScanPayload> STREAM_CODEC =
            StreamCodec.of(S2CRtsResumePlacementScanPayload::encode, S2CRtsResumePlacementScanPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CRtsResumePlacementScanPayload payload) {
        buf.writeUtf(payload.itemId() == null ? "" : payload.itemId());
        buf.writeUtf(payload.itemLabel() == null ? "" : payload.itemLabel());
        buf.writeInt(payload.totalRemaining());
        buf.writeInt(payload.alreadyPlacedCount());
        buf.writeInt(payload.conflictCount());
        buf.writeLong(payload.availableItems());
        buf.writeInt(payload.neededItems());
        buf.writeLong(payload.missingItems());
        buf.writeInt(payload.workflowEntryId());
    }

    private static S2CRtsResumePlacementScanPayload decode(RegistryFriendlyByteBuf buf) {
        return new S2CRtsResumePlacementScanPayload(
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readLong(),
                buf.readInt(),
                buf.readLong(),
                buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

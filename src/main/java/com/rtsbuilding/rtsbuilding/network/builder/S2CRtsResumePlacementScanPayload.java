package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端→客户端：搁置放置作业的扫描结果。
 * <p>
 * 承载 {@link com.rtsbuilding.rtsbuilding.server.service.RtsResumeScanResult} 的所有字段，
 * 使客户端面板能够展示所需方块数、可用数、冲突格数等统计。
 *
 * @param itemId            正在放置的物品 ID
 * @param itemLabel         物品显示名称
 * @param totalRemaining    剩余总位置数
 * @param alreadyPlacedCount 已存在的同种方块数
 * @param conflictCount     冲突格数
 * @param availableItems    存储中可用物品数
 * @param neededItems       实际需要物品数
 * @param missingItems      缺少的物品数（<=0 表示足够）
 * @param workflowEntryId   目标工作流条目 ID
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
        int workflowEntryId
) implements CustomPacketPayload {
    public static final Type<S2CRtsResumePlacementScanPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_resume_placement_scan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsResumePlacementScanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.itemId());
                buf.writeUtf(payload.itemLabel());
                buf.writeInt(payload.totalRemaining());
                buf.writeInt(payload.alreadyPlacedCount());
                buf.writeInt(payload.conflictCount());
                buf.writeLong(payload.availableItems());
                buf.writeInt(payload.neededItems());
                buf.writeLong(payload.missingItems());
                buf.writeInt(payload.workflowEntryId());
            },
            (buf) -> new S2CRtsResumePlacementScanPayload(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

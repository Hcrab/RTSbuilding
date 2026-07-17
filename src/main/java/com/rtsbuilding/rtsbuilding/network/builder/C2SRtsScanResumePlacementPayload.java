package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：请求扫描当前挂起放置作业的剩余位置。
 * <p>
 * 服务端收到后执行 {@link com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService#scanPendingJob}，
 * 并通过 {@link S2CRtsResumePlacementScanPayload} 返回扫描结果。
 *
 * @param workflowEntryId 目标工作流条目 ID，用于找到对应的挂起作业
 */
public record C2SRtsScanResumePlacementPayload(int workflowEntryId) implements CustomPacketPayload {
    public static final Type<C2SRtsScanResumePlacementPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_scan_resume_placement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsScanResumePlacementPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.workflowEntryId()),
            (buf) -> new C2SRtsScanResumePlacementPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

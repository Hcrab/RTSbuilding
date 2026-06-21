package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端到服务端：请求扫描一个挂起的放置工作流。
 *
 * <p>扫描只统计剩余位置、材料缺口和冲突方块，不直接恢复任务。玩家确认策略后
 * 再发送 {@link C2SRtsResumePlacementActionPayload}。</p>
 */
public record C2SRtsScanResumePlacementPayload(int workflowEntryId) implements CustomPacketPayload {
    public static final Type<C2SRtsScanResumePlacementPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_scan_resume_placement"),
            C2SRtsScanResumePlacementPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsScanResumePlacementPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeInt(payload.workflowEntryId()),
                    buf -> new C2SRtsScanResumePlacementPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：请求扫描挂起的蓝图作业的剩余材料需求。
 * <p>
 * 服务端收到后遍历蓝图剩余方块的材料需求，检查存储可用性，
 * 并通过 {@link S2CRtsBlueprintResumeScanPayload} 返回扫描结果。
 *
 * @param workflowEntryId 目标工作流条目 ID
 */
public record C2SRtsScanBlueprintResumePayload(int workflowEntryId) implements CustomPacketPayload {
    public static final Type<C2SRtsScanBlueprintResumePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_scan_blueprint_resume"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsScanBlueprintResumePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.workflowEntryId()),
            (buf) -> new C2SRtsScanBlueprintResumePayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

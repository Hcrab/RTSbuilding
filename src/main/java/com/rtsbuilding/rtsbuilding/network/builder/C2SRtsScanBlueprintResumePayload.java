package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

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
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_scan_blueprint_resume"),
            C2SRtsScanBlueprintResumePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsScanBlueprintResumePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.workflowEntryId()),
            (buf) -> new C2SRtsScanBlueprintResumePayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端到服务端：用玩家选择的策略恢复挂起放置任务。
 *
 * @param strategy        0=跳过已完成/冲突格，1=覆盖冲突格
 * @param workflowEntryId 要恢复的工作流条目 ID
 */
public record C2SRtsResumePlacementActionPayload(int strategy, int workflowEntryId) implements CustomPacketPayload {
    public static final Type<C2SRtsResumePlacementActionPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_resume_placement_action"),
            C2SRtsResumePlacementActionPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsResumePlacementActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.strategy());
                        buf.writeInt(payload.workflowEntryId());
                    },
                    buf -> new C2SRtsResumePlacementActionPayload(buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

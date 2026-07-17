package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：重启搁置放置作业，并附带冲突处理策略。
 * <p>
 * 玩家在 {@code RtsResumePlacementPanel} 中点击重启时发送。
 *
 * @param strategy 0=跳过冲突格后重启，1=覆盖放置（破坏冲突方块后放置）
 * @param workflowEntryId 目标工作流条目 ID，用于找到对应的挂起作业
 */
public record C2SRtsResumePlacementActionPayload(int strategy, int workflowEntryId) implements CustomPacketPayload {
    public static final Type<C2SRtsResumePlacementActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_resume_placement_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsResumePlacementActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.strategy());
                buf.writeInt(payload.workflowEntryId());
            },
            (buf) -> new C2SRtsResumePlacementActionPayload(buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

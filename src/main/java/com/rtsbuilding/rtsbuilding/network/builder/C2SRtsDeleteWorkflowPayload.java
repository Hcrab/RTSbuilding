package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：删除/取消指定工作流。
 *
 * <p>使用不可变的工作流条目 ID（entryId）而非位置索引，
 * 确保即使工作流列表在客户端和服务器之间不同步时也能正确删除。</p>
 *
 * @param workflowEntryId 要删除的工作流条目的不可变 ID
 */
public record C2SRtsDeleteWorkflowPayload(int workflowEntryId) implements CustomPacketPayload {
    public static final Type<C2SRtsDeleteWorkflowPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_delete_workflow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsDeleteWorkflowPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            C2SRtsDeleteWorkflowPayload::workflowEntryId,
            C2SRtsDeleteWorkflowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

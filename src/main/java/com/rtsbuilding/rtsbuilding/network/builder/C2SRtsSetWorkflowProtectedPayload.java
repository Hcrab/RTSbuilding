package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端请求设置某个工作流是否“不被覆盖”。
 *
 * <p>客户端发送目标状态而不是简单翻转，避免重复点击或延迟同步时把状态翻到反面。</p>
 */
public record C2SRtsSetWorkflowProtectedPayload(
        int workflowEntryId,
        boolean protectedWorkflow) implements CustomPacketPayload {

    public static final Type<C2SRtsSetWorkflowProtectedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_set_workflow_protected"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetWorkflowProtectedPayload> STREAM_CODEC =
            StreamCodec.of(
                    C2SRtsSetWorkflowProtectedPayload::encode,
                    C2SRtsSetWorkflowProtectedPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, C2SRtsSetWorkflowProtectedPayload payload) {
        buf.writeInt(payload.workflowEntryId());
        buf.writeBoolean(payload.protectedWorkflow());
    }

    private static C2SRtsSetWorkflowProtectedPayload decode(RegistryFriendlyByteBuf buf) {
        return new C2SRtsSetWorkflowProtectedPayload(buf.readInt(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

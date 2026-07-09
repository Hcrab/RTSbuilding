package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端请求设置某个工作流是否不被自动覆盖。
 */
public record C2SRtsSetWorkflowProtectedPayload(
        int workflowEntryId,
        boolean protectedWorkflow) implements CustomPacketPayload {

    public static final Type<C2SRtsSetWorkflowProtectedPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_set_workflow_protected"),
            C2SRtsSetWorkflowProtectedPayload.class);

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

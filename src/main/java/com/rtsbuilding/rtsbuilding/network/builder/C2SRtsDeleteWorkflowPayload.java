package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端到服务端：删除/取消指定工作流。
 */
public record C2SRtsDeleteWorkflowPayload(int workflowEntryId) implements CustomPacketPayload {
    public static final Type<C2SRtsDeleteWorkflowPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_delete_workflow"),
            C2SRtsDeleteWorkflowPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsDeleteWorkflowPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeInt(payload.workflowEntryId()),
                    buf -> new C2SRtsDeleteWorkflowPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

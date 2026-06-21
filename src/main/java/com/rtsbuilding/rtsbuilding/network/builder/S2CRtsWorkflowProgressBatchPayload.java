package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端到客户端的工作流批量进度同步包，避免多个任务时一条条刷包。
 */
public record S2CRtsWorkflowProgressBatchPayload(
        List<S2CRtsWorkflowProgressPayload> entries) implements CustomPacketPayload {

    public static final Type<S2CRtsWorkflowProgressBatchPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_workflow_progress_batch"),
            S2CRtsWorkflowProgressBatchPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsWorkflowProgressBatchPayload> STREAM_CODEC =
            StreamCodec.of(S2CRtsWorkflowProgressBatchPayload::encode, S2CRtsWorkflowProgressBatchPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CRtsWorkflowProgressBatchPayload payload) {
        List<S2CRtsWorkflowProgressPayload> entries = payload.entries() == null ? List.of() : payload.entries();
        buf.writeInt(entries.size());
        for (S2CRtsWorkflowProgressPayload entry : entries) {
            S2CRtsWorkflowProgressPayload.STREAM_CODEC.encode(buf, entry);
        }
    }

    private static S2CRtsWorkflowProgressBatchPayload decode(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, buf.readInt());
        List<S2CRtsWorkflowProgressPayload> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(S2CRtsWorkflowProgressPayload.STREAM_CODEC.decode(buf));
        }
        return new S2CRtsWorkflowProgressBatchPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 多个工作流槽位的批量同步；字段编码与单条负载共用同一协议函数。 */
public record S2CRtsWorkflowProgressBatchPayload(
        List<S2CRtsWorkflowProgressPayload> entries) implements CustomPacketPayload {

    public static final Type<S2CRtsWorkflowProgressBatchPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_workflow_progress_batch"),
            S2CRtsWorkflowProgressBatchPayload.class);
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsWorkflowProgressBatchPayload> STREAM_CODEC =
            StreamCodec.of(S2CRtsWorkflowProgressBatchPayload::encode, S2CRtsWorkflowProgressBatchPayload::decode);

    public S2CRtsWorkflowProgressBatchPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT) {
            throw new IllegalArgumentException("workflow batch 数量超出协议预算");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buf, S2CRtsWorkflowProgressBatchPayload payload) {
        buf.writeVarInt(payload.entries().size());
        for (S2CRtsWorkflowProgressPayload entry : payload.entries()) {
            S2CRtsWorkflowProgressPayload.writeFields(buf, entry);
        }
    }

    private static S2CRtsWorkflowProgressBatchPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT) {
            throw new IllegalArgumentException("workflow batch 数量超出协议预算");
        }
        List<S2CRtsWorkflowProgressPayload> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(S2CRtsWorkflowProgressPayload.readFields(buf));
        return new S2CRtsWorkflowProgressBatchPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

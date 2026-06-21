package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端到服务端：切换指定工作流条目的暂停状态。
 */
public record C2SRtsPauseWorkflowPayload(int entryId) implements CustomPacketPayload {
    public static final Type<C2SRtsPauseWorkflowPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_pause_workflow"),
            C2SRtsPauseWorkflowPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsPauseWorkflowPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeInt(payload.entryId()),
                    buf -> new C2SRtsPauseWorkflowPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端请求服务端提交当前玩家所有已冻结、等待执行的放置作业。
 *
 * <p>载荷本身不携带可伪造的任务数据；服务端只从自己的会话与持久化任务仓库读取权威状态。
 */
public record C2SRtsSubmitPendingPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsSubmitPendingPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_submit_pending"),
            C2SRtsSubmitPendingPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSubmitPendingPayload> STREAM_CODEC =
            StreamCodec.unit(new C2SRtsSubmitPendingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

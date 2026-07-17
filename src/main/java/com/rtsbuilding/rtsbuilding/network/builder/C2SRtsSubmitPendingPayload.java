package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：提交所有挂起的放置作业。
 */
public record C2SRtsSubmitPendingPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsSubmitPendingPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_submit_pending"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSubmitPendingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            (buf) -> new C2SRtsSubmitPendingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

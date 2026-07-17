package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端→服务端：请求撤回一次操作。
 */
public record C2SRtsUndoPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsUndoPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_undo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUndoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            (buf) -> new C2SRtsUndoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

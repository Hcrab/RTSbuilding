package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端到服务端：请求重做最近一次已撤回的创造模式操作。 */
public record C2SRtsRedoPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsRedoPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_redo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsRedoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            (buf) -> new C2SRtsRedoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

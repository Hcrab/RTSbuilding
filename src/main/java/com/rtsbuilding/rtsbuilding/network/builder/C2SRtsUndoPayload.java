package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端：请求撤回一次操作。
 */
public record C2SRtsUndoPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsUndoPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_undo"), C2SRtsUndoPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUndoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            (buf) -> new C2SRtsUndoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

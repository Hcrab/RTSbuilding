package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsReturnCarriedPayload(
        String itemId,
        int amount) implements CustomPacketPayload {
    public static final Type<C2SRtsReturnCarriedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_return_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsReturnCarriedPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.itemId(), 128);
                buf.writeVarInt(payload.amount());
            },
            (buf) -> new C2SRtsReturnCarriedPayload(
                    buf.readUtf(128),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

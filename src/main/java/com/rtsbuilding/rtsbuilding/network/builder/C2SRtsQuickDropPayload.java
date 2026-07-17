package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SRtsQuickDropPayload(
        String itemId,
        byte amount,
        double dropX,
        double dropY,
        double dropZ) implements CustomPacketPayload {
    public static final Type<C2SRtsQuickDropPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_quick_drop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsQuickDropPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.itemId(), 128);
                        buf.writeByte(payload.amount());
                        buf.writeDouble(payload.dropX());
                        buf.writeDouble(payload.dropY());
                        buf.writeDouble(payload.dropZ());
                    },
                    (buf) -> new C2SRtsQuickDropPayload(
                            buf.readUtf(128),
                            buf.readByte(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

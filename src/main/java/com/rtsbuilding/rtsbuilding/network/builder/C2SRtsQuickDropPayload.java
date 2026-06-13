package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsQuickDropPayload(
        String itemId,
        byte amount,
        double dropX,
        double dropY,
        double dropZ) implements CustomPacketPayload {
    public static final Type<C2SRtsQuickDropPayload> TYPE = new Type<>(new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_quick_drop"), C2SRtsQuickDropPayload.class);

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

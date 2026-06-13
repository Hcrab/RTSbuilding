package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsReturnCarriedPayload(
        String itemId,
        int amount) implements CustomPacketPayload {
    public static final Type<C2SRtsReturnCarriedPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_return_carried"), C2SRtsReturnCarriedPayload.class);

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

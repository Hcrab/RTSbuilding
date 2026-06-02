package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsJeiTransferPayload(
        String recipeId,
        boolean maxTransfer,
        boolean clearGridFirst) implements CustomPacketPayload {
    public static final Type<C2SRtsJeiTransferPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_jei_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsJeiTransferPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.recipeId(), 256);
                        buf.writeBoolean(payload.maxTransfer());
                        buf.writeBoolean(payload.clearGridFirst());
                    },
                    (buf) -> new C2SRtsJeiTransferPayload(
                            buf.readUtf(256),
                            buf.readBoolean(),
                            buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

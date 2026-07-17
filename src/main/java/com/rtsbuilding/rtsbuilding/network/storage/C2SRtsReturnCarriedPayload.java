package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsReturnCarriedPayload(
        String itemId,
        int amount,
        ItemStack carried) implements CustomPacketPayload {
    public static final Type<C2SRtsReturnCarriedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_return_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsReturnCarriedPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.itemId(), 128);
                buf.writeVarInt(payload.amount());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.carried());
            },
            (buf) -> new C2SRtsReturnCarriedPayload(
                    buf.readUtf(128),
                    buf.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

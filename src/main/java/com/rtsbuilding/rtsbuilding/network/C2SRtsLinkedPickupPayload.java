package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsLinkedPickupPayload(
        ItemStack prototype,
        int amount) implements CustomPacketPayload {
    public static final Type<C2SRtsLinkedPickupPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_linked_pickup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsLinkedPickupPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ItemStack.STREAM_CODEC.encode(buf, payload.prototype());
                buf.writeVarInt(payload.amount());
            },
            (buf) -> new C2SRtsLinkedPickupPayload(
                    ItemStack.STREAM_CODEC.decode(buf),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

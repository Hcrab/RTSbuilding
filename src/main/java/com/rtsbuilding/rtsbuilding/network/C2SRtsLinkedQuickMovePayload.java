package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsLinkedQuickMovePayload(ItemStack prototype) implements CustomPacketPayload {
    public static final Type<C2SRtsLinkedQuickMovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_linked_quick_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsLinkedQuickMovePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> ItemStack.STREAM_CODEC.encode(buf, payload.prototype()),
            (buf) -> new C2SRtsLinkedQuickMovePayload(ItemStack.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

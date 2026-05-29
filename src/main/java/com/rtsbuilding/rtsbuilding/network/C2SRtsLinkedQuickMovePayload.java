package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsLinkedQuickMovePayload(ItemStack prototype) implements CustomPacketPayload {
    public static final Type<C2SRtsLinkedQuickMovePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_linked_quick_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsLinkedQuickMovePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeItem(payload.prototype()),
            (buf) -> new C2SRtsLinkedQuickMovePayload(buf.readItem()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

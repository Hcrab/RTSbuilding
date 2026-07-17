package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record C2SRtsSetQuickSlotPayload(
        byte slot,
        String itemId,
        ItemStack previewStack) implements CustomPacketPayload {
    public static final Type<C2SRtsSetQuickSlotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_set_quick_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetQuickSlotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.slot());
                buf.writeUtf(payload.itemId() == null ? "" : payload.itemId(), 128);
                ItemStack preview = payload.previewStack() == null ? ItemStack.EMPTY : payload.previewStack();
                buf.writeBoolean(!preview.isEmpty());
                if (!preview.isEmpty()) {
                    ItemStack.STREAM_CODEC.encode(buf, preview.copyWithCount(1));
                }
            },
            (buf) -> new C2SRtsSetQuickSlotPayload(
                    buf.readByte(),
                    buf.readUtf(128),
                    buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

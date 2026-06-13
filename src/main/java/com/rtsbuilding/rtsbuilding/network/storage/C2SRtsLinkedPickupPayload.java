package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record C2SRtsLinkedPickupPayload(
        ItemStack prototype,
        int amount) implements CustomPacketPayload {
    public static final Type<C2SRtsLinkedPickupPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_linked_pickup"), C2SRtsLinkedPickupPayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsLinkedPickupPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.writeItem(buf, payload.prototype());
                buf.writeVarInt(payload.amount());
            },
            (buf) -> new C2SRtsLinkedPickupPayload(
                    com.rtsbuilding.rtsbuilding.forgecompat.network.RtsForgeBufCodecs.readItem(buf),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

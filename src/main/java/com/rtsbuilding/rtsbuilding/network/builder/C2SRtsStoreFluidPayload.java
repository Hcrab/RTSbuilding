package com.rtsbuilding.rtsbuilding.network.builder;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsStoreFluidPayload(
        byte sourceType,
        byte toolSlot,
        String itemId) implements CustomPacketPayload {
    public static final byte SOURCE_STORAGE_ITEM = 0;
    public static final byte SOURCE_TOOL_SLOT = 1;
    public static final byte SOURCE_PIN_ITEM = 2;

    public static final Type<C2SRtsStoreFluidPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_store_fluid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsStoreFluidPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.sourceType());
                buf.writeByte(payload.toolSlot());
                buf.writeUtf(payload.itemId(), 128);
            },
            (buf) -> new C2SRtsStoreFluidPayload(
                    buf.readByte(),
                    buf.readByte(),
                    buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

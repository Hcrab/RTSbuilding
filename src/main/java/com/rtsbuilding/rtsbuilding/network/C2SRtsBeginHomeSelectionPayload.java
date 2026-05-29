package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsBeginHomeSelectionPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsBeginHomeSelectionPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_begin_home_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsBeginHomeSelectionPayload> STREAM_CODEC =
            StreamCodec.unit(new C2SRtsBeginHomeSelectionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

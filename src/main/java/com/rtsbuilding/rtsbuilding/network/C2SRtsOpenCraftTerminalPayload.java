package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsOpenCraftTerminalPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsOpenCraftTerminalPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_open_craft_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsOpenCraftTerminalPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                    },
                    (buf) -> new C2SRtsOpenCraftTerminalPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

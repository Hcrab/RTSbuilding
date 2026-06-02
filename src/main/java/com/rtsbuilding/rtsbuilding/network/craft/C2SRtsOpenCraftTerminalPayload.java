package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsOpenCraftTerminalPayload() implements CustomPacketPayload {
    public static final Type<C2SRtsOpenCraftTerminalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_open_craft_terminal"));

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

package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsSetProgressionCostPayload(ResourceLocation nodeId, String costsText) implements CustomPacketPayload {
    public static final Type<C2SRtsSetProgressionCostPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_set_progression_cost"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsSetProgressionCostPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeResourceLocation(payload.nodeId());
                buf.writeUtf(payload.costsText() == null ? "" : payload.costsText(), 512);
            },
            buf -> new C2SRtsSetProgressionCostPayload(buf.readResourceLocation(), buf.readUtf(512)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

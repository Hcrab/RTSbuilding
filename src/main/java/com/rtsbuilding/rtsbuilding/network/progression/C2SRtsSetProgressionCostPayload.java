package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsSetProgressionCostPayload(ResourceLocation nodeId, String costsText) implements CustomPacketPayload {
    public static final Type<C2SRtsSetProgressionCostPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_set_progression_cost"), C2SRtsSetProgressionCostPayload.class);

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

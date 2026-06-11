package com.rtsbuilding.rtsbuilding.network.progression;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsUnlockProgressionNodePayload(ResourceLocation nodeId) implements CustomPacketPayload {
    public static final Type<C2SRtsUnlockProgressionNodePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_unlock_progression_node"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsUnlockProgressionNodePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeResourceLocation(payload.nodeId()),
            (buf) -> new C2SRtsUnlockProgressionNodePayload(buf.readResourceLocation()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

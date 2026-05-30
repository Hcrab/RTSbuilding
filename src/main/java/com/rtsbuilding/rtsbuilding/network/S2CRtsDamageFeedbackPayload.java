package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRtsDamageFeedbackPayload(float amount) implements CustomPacketPayload {
    public static final Type<S2CRtsDamageFeedbackPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_damage_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsDamageFeedbackPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeFloat(payload.amount()),
            (buf) -> new S2CRtsDamageFeedbackPayload(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

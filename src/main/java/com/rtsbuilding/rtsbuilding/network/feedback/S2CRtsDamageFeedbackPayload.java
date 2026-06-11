package com.rtsbuilding.rtsbuilding.network.feedback;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;

import net.minecraft.resources.ResourceLocation;

public record S2CRtsDamageFeedbackPayload(float amount, boolean lowHealth) implements CustomPacketPayload {
    public static final Type<S2CRtsDamageFeedbackPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_damage_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsDamageFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeFloat(Math.max(0.0F, payload.amount()));
                        buf.writeBoolean(payload.lowHealth());
                    },
                    (buf) -> new S2CRtsDamageFeedbackPayload(buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

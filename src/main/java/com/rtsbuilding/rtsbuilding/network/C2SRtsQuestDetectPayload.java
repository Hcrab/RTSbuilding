package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsQuestDetectPayload(byte mode) implements CustomPacketPayload {
    public static final byte MODE_MANUAL = 0;

    public static final Type<C2SRtsQuestDetectPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_quest_detect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsQuestDetectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeByte(payload.mode()),
                    (buf) -> new C2SRtsQuestDetectPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

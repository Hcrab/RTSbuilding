package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRtsQuestDetectPayload(byte mode) implements CustomPacketPayload {
    public static final byte MODE_MANUAL = 0;

    public static final Type<C2SRtsQuestDetectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_quest_detect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsQuestDetectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeByte(payload.mode()),
                    (buf) -> new C2SRtsQuestDetectPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

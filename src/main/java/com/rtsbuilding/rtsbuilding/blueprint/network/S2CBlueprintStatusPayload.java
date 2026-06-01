package com.rtsbuilding.rtsbuilding.blueprint.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CBlueprintStatusPayload(byte status, String messageKey, String detail) implements CustomPacketPayload {
    public static final byte INFO = 0;
    public static final byte SUCCESS = 1;
    public static final byte ERROR = 2;
    public static final int MAX_TEXT_CHARS = 192;

    public static final Type<S2CBlueprintStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_blueprint_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CBlueprintStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.status());
                buf.writeUtf(payload.messageKey() == null ? "" : payload.messageKey(), MAX_TEXT_CHARS);
                buf.writeUtf(payload.detail() == null ? "" : payload.detail(), MAX_TEXT_CHARS);
            },
            (buf) -> new S2CBlueprintStatusPayload(
                    buf.readByte(),
                    buf.readUtf(MAX_TEXT_CHARS),
                    buf.readUtf(MAX_TEXT_CHARS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

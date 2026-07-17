package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CBlueprintStatusPayload(byte status, String messageKey, String detail) implements CustomPacketPayload {
    public static final byte INFO = 0;
    public static final byte SUCCESS = 1;
    public static final byte ERROR = 2;
    public static final int MAX_TEXT_CHARS = 192;

    public S2CBlueprintStatusPayload {
        messageKey = limitText(messageKey);
        detail = limitText(detail);
    }

    public static final Type<S2CBlueprintStatusPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_blueprint_status"));

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

    /** 所有发送入口统一限长，并避免把 UTF-16 代理对截成半个字符。 */
    private static String limitText(String value) {
        if (value == null) return "";
        if (value.length() <= MAX_TEXT_CHARS) return value;
        int end = MAX_TEXT_CHARS;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}

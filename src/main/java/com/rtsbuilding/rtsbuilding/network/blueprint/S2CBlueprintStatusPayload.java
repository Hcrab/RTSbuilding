package com.rtsbuilding.rtsbuilding.network.blueprint;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 服务端返回蓝图上传、解析或任务创建的状态。 */
public final class S2CBlueprintStatusPayload implements IMessage {
    public static final byte INFO = 0;
    public static final byte SUCCESS = 1;
    public static final byte ERROR = 2;
    public static final int MAX_TEXT_CHARS = 192;

    private byte status;
    private String messageKey;
    private String detail;

    public S2CBlueprintStatusPayload() {
    }

    public S2CBlueprintStatusPayload(byte status, String messageKey, String detail) {
        this.status = status;
        this.messageKey = limitText(messageKey);
        this.detail = limitText(detail);
    }

    public byte status() { return status; }
    public String messageKey() { return messageKey; }
    public String detail() { return detail; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        status = buffer.readByte();
        messageKey = RtsPacketBuffer.readString(buffer, MAX_TEXT_CHARS, "blueprint status key");
        detail = RtsPacketBuffer.readString(buffer, MAX_TEXT_CHARS, "blueprint status detail");
        if (!isValidStatus(status)) throw new IllegalArgumentException("Unknown blueprint status: " + status);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isValidStatus(status)) throw new IllegalArgumentException("Unknown blueprint status: " + status);
        buffer.writeByte(status);
        RtsPacketBuffer.writeString(buffer, messageKey, MAX_TEXT_CHARS, "blueprint status key");
        RtsPacketBuffer.writeString(buffer, detail, MAX_TEXT_CHARS, "blueprint status detail");
    }

    private static boolean isValidStatus(byte status) {
        return status == INFO || status == SUCCESS || status == ERROR;
    }

    private static String limitText(String value) {
        if (value == null) return "";
        if (value.length() <= MAX_TEXT_CHARS) return value;
        int end = MAX_TEXT_CHARS;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}

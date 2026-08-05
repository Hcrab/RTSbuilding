package com.rtsbuilding.rtsbuilding.network.progression;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 请求服务端执行一次任务检测。 */
public final class C2SRtsQuestDetectPayload implements IMessage {
    public static final byte MODE_MANUAL = 0;

    private byte mode;

    public C2SRtsQuestDetectPayload() {
    }

    public C2SRtsQuestDetectPayload(byte mode) {
        this.mode = mode;
    }

    public byte mode() { return this.mode; }
    public boolean isValid() { return this.mode == MODE_MANUAL; }

    @Override public void fromBytes(ByteBuf buffer) { this.mode = buffer.readByte(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeByte(this.mode); }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 客户端选择建造模式；有效枚举范围最终由服务端 BuilderMode 决定。 */
public final class C2SRtsSetModePayload implements IMessage {
    private byte mode;

    public C2SRtsSetModePayload() {
    }

    public C2SRtsSetModePayload(byte mode) {
        this.mode = mode;
    }

    public byte mode() { return mode; }

    @Override public void fromBytes(ByteBuf buffer) { mode = buffer.readByte(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeByte(mode); }
    public boolean isValid() { return mode >= 0; }
}

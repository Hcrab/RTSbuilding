package com.rtsbuilding.rtsbuilding.network.progression;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 请求服务端进入 RTS 家园选点流程。 */
public final class C2SRtsBeginHomeSelectionPayload implements IMessage {
    public C2SRtsBeginHomeSelectionPayload() {
    }

    @Override public void fromBytes(ByteBuf buffer) { }
    @Override public void toBytes(ByteBuf buffer) { }
}

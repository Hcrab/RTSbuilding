package com.rtsbuilding.rtsbuilding.network.progression;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 请求服务端重新同步当前玩家的生存进度状态。 */
public final class C2SRtsRequestProgressionStatePayload implements IMessage {
    public C2SRtsRequestProgressionStatePayload() {
    }

    @Override public void fromBytes(ByteBuf buffer) { }
    @Override public void toBytes(ByteBuf buffer) { }
}

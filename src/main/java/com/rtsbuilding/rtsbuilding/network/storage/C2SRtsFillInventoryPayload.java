package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
/** 请求从当前玩家的已链接储存补满背包。 */
public final class C2SRtsFillInventoryPayload implements IMessage {
    public C2SRtsFillInventoryPayload() {}
    @Override public void fromBytes(ByteBuf buffer) {}
    @Override public void toBytes(ByteBuf buffer) {}
}

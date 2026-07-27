package com.rtsbuilding.rtsbuilding.network.storage;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
/** 请求关闭当前玩家自己的远程菜单。 */
public final class C2SRtsCloseRemoteMenuPayload implements IMessage {
    public C2SRtsCloseRemoteMenuPayload() {}
    @Override public void fromBytes(ByteBuf buffer) {}
    @Override public void toBytes(ByteBuf buffer) {}
}

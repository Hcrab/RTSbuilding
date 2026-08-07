package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 请求服务端重做最近一次成功撤销的创造模式操作。 */
public final class C2SRtsRedoPayload implements IMessage {
    public C2SRtsRedoPayload() {}
    @Override public void fromBytes(ByteBuf buffer) {}
    @Override public void toBytes(ByteBuf buffer) {}
}

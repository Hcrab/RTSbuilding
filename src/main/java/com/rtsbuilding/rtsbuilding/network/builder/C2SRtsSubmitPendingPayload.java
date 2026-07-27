package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 提交连接玩家自己的所有挂起放置作业，不携带任何客户端任务标识。 */
public final class C2SRtsSubmitPendingPayload implements IMessage {
    public C2SRtsSubmitPendingPayload() {
    }
    @Override public void fromBytes(ByteBuf buffer) { }
    @Override public void toBytes(ByteBuf buffer) { }
}

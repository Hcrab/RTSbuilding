package com.rtsbuilding.rtsbuilding.network.culling;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 请求当前玩家、当前维度的剔除状态。 */
public final class C2SRtsRequestCullingStatePayload implements IMessage {
    public C2SRtsRequestCullingStatePayload() { }
    @Override public void fromBytes(ByteBuf buf) { }
    @Override public void toBytes(ByteBuf buf) { }
}

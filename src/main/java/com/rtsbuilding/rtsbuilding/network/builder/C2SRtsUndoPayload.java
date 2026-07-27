package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 客户端请求撤回自己最近一次可撤回的 RTS 操作。 */
public final class C2SRtsUndoPayload implements IMessage {
    public C2SRtsUndoPayload() {
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        // 无字段；玩家身份和历史栈均由服务端连接上下文决定。
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        // 无字段。
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * 客户端请求删除/取消指定工作流。
 *
 * <p>客户端只提供不可变条目 ID；服务端处理器始终以连接玩家查询工作流，不能删除
 * 其他玩家的条目。</p>
 */
public final class C2SRtsDeleteWorkflowPayload implements IMessage {
    private int workflowEntryId;

    public C2SRtsDeleteWorkflowPayload() {
    }

    public C2SRtsDeleteWorkflowPayload(int workflowEntryId) {
        this.workflowEntryId = workflowEntryId;
    }

    public int workflowEntryId() {
        return workflowEntryId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        workflowEntryId = RtsPacketBuffer.readVarInt(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeVarInt(buffer, workflowEntryId);
    }

    public boolean isValid() {
        return workflowEntryId >= 0;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 客户端请求设置自己某个工作流的覆盖保护状态。 */
public final class C2SRtsSetWorkflowProtectedPayload implements IMessage {
    private int workflowEntryId;
    private boolean protectedWorkflow;

    public C2SRtsSetWorkflowProtectedPayload() {
    }

    public C2SRtsSetWorkflowProtectedPayload(int workflowEntryId, boolean protectedWorkflow) {
        this.workflowEntryId = workflowEntryId;
        this.protectedWorkflow = protectedWorkflow;
    }

    public int workflowEntryId() {
        return workflowEntryId;
    }

    public boolean protectedWorkflow() {
        return protectedWorkflow;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        workflowEntryId = RtsPacketBuffer.readVarInt(buffer);
        protectedWorkflow = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeVarInt(buffer, workflowEntryId);
        buffer.writeBoolean(protectedWorkflow);
    }

    public boolean isValid() {
        return workflowEntryId >= 0;
    }
}

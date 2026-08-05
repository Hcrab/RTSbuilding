package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 客户端请求扫描自己某个挂起蓝图任务的剩余材料。 */
public final class C2SRtsScanBlueprintResumePayload implements IMessage {
    private int workflowEntryId;

    public C2SRtsScanBlueprintResumePayload() {
    }

    public C2SRtsScanBlueprintResumePayload(int workflowEntryId) {
        this.workflowEntryId = workflowEntryId;
    }

    public int workflowEntryId() { return workflowEntryId; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        workflowEntryId = RtsPacketBuffer.readVarInt(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeVarInt(buffer, workflowEntryId);
    }

    public boolean isValid() { return workflowEntryId >= 0; }
}

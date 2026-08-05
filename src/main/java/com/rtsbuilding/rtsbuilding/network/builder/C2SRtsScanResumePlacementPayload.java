package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 请求扫描指定挂起放置工作流的剩余材料和冲突。 */
public final class C2SRtsScanResumePlacementPayload implements IMessage {
    private int workflowEntryId;
    public C2SRtsScanResumePlacementPayload() {}
    public C2SRtsScanResumePlacementPayload(int workflowEntryId) { this.workflowEntryId = workflowEntryId; }
    public int workflowEntryId() { return this.workflowEntryId; }
    public boolean isValid() { return this.workflowEntryId >= 0; }
    @Override public void fromBytes(ByteBuf buffer) { this.workflowEntryId = buffer.readInt(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeInt(this.workflowEntryId); }
}

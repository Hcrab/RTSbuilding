package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 切换指定工作流暂停状态；-1 保留为全部工作流的协议值。 */
public final class C2SRtsPauseWorkflowPayload implements IMessage {
    private int entryId = -1;
    public C2SRtsPauseWorkflowPayload() {}
    public C2SRtsPauseWorkflowPayload(int entryId) { this.entryId = entryId; }
    public int entryId() { return this.entryId; }
    public boolean isValid() { return this.entryId >= -1; }
    @Override public void fromBytes(ByteBuf buffer) { this.entryId = buffer.readInt(); }
    @Override public void toBytes(ByteBuf buffer) { buffer.writeInt(this.entryId); }
}

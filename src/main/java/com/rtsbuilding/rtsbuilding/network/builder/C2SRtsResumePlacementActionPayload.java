package com.rtsbuilding.rtsbuilding.network.builder;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 以跳过冲突（0）或覆盖冲突（1）的策略恢复挂起放置。 */
public final class C2SRtsResumePlacementActionPayload implements IMessage {
    public static final int STRATEGY_SKIP = 0;
    public static final int STRATEGY_OVERWRITE = 1;
    private int strategy;
    private int workflowEntryId;
    public C2SRtsResumePlacementActionPayload() {}
    public C2SRtsResumePlacementActionPayload(int strategy, int workflowEntryId) {
        this.strategy = strategy;
        this.workflowEntryId = workflowEntryId;
    }
    public int strategy() { return this.strategy; }
    public int workflowEntryId() { return this.workflowEntryId; }
    public boolean isValid() {
        return (this.strategy == STRATEGY_SKIP || this.strategy == STRATEGY_OVERWRITE)
                && this.workflowEntryId >= 0;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        this.strategy = buffer.readInt();
        this.workflowEntryId = buffer.readInt();
    }
    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.strategy);
        buffer.writeInt(this.workflowEntryId);
    }
}

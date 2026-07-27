package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 同步当前可撤回步数。 */
public final class S2CRtsHistorySyncPayload implements IMessage {
    private static final int MAX_UNDO_SIZE = 1_000_000;
    private int undoSize;
    public S2CRtsHistorySyncPayload() {}
    public S2CRtsHistorySyncPayload(int undoSize) { this.undoSize = bounded(undoSize); }
    public int undoSize() { return this.undoSize; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.undoSize = RtsPacketBuffer.readBoundedCount(buffer, MAX_UNDO_SIZE, "undo size");
    }
    @Override public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeVarInt(buffer, bounded(this.undoSize));
    }
    private static int bounded(int value) {
        if (value < 0 || value > MAX_UNDO_SIZE) throw new IllegalArgumentException("undo size out of range: " + value);
        return value;
    }
}

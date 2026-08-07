package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 同步当前可撤销和可重做步数。 */
public final class S2CRtsHistorySyncPayload implements IMessage {
    private static final int MAX_UNDO_SIZE = 1_000_000;
    private int undoSize;
    private int redoSize;
    public S2CRtsHistorySyncPayload() {}
    public S2CRtsHistorySyncPayload(int undoSize) { this(undoSize, 0); }
    public S2CRtsHistorySyncPayload(int undoSize, int redoSize) {
        this.undoSize = bounded(undoSize);
        this.redoSize = bounded(redoSize);
    }
    public int undoSize() { return this.undoSize; }
    public int redoSize() { return this.redoSize; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.undoSize = RtsPacketBuffer.readBoundedCount(buffer, MAX_UNDO_SIZE, "undo size");
        this.redoSize = RtsPacketBuffer.readBoundedCount(buffer, MAX_UNDO_SIZE, "redo size");
    }
    @Override public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeVarInt(buffer, bounded(this.undoSize));
        RtsPacketBuffer.writeVarInt(buffer, bounded(this.redoSize));
    }
    private static int bounded(int value) {
        if (value < 0 || value > MAX_UNDO_SIZE) throw new IllegalArgumentException("undo size out of range: " + value);
        return value;
    }
}

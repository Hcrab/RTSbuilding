package com.rtsbuilding.rtsbuilding.network.progression;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** 服务端下发的任务检测进度快照。 */
public final class S2CRtsQuestDetectStatusPayload implements IMessage {
    public static final byte PHASE_STARTED = 0;
    public static final byte PHASE_COMPLETE = 1;
    public static final byte PHASE_UNAVAILABLE = 2;
    public static final byte PHASE_ERROR = 3;

    private static final int MAX_TASKS = 10_000_000;

    private byte phase;
    private int scannedTasks;
    private int totalTasks;
    private int completedTasks;

    public S2CRtsQuestDetectStatusPayload() {
    }

    public S2CRtsQuestDetectStatusPayload(byte phase, int scannedTasks, int totalTasks, int completedTasks) {
        this.phase = sanitizePhase(phase);
        this.scannedTasks = bounded(scannedTasks, "scanned tasks");
        this.totalTasks = bounded(totalTasks, "total tasks");
        this.completedTasks = bounded(completedTasks, "completed tasks");
    }

    public byte phase() { return this.phase; }
    public int scannedTasks() { return this.scannedTasks; }
    public int totalTasks() { return this.totalTasks; }
    public int completedTasks() { return this.completedTasks; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.phase = sanitizePhase(buffer.readByte());
        this.scannedTasks = RtsPacketBuffer.readBoundedCount(buffer, MAX_TASKS, "scanned tasks");
        this.totalTasks = RtsPacketBuffer.readBoundedCount(buffer, MAX_TASKS, "total tasks");
        this.completedTasks = RtsPacketBuffer.readBoundedCount(buffer, MAX_TASKS, "completed tasks");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(sanitizePhase(this.phase));
        RtsPacketBuffer.writeVarInt(buffer, bounded(this.scannedTasks, "scanned tasks"));
        RtsPacketBuffer.writeVarInt(buffer, bounded(this.totalTasks, "total tasks"));
        RtsPacketBuffer.writeVarInt(buffer, bounded(this.completedTasks, "completed tasks"));
    }

    private static byte sanitizePhase(byte value) {
        if (value < PHASE_STARTED || value > PHASE_ERROR) {
            throw new IllegalArgumentException("quest detect phase out of range: " + value);
        }
        return value;
    }

    private static int bounded(int value, String name) {
        if (value < 0 || value > MAX_TASKS) throw new IllegalArgumentException(name + " out of range: " + value);
        return value;
    }
}

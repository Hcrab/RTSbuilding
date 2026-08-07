package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 服务端对一个 traced RTS 操作发送的聚合终态，不包含目标坐标、库存或 NBT。 */
public final class S2CRtsOperationTerminalPayload implements IMessage, RtsTracedPayload {
    private long traceId;
    private int sequence;
    private String outcome = "UNKNOWN";
    private String reason = "UNKNOWN";
    private int workflowId = -1;
    private String taskId = "-";
    private int completed;
    private int failed;
    private long serverTick;
    private boolean everExecuted;
    private long firstSliceWaitTicks = -1L;

    public S2CRtsOperationTerminalPayload() {}

    public S2CRtsOperationTerminalPayload(long traceId, int sequence, String outcome, String reason,
            int workflowId, String taskId, int completed, int failed, long serverTick,
            boolean everExecuted, long firstSliceWaitTicks) {
        this.traceId = traceId;
        this.sequence = Math.max(0, sequence);
        this.outcome = safe(outcome);
        this.reason = safe(reason);
        this.workflowId = workflowId;
        this.taskId = safe(taskId);
        this.completed = Math.max(0, completed);
        this.failed = Math.max(0, failed);
        this.serverTick = serverTick;
        this.everExecuted = everExecuted;
        this.firstSliceWaitTicks = firstSliceWaitTicks;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        traceId = buffer.readLong();
        sequence = RtsPacketBuffer.readVarInt(buffer);
        outcome = RtsPacketBuffer.readString(buffer, 64, "terminal outcome");
        reason = RtsPacketBuffer.readString(buffer, 128, "terminal reason");
        workflowId = buffer.readInt();
        taskId = RtsPacketBuffer.readString(buffer, 80, "terminal task");
        completed = RtsPacketBuffer.readVarInt(buffer);
        failed = RtsPacketBuffer.readVarInt(buffer);
        serverTick = buffer.readLong();
        everExecuted = buffer.readBoolean();
        firstSliceWaitTicks = buffer.readLong();
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(traceId);
        RtsPacketBuffer.writeVarInt(buffer, Math.max(0, sequence));
        RtsPacketBuffer.writeString(buffer, safe(outcome), 64, "terminal outcome");
        RtsPacketBuffer.writeString(buffer, safe(reason), 128, "terminal reason");
        buffer.writeInt(workflowId);
        RtsPacketBuffer.writeString(buffer, safe(taskId), 80, "terminal task");
        RtsPacketBuffer.writeVarInt(buffer, Math.max(0, completed));
        RtsPacketBuffer.writeVarInt(buffer, Math.max(0, failed));
        buffer.writeLong(serverTick);
        buffer.writeBoolean(everExecuted);
        buffer.writeLong(firstSliceWaitTicks);
    }

    @Override public long traceId() { return traceId; }
    public int sequence() { return sequence; }
    public String outcome() { return outcome; }
    public String reason() { return reason; }
    public int workflowId() { return workflowId; }
    public String taskId() { return taskId; }
    public int completed() { return completed; }
    public int failed() { return failed; }
    public long serverTick() { return serverTick; }
    public boolean everExecuted() { return everExecuted; }
    public long firstSliceWaitTicks() { return firstSliceWaitTicks; }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}

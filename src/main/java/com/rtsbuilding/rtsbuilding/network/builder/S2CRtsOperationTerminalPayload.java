package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端对一个 traced RTS 操作发送的聚合终态，不包含目标坐标、库存或 NBT。 */
public record S2CRtsOperationTerminalPayload(
        long traceId,
        int sequence,
        String outcome,
        String reason,
        int workflowId,
        String taskId,
        int completed,
        int failed,
        long serverTick,
        boolean everExecuted,
        long firstSliceWaitTicks) implements CustomPacketPayload, RtsTracedPayload {
    public static final Type<S2CRtsOperationTerminalPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_operation_terminal_v2"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsOperationTerminalPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeLong(payload.traceId());
                buf.writeVarInt(payload.sequence());
                buf.writeUtf(safe(payload.outcome()), 64);
                buf.writeUtf(safe(payload.reason()), 128);
                buf.writeVarInt(payload.workflowId());
                buf.writeUtf(safe(payload.taskId()), 80);
                buf.writeVarInt(Math.max(0, payload.completed()));
                buf.writeVarInt(Math.max(0, payload.failed()));
                buf.writeVarLong(payload.serverTick());
                buf.writeBoolean(payload.everExecuted());
                buf.writeVarLong(payload.firstSliceWaitTicks());
            },
            buf -> new S2CRtsOperationTerminalPayload(
                    buf.readLong(), buf.readVarInt(), buf.readUtf(64), buf.readUtf(128),
                    buf.readVarInt(), buf.readUtf(80), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarLong(), buf.readBoolean(), buf.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyTracePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaMineTracePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMineTracePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltiminePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltimineTracePayload;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsServerTraceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side C2S adapter for RTS mining, ultimine, area mining, and area
 * destroy actions.
 *
 * <p>Keep tool leasing, item extraction, and undo recording in
 * RtsMiningService; this layer should only unwrap payloads and enqueue work on
 * the server thread.
 */
public final class RtsMiningHandlers {
    private RtsMiningHandlers() {
    }

    public static void handleMine(C2SRtsMinePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = RtsServerTraceRegistry.acceptNetwork(
                        serverPlayer, null, -1L, 0,
                        RtsTraceInputKind.UNKNOWN.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                        payload.start() ? "MINE_START_LEGACY" : "MINE_STOP_LEGACY",
                        receivedNanos, receivedTick);
                Direction face = Direction.from3DDataValue(payload.face());
                ServiceRegistry.getInstance().mining().mine(
                        serverPlayer,
                        payload.pos(),
                        face,
                        payload.start(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery(),
                        payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleMineTrace(C2SRtsMineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(),
                        payload.start() ? "MINE_START" : "MINE_STOP", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().mine(
                        serverPlayer, payload.pos(), Direction.from3DDataValue(payload.face()),
                        payload.start(), payload.toolSlot(), payload.toolItemId(), payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery(), payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleUltimine(C2SRtsUltiminePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = RtsServerTraceRegistry.acceptNetwork(
                        serverPlayer, null, -1L, 0,
                        RtsTraceInputKind.UNKNOWN.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                        "ULTIMINE_LEGACY", receivedNanos, receivedTick);
                Direction face = Direction.from3DDataValue(payload.face());
                ServiceRegistry.getInstance().mining().startUltimine(
                        serverPlayer,
                        payload.pos(),
                        face,
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.limit(),
                        payload.mode(),
                        payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleUltimineTrace(C2SRtsUltimineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(), "ULTIMINE", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().startUltimine(
                        serverPlayer, payload.pos(), Direction.from3DDataValue(payload.face()),
                        payload.toolSlot(), payload.toolItemId(), payload.toolPrototype(),
                        payload.limit(), payload.mode(), payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleAreaMine(C2SRtsAreaMinePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = RtsServerTraceRegistry.acceptNetwork(
                        serverPlayer, null, -1L, 0,
                        RtsTraceInputKind.UNKNOWN.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                        "AREA_MINE_LEGACY", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().areaMine(
                        serverPlayer,
                        payload.minX(), payload.maxX(),
                        payload.minY(), payload.maxY(),
                        payload.minZ(), payload.maxZ(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.shapeType(),
                        payload.fillType(),
                        payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleAreaMineTrace(C2SRtsAreaMineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(), "AREA_MINE", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().areaMine(
                        serverPlayer, payload.minX(), payload.maxX(), payload.minY(), payload.maxY(),
                        payload.minZ(), payload.maxZ(), payload.toolSlot(), payload.toolItemId(),
                        payload.toolPrototype(), payload.shapeType(), payload.fillType(),
                        payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleAreaDestroy(C2SRtsAreaDestroyPayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = RtsServerTraceRegistry.acceptNetwork(
                        serverPlayer, null, -1L, 0,
                        RtsTraceInputKind.UNKNOWN.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                        "AREA_DESTROY_LEGACY", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().areaDestroy(
                        serverPlayer,
                        payload.positions(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleAreaDestroyTrace(C2SRtsAreaDestroyTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(), "AREA_DESTROY", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().areaDestroy(
                        serverPlayer, payload.positions(), payload.toolSlot(), payload.toolItemId(),
                        payload.toolPrototype(), payload.toolProtectionEnabled(), trace);
            }
        });
    }

    private static com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext trace(
            ServerPlayer player, RtsTracedPayload payload, long clientTick, int heldMs,
            byte inputKind, byte stopOrigin, String packet, long receivedNanos, long receivedTick) {
        return RtsServerTraceRegistry.acceptNetwork(
                player, payload, clientTick, heldMs, inputKind, stopOrigin,
                packet, receivedNanos, receivedTick);
    }

    private static long receiveTick(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player
                ? player.serverLevel().getGameTime() : -1L;
    }
}

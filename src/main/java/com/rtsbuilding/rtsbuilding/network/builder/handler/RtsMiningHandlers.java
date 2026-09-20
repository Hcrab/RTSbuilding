package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyFragmentPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyTracePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaMineTracePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsConvenienceDestroyPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsConvenienceDestroyTracePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMineTracePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltiminePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltimineTracePayload;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsServerTraceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.network.RtsAreaDestroyFragmentNetwork;
import com.rtsbuilding.rtsbuilding.server.network.RtsAreaDestroyFragmentReassembler;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsConvenienceDestroyService;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side C2S adapter for RTS mining, ultimine, area mining, and area
 * destroy actions.
 *
 * <p>Keep tool leasing, item extraction, and undo recording in
 * RtsMiningService; this layer only unwraps payloads and enqueues work on the
 * server thread. Legacy packets receive a synthetic trace so the old client
 * path remains behavior-compatible while still appearing in diagnostics.</p>
 */
public final class RtsMiningHandlers {
    private RtsMiningHandlers() {
    }

    public static void handleMine(C2SRtsMinePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = legacyTrace(serverPlayer,
                        payload.start() ? "MINE_START_LEGACY" : "MINE_STOP_LEGACY",
                        receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().mine(
                        serverPlayer,
                        payload.pos(),
                        Direction.from3DDataValue(payload.face()),
                        payload.start(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery(),
                        payload.toolProtectionEnabled(),
                        trace);
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
                        serverPlayer,
                        payload.pos(),
                        Direction.from3DDataValue(payload.face()),
                        payload.start(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery(),
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    public static void handleUltimine(C2SRtsUltiminePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = legacyTrace(serverPlayer, "ULTIMINE_LEGACY", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().startUltimine(
                        serverPlayer,
                        payload.pos(),
                        Direction.from3DDataValue(payload.face()),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.limit(),
                        payload.mode(),
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    public static void handleUltimineTrace(
            C2SRtsUltimineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(), "ULTIMINE", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().startUltimine(
                        serverPlayer,
                        payload.pos(),
                        Direction.from3DDataValue(payload.face()),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.limit(),
                        payload.mode(),
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    public static void handleAreaMine(C2SRtsAreaMinePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = legacyTrace(serverPlayer, "AREA_MINE_LEGACY", receivedNanos, receivedTick);
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
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    public static void handleAreaMineTrace(
            C2SRtsAreaMineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(), "AREA_MINE", receivedNanos, receivedTick);
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
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    public static void handleAreaDestroy(C2SRtsAreaDestroyPayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = legacyTrace(serverPlayer, "AREA_DESTROY_LEGACY", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().areaDestroy(
                        serverPlayer,
                        payload.positions(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    public static void handleAreaDestroyTrace(
            C2SRtsAreaDestroyTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(), "AREA_DESTROY", receivedNanos, receivedTick);
                ServiceRegistry.getInstance().mining().areaDestroy(
                        serverPlayer,
                        payload.positions(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    /** 只有分片重组完成后才复用旧 trace 处理路径，避免单片提前执行。 */
    public static void handleAreaDestroyFragment(
            C2SRtsAreaDestroyFragmentPayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            long nowTick = receiveTick(context);
            var result = RtsAreaDestroyFragmentNetwork.accept(
                    serverPlayer.getUUID(), payload, nowTick < 0L ? receivedTick : nowTick);
            if (result.status() == RtsAreaDestroyFragmentReassembler.Status.REJECTED) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.rtsbuilding.mining.transfer_incomplete"), false);
            }
            if (result.status() != RtsAreaDestroyFragmentReassembler.Status.COMPLETE
                    || result.payload() == null) return;
            dispatchAreaDestroyTrace(serverPlayer, result.payload(), receivedNanos, receivedTick);
        });
    }

    private static void dispatchAreaDestroyTrace(
            ServerPlayer serverPlayer, C2SRtsAreaDestroyTracePayload payload,
            long receivedNanos, long receivedTick) {
        var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                payload.inputKind(), payload.stopOrigin(), "AREA_DESTROY", receivedNanos, receivedTick);
        ServiceRegistry.getInstance().mining().areaDestroy(
                serverPlayer, payload.positions(), payload.toolSlot(), payload.toolItemId(),
                payload.toolPrototype(), payload.toolProtectionEnabled(), trace);
    }

    public static void handleConvenienceDestroy(
            C2SRtsConvenienceDestroyPayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = legacyTrace(serverPlayer, "CONVENIENCE_DESTROY_LEGACY",
                        receivedNanos, receivedTick);
                if (payload.face() < 0 || payload.face() >= Direction.values().length) {
                    RtsServerTraceRegistry.terminalWithoutWorkflow(
                            serverPlayer, trace, RtsWorkflowType.AREA_DESTROY,
                            "REJECTED", "INVALID_FACE");
                    return;
                }
                RtsConvenienceDestroyService.INSTANCE.submit(
                        serverPlayer,
                        payload.mode(),
                        payload.anchor(),
                        Direction.from3DDataValue(payload.face()),
                        payload.settings(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    public static void handleConvenienceDestroyTrace(
            C2SRtsConvenienceDestroyTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                var trace = trace(serverPlayer, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(),
                        "CONVENIENCE_DESTROY", receivedNanos, receivedTick);
                if (payload.face() < 0 || payload.face() >= Direction.values().length) {
                    RtsServerTraceRegistry.terminalWithoutWorkflow(
                            serverPlayer, trace, RtsWorkflowType.AREA_DESTROY,
                            "REJECTED", "INVALID_FACE");
                    return;
                }
                RtsConvenienceDestroyService.INSTANCE.submit(
                        serverPlayer,
                        payload.mode(),
                        payload.anchor(),
                        Direction.from3DDataValue(payload.face()),
                        payload.settings(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.toolProtectionEnabled(),
                        trace);
            }
        });
    }

    private static RtsOperationTraceContext trace(
            ServerPlayer player, RtsTracedPayload payload, long clientTick, int heldMs,
            byte inputKind, byte stopOrigin, String packet, long receivedNanos, long receivedTick) {
        return RtsServerTraceRegistry.acceptNetwork(
                player, payload, clientTick, heldMs, inputKind, stopOrigin,
                packet, receivedNanos, receivedTick);
    }

    private static RtsOperationTraceContext legacyTrace(
            ServerPlayer player, String packet, long receivedNanos, long receivedTick) {
        return RtsServerTraceRegistry.acceptNetwork(
                player, null, -1L, 0,
                RtsTraceInputKind.UNKNOWN.wireId(), RtsMiningStopOrigin.NONE.wireId(),
                packet, receivedNanos, receivedTick);
    }

    private static long receiveTick(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player
                ? player.serverLevel().getGameTime() : -1L;
    }
}

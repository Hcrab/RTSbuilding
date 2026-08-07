package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsServerTraceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsConvenienceDestroyService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsNativeLeftClickBridge;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端采掘网络适配层，只负责解包和建立诊断上下文。
 * 工具租约、资源返还和撤回记录仍由采掘服务负责。
 */
public final class RtsMiningHandlers {
    private RtsMiningHandlers() {
    }

    public static void handleMine(C2SRtsMinePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var trace = legacy(player, payload.start() ? "MINE_START_LEGACY" : "MINE_STOP_LEGACY",
                        receivedNanos, receivedTick);
                if (RtsNativeLeftClickBridge.interceptMiningStart(player, payload)) {
                    RtsServerTraceRegistry.terminalWithoutWorkflow(
                            player, trace, RtsWorkflowType.MINE_SINGLE,
                            "COMPLETED", "NATIVE_LEFT_CLICK_HANDLED");
                    return;
                }
                ServiceRegistry.getInstance().mining().mine(
                        player, payload.pos(), Direction.from3DDataValue(payload.face()), payload.start(),
                        payload.toolSlot(), payload.toolItemId(), payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery(), payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleMineTrace(C2SRtsMineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var trace = trace(player, payload, payload.clientTick(), payload.heldMs(),
                        payload.inputKind(), payload.stopOrigin(),
                        payload.start() ? "MINE_START" : "MINE_STOP", receivedNanos, receivedTick);
                if (RtsNativeLeftClickBridge.interceptMiningStart(player, payload)) {
                    RtsServerTraceRegistry.terminalWithoutWorkflow(
                            player, trace, RtsWorkflowType.MINE_SINGLE,
                            "COMPLETED", "NATIVE_LEFT_CLICK_HANDLED");
                    return;
                }
                ServiceRegistry.getInstance().mining().mine(
                        player, payload.pos(), Direction.from3DDataValue(payload.face()), payload.start(),
                        payload.toolSlot(), payload.toolItemId(), payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery(), payload.toolProtectionEnabled(), trace);
            }
        });
    }

    public static void handleUltimine(C2SRtsUltiminePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServiceRegistry.getInstance().mining().startUltimine(
                        player, payload.pos(), Direction.from3DDataValue(payload.face()),
                        payload.toolSlot(), payload.toolItemId(), payload.toolPrototype(),
                        payload.limit(), payload.mode(), payload.toolProtectionEnabled(),
                        legacy(player, "ULTIMINE_LEGACY", receivedNanos, receivedTick));
            }
        });
    }

    public static void handleUltimineTrace(C2SRtsUltimineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServiceRegistry.getInstance().mining().startUltimine(
                        player, payload.pos(), Direction.from3DDataValue(payload.face()),
                        payload.toolSlot(), payload.toolItemId(), payload.toolPrototype(),
                        payload.limit(), payload.mode(), payload.toolProtectionEnabled(),
                        trace(player, payload, payload.clientTick(), payload.heldMs(),
                                payload.inputKind(), payload.stopOrigin(), "ULTIMINE",
                                receivedNanos, receivedTick));
            }
        });
    }

    public static void handleAreaMine(C2SRtsAreaMinePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServiceRegistry.getInstance().mining().areaMine(
                        player, payload.minX(), payload.maxX(), payload.minY(), payload.maxY(),
                        payload.minZ(), payload.maxZ(), payload.toolSlot(), payload.toolItemId(),
                        payload.toolPrototype(), payload.shapeType(), payload.fillType(),
                        payload.toolProtectionEnabled(),
                        legacy(player, "AREA_MINE_LEGACY", receivedNanos, receivedTick));
            }
        });
    }

    public static void handleAreaMineTrace(C2SRtsAreaMineTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServiceRegistry.getInstance().mining().areaMine(
                        player, payload.minX(), payload.maxX(), payload.minY(), payload.maxY(),
                        payload.minZ(), payload.maxZ(), payload.toolSlot(), payload.toolItemId(),
                        payload.toolPrototype(), payload.shapeType(), payload.fillType(),
                        payload.toolProtectionEnabled(),
                        trace(player, payload, payload.clientTick(), payload.heldMs(),
                                payload.inputKind(), payload.stopOrigin(), "AREA_MINE",
                                receivedNanos, receivedTick));
            }
        });
    }

    public static void handleAreaDestroy(C2SRtsAreaDestroyPayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServiceRegistry.getInstance().mining().areaDestroy(
                        player, payload.positions(), payload.toolSlot(), payload.toolItemId(),
                        payload.toolPrototype(), payload.toolProtectionEnabled(),
                        legacy(player, "AREA_DESTROY_LEGACY", receivedNanos, receivedTick));
            }
        });
    }

    public static void handleAreaDestroyTrace(C2SRtsAreaDestroyTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServiceRegistry.getInstance().mining().areaDestroy(
                        player, payload.positions(), payload.toolSlot(), payload.toolItemId(),
                        payload.toolPrototype(), payload.toolProtectionEnabled(),
                        trace(player, payload, payload.clientTick(), payload.heldMs(),
                                payload.inputKind(), payload.stopOrigin(), "AREA_DESTROY",
                                receivedNanos, receivedTick));
            }
        });
    }

    /** 服务端按真实世界重新规划便捷破坏，不接受客户端坐标数组。 */
    public static void handleConvenienceDestroy(
            C2SRtsConvenienceDestroyPayload payload, IPayloadContext context) {
        submitConvenience(payload, context, null);
    }

    public static void handleConvenienceDestroyTrace(
            C2SRtsConvenienceDestroyTracePayload payload, IPayloadContext context) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsConvenienceDestroyService.INSTANCE.submit(
                        player, payload.mode(), payload.anchor(),
                        Direction.from3DDataValue(payload.face()), payload.settings(), payload.toolSlot(),
                        payload.toolItemId(), payload.toolPrototype(), payload.toolProtectionEnabled(),
                        trace(player, payload, payload.clientTick(), payload.heldMs(),
                                payload.inputKind(), payload.stopOrigin(), "CONVENIENCE_DESTROY",
                                receivedNanos, receivedTick));
            }
        });
    }

    private static void submitConvenience(
            C2SRtsConvenienceDestroyPayload payload, IPayloadContext context, Void ignored) {
        long receivedNanos = System.nanoTime();
        long receivedTick = receiveTick(context);
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RtsConvenienceDestroyService.INSTANCE.submit(
                        player, payload.mode(), payload.anchor(),
                        Direction.from3DDataValue(payload.face()), payload.settings(), payload.toolSlot(),
                        payload.toolItemId(), payload.toolPrototype(), payload.toolProtectionEnabled(),
                        legacy(player, "CONVENIENCE_DESTROY_LEGACY", receivedNanos, receivedTick));
            }
        });
    }

    private static com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext legacy(
            ServerPlayer player, String packet, long receivedNanos, long receivedTick) {
        return RtsServerTraceRegistry.acceptNetwork(
                player, null, -1L, 0, RtsTraceInputKind.UNKNOWN.wireId(),
                RtsMiningStopOrigin.NONE.wireId(), packet, receivedNanos, receivedTick);
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
                ? player.level().getGameTime() : -1L;
    }
}

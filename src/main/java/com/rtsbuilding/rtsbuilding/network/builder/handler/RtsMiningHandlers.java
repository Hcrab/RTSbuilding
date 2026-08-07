package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadContext;
import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
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
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsConvenienceDestroyService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsNativeLeftClickBridge;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/** C2S 挖掘适配层：解包后仅在服务端线程创建真实 trace 并交给服务。 */
public final class RtsMiningHandlers {
  private RtsMiningHandlers() {}

  public static void handleMine(C2SRtsMinePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "MINE_LEGACY",
        null,
        -1L,
        0,
        RtsTraceInputKind.UNKNOWN.wireId(),
        RtsMiningStopOrigin.NONE.wireId(),
        (player, trace) -> {
          if (RtsNativeLeftClickBridge.interceptMiningStart(player, payload)) {
            return;
          }
          ServiceRegistry.getInstance()
              .mining()
              .mine(
                  player,
                  payload.pos(),
                  Direction.from3DDataValue(payload.face()),
                  payload.start(),
                  payload.toolSlot(),
                  payload.toolItemId(),
                  payload.toolPrototype(),
                  payload.allowPlacedBlockRecovery(),
                  payload.toolProtectionEnabled(),
                  trace);
        });
  }

  public static void handleMineTrace(C2SRtsMineTracePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        payload.start() ? "MINE_START" : "MINE_STOP",
        payload,
        payload.clientTick(),
        payload.heldMs(),
        payload.inputKind(),
        payload.stopOrigin(),
        (player, trace) -> {
          if (RtsNativeLeftClickBridge.interceptMiningStart(player, payload)) {
            RtsServerTraceRegistry.terminalWithoutWorkflow(
                player,
                trace,
                RtsWorkflowType.MINE_SINGLE,
                "COMPLETED",
                "NATIVE_LEFT_CLICK_HANDLED");
            return;
          }
          ServiceRegistry.getInstance()
              .mining()
              .mine(
                  player,
                  payload.pos(),
                  Direction.from3DDataValue(payload.face()),
                  payload.start(),
                  payload.toolSlot(),
                  payload.toolItemId(),
                  payload.toolPrototype(),
                  payload.allowPlacedBlockRecovery(),
                  payload.toolProtectionEnabled(),
                  trace);
        });
  }

  public static void handleUltimine(C2SRtsUltiminePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "ULTIMINE_LEGACY",
        null,
        -1L,
        0,
        RtsTraceInputKind.UNKNOWN.wireId(),
        RtsMiningStopOrigin.NONE.wireId(),
        (player, trace) ->
            ServiceRegistry.getInstance()
                .mining()
                .startUltimine(
                    player,
                    payload.pos(),
                    Direction.from3DDataValue(payload.face()),
                    payload.toolSlot(),
                    payload.toolItemId(),
                    payload.toolPrototype(),
                    payload.limit(),
                    payload.mode(),
                    payload.toolProtectionEnabled(),
                    trace));
  }

  public static void handleUltimineTrace(
      C2SRtsUltimineTracePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "ULTIMINE",
        payload,
        payload.clientTick(),
        payload.heldMs(),
        payload.inputKind(),
        payload.stopOrigin(),
        (player, trace) ->
            ServiceRegistry.getInstance()
                .mining()
                .startUltimine(
                    player,
                    payload.pos(),
                    Direction.from3DDataValue(payload.face()),
                    payload.toolSlot(),
                    payload.toolItemId(),
                    payload.toolPrototype(),
                    payload.limit(),
                    payload.mode(),
                    payload.toolProtectionEnabled(),
                    trace));
  }

  public static void handleAreaMine(C2SRtsAreaMinePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "AREA_MINE_LEGACY",
        null,
        -1L,
        0,
        RtsTraceInputKind.UNKNOWN.wireId(),
        RtsMiningStopOrigin.NONE.wireId(),
        (player, trace) ->
            ServiceRegistry.getInstance()
                .mining()
                .areaMine(
                    player,
                    payload.minX(),
                    payload.maxX(),
                    payload.minY(),
                    payload.maxY(),
                    payload.minZ(),
                    payload.maxZ(),
                    payload.toolSlot(),
                    payload.toolItemId(),
                    payload.toolPrototype(),
                    payload.shapeType(),
                    payload.fillType(),
                    payload.toolProtectionEnabled(),
                    trace));
  }

  public static void handleAreaMineTrace(
      C2SRtsAreaMineTracePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "AREA_MINE",
        payload,
        payload.clientTick(),
        payload.heldMs(),
        payload.inputKind(),
        payload.stopOrigin(),
        (player, trace) ->
            ServiceRegistry.getInstance()
                .mining()
                .areaMine(
                    player,
                    payload.minX(),
                    payload.maxX(),
                    payload.minY(),
                    payload.maxY(),
                    payload.minZ(),
                    payload.maxZ(),
                    payload.toolSlot(),
                    payload.toolItemId(),
                    payload.toolPrototype(),
                    payload.shapeType(),
                    payload.fillType(),
                    payload.toolProtectionEnabled(),
                    trace));
  }

  public static void handleAreaDestroy(
      C2SRtsAreaDestroyPayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "AREA_DESTROY_LEGACY",
        null,
        -1L,
        0,
        RtsTraceInputKind.UNKNOWN.wireId(),
        RtsMiningStopOrigin.NONE.wireId(),
        (player, trace) ->
            ServiceRegistry.getInstance()
                .mining()
                .areaDestroy(
                    player,
                    payload.positions(),
                    payload.toolSlot(),
                    payload.toolItemId(),
                    payload.toolPrototype(),
                    payload.toolProtectionEnabled(),
                    trace));
  }

  public static void handleAreaDestroyTrace(
      C2SRtsAreaDestroyTracePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "AREA_DESTROY",
        payload,
        payload.clientTick(),
        payload.heldMs(),
        payload.inputKind(),
        payload.stopOrigin(),
        (player, trace) ->
            ServiceRegistry.getInstance()
                .mining()
                .areaDestroy(
                    player,
                    payload.positions(),
                    payload.toolSlot(),
                    payload.toolItemId(),
                    payload.toolPrototype(),
                    payload.toolProtectionEnabled(),
                    trace));
  }

  public static void handleConvenienceDestroy(
      C2SRtsConvenienceDestroyPayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "CONVENIENCE_DESTROY_LEGACY",
        null,
        -1L,
        0,
        RtsTraceInputKind.UNKNOWN.wireId(),
        RtsMiningStopOrigin.NONE.wireId(),
        (player, trace) ->
            RtsConvenienceDestroyService.INSTANCE.submit(
                player,
                payload.mode(),
                payload.anchor(),
                Direction.from3DDataValue(payload.face()),
                payload.settings(),
                payload.toolSlot(),
                payload.toolItemId(),
                payload.toolPrototype(),
                payload.toolProtectionEnabled(),
                trace));
  }

  public static void handleConvenienceDestroyTrace(
      C2SRtsConvenienceDestroyTracePayload payload, RtsPayloadContext context) {
    dispatch(
        context,
        "CONVENIENCE_DESTROY",
        payload,
        payload.clientTick(),
        payload.heldMs(),
        payload.inputKind(),
        payload.stopOrigin(),
        (player, trace) ->
            RtsConvenienceDestroyService.INSTANCE.submit(
                player,
                payload.mode(),
                payload.anchor(),
                Direction.from3DDataValue(payload.face()),
                payload.settings(),
                payload.toolSlot(),
                payload.toolItemId(),
                payload.toolPrototype(),
                payload.toolProtectionEnabled(),
                trace));
  }

  private static void dispatch(
      RtsPayloadContext context,
      String packet,
      RtsTracedPayload payload,
      long clientTick,
      int heldMs,
      byte inputKind,
      byte stopOrigin,
      TracedAction action) {
    long receivedNanos = System.nanoTime();
    context.enqueueWork(
        () -> {
          if (!(context.player() instanceof ServerPlayer player)) return;
          var trace =
              RtsServerTraceRegistry.acceptNetwork(
                  player,
                  payload,
                  clientTick,
                  heldMs,
                  inputKind,
                  stopOrigin,
                  packet,
                  receivedNanos,
                  player.serverLevel().getGameTime());
          action.run(player, trace);
        });
  }

  @FunctionalInterface
  private interface TracedAction {
    void run(
        ServerPlayer player,
        com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext trace);
  }
}

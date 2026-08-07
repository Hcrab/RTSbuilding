package com.rtsbuilding.rtsbuilding.server.service.destruction;

import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationTraceContext;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsServerTraceRegistry;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 便捷破坏的服务端权威边界。
 *
 * <p>请求不携带客户端预览的坐标集合；此处从锚点、工具和受限设置重新规划，再提交到正式 AREA_DESTROY 管道。 它不新建第二套破坏执行器，因此工具借用、分
 * tick、领地校验、掉落收纳与撤销语义保持一致。
 */
public final class RtsConvenienceDestroyService {
  public static final RtsConvenienceDestroyService INSTANCE = new RtsConvenienceDestroyService();

  private RtsConvenienceDestroyService() {}

  public RtsConvenienceDestroyPlanner.Plan submit(
      ServerPlayer player,
      RtsConvenienceDestroyMode mode,
      BlockPos anchor,
      Direction face,
      RtsConvenienceDestroySettings settings,
      byte toolSlot,
      String toolItemId,
      ItemStack toolPrototype,
      boolean toolProtectionEnabled) {
    return submit(
        player,
        mode,
        anchor,
        face,
        settings,
        toolSlot,
        toolItemId,
        toolPrototype,
        toolProtectionEnabled,
        RtsOperationTraceContext.legacy("CONVENIENCE_DESTROY"));
  }

  /** trace 只用于关联诊断，服务端仍重新规划目标并走正式 AREA_DESTROY 管道。 */
  public RtsConvenienceDestroyPlanner.Plan submit(
      ServerPlayer player,
      RtsConvenienceDestroyMode mode,
      BlockPos anchor,
      Direction face,
      RtsConvenienceDestroySettings settings,
      byte toolSlot,
      String toolItemId,
      ItemStack toolPrototype,
      boolean toolProtectionEnabled,
      RtsOperationTraceContext trace) {
    if (player == null) return invalidPlan();
    if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)) {
      RtsServerTraceRegistry.terminalWithoutWorkflow(
          player, trace, RtsWorkflowType.AREA_DESTROY, "REJECTED", "FEATURE_LOCKED");
      return invalidPlan();
    }
    RtsConvenienceDestroyPlanner.Plan plan =
        RtsConvenienceDestroyPlanner.plan(player.serverLevel(), mode, anchor, face, settings);
    if (!plan.ready()) {
      notifyRejected(player, plan.code(), settings);
      RtsServerTraceRegistry.terminalWithoutWorkflow(
          player, trace, RtsWorkflowType.AREA_DESTROY, "REJECTED", plan.code().name());
      return plan;
    }
    ServiceRegistry.getInstance()
        .mining()
        .areaDestroy(
            player,
            plan.targets(),
            toolSlot,
            toolItemId == null ? "" : toolItemId,
            toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
            toolProtectionEnabled,
            trace);
    return plan;
  }

  private static RtsConvenienceDestroyPlanner.Plan invalidPlan() {
    return new RtsConvenienceDestroyPlanner.Plan(
        RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, java.util.List.of(), 0);
  }

  private static void notifyRejected(
      ServerPlayer player,
      RtsConvenienceDestroyPlanner.ResultCode code,
      RtsConvenienceDestroySettings rawSettings) {
    String key =
        switch (code) {
          case OVER_LIMIT -> "message.rtsbuilding.convenience_destroy.over_limit";
          case UNLOADED_CHUNK -> "message.rtsbuilding.convenience_destroy.unloaded";
          case EMPTY -> "message.rtsbuilding.convenience_destroy.empty";
          default -> "message.rtsbuilding.convenience_destroy.invalid";
        };
    Component message =
        code == RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT
            ? Component.translatable(
                key, RtsConvenienceDestroyPlanner.sanitize(rawSettings).treeMaxBlocks())
            : Component.translatable(key);
    player.displayClientMessage(message, true);
  }
}

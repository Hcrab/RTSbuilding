package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMineTracePayload;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 在 RTS 单方块挖掘开始前桥接 Fabric 的原生方块左键回调。
 *
 * <p>职责仅限于让真实快捷栏中的工具收到一次 {@link AttackBlockCallback}。当回调消费事件或工具拒绝攻击方块时， RTS
 * 挖掘状态机不会再重复执行；否则仍由既有状态机负责连续挖掘、掉落、耐久、租借工具和撤销记录。 远程选择的工具绝不能进入这里，因为它并不处于玩家真实主手，继续由 RTS 服务安全处理。
 */
public final class RtsNativeLeftClickBridge {
  private RtsNativeLeftClickBridge() {}

  /**
   * 处理兼容期旧包。旧包不携带射线和潜行信息，因此以目标方块中心和非潜行为保守回退。
   *
   * @return {@code true} 表示左键已经被消费，调用方不得启动 RTS 挖掘。
   */
  public static boolean interceptMiningStart(ServerPlayer player, C2SRtsMinePayload payload) {
    if (player == null
        || payload == null
        || !payload.start()
        || usesRemoteSelectedTool(payload.toolItemId(), payload.toolPrototype())) {
      return false;
    }
    return intercept(
        player,
        payload.pos(),
        Direction.from3DDataValue(payload.face()),
        payload.toolSlot(),
        false,
        Vec3.atCenterOf(payload.pos()),
        null);
  }

  /**
   * v2 诊断包只扩展链路字段；这里复用同一个原生左键判定并利用其完整的射线/潜行上下文。
   *
   * @return {@code true} 表示左键已经被消费，调用方不得启动 RTS 挖掘。
   */
  public static boolean interceptMiningStart(ServerPlayer player, C2SRtsMineTracePayload payload) {
    if (player == null
        || payload == null
        || !payload.start()
        || usesRemoteSelectedTool(payload.toolItemId(), payload.toolPrototype())) {
      return false;
    }
    BlockPos pos = payload.pos();
    TemporaryContextSwitcher.RayContext rayContext =
        TemporaryContextSwitcher.parseRayContext(
            payload.rayOriginX(),
            payload.rayOriginY(),
            payload.rayOriginZ(),
            payload.rayDirX(),
            payload.rayDirY(),
            payload.rayDirZ());
    return intercept(
        player,
        pos,
        Direction.from3DDataValue(payload.face()),
        payload.toolSlot(),
        payload.shiftDown(),
        validatedHitLocation(payload.hitX(), payload.hitY(), payload.hitZ(), pos),
        rayContext);
  }

  private static boolean intercept(
      ServerPlayer player,
      BlockPos pos,
      Direction face,
      int requestedToolSlot,
      boolean shiftDown,
      Vec3 hitLocation,
      TemporaryContextSwitcher.RayContext rayContext) {
    if (pos == null
        || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)
        || !RtsClaimProtectionService.canBreakBlock(player, pos, face)) {
      rejectMining(player, pos);
      return true;
    }

    boolean consumed =
        TemporaryContextSwitcher.withTemporaryUseItemContext(
            player,
            hitLocation,
            hitLocation,
            rayContext,
            Config.remotePovBlockReach(),
            () ->
                TemporaryContextSwitcher.withTemporarySelectedSlot(
                    player,
                    Mth.clamp(requestedToolSlot, 0, 8),
                    () ->
                        TemporaryContextSwitcher.withTemporaryShiftKey(
                            player,
                            shiftDown,
                            () -> postLeftClickAndCheckMining(player, pos, face))));

    if (consumed) {
      player.inventoryMenu.broadcastChanges();
      rejectMining(player, pos);
    }
    return consumed;
  }

  private static boolean postLeftClickAndCheckMining(
      ServerPlayer player, BlockPos pos, Direction face) {
    InteractionResult result =
        AttackBlockCallback.EVENT
            .invoker()
            .interact(player, player.serverLevel(), InteractionHand.MAIN_HAND, pos, face);
    if (result != InteractionResult.PASS) {
      return true;
    }

    ItemStack stack = player.getMainHandItem();
    if (stack.isEmpty()) {
      return false;
    }
    BlockState state = player.serverLevel().getBlockState(pos);
    return !stack.getItem().canAttackBlock(state, player.serverLevel(), pos, player);
  }

  private static boolean usesRemoteSelectedTool(String toolItemId, ItemStack toolPrototype) {
    return (toolItemId != null && !toolItemId.isBlank())
        || (toolPrototype != null && !toolPrototype.isEmpty());
  }

  private static Vec3 validatedHitLocation(double hitX, double hitY, double hitZ, BlockPos pos) {
    Vec3 center = Vec3.atCenterOf(pos);
    if (!Double.isFinite(hitX) || !Double.isFinite(hitY) || !Double.isFinite(hitZ)) {
      return center;
    }
    Vec3 hit = new Vec3(hitX, hitY, hitZ);
    return hit.x >= pos.getX() - 0.01D
            && hit.x <= pos.getX() + 1.01D
            && hit.y >= pos.getY() - 0.01D
            && hit.y <= pos.getY() + 1.01D
            && hit.z >= pos.getZ() - 0.01D
            && hit.z <= pos.getZ() + 1.01D
        ? hit
        : center;
  }

  private static void rejectMining(ServerPlayer player, BlockPos pos) {
    if (player != null && pos != null) {
      RtsMiningNetworkHelper.sendMineProgress(player, pos, -1);
    }
  }
}

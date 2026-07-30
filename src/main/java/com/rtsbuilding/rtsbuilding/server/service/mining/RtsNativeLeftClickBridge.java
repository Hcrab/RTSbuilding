package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 在 RTS 单方块挖掘开始前桥接 NeoForge 的原生左键方块事件。
 *
 * <p>本类只处理玩家真实快捷栏中的物品，并且每次按下只触发一次。它不接管 RTS
 * 的持续挖掘、掉落、工具租借或撤销记录；第三方模组没有消费左键时，原有挖掘状态机
 * 仍是唯一执行者。这样既能让 Worldshaper 一类依赖 {@code LeftClickBlock} 的物品工作，
 * 又不会在按住鼠标期间重复触发第三方物品逻辑。
 */
public final class RtsNativeLeftClickBridge {
    private RtsNativeLeftClickBridge() {
    }

    /**
     * @return {@code true} 表示本次左键已被第三方物品消费或被安全校验拒绝，调用方不得启动 RTS 挖掘。
     */
    public static boolean interceptMiningStart(ServerPlayer player, C2SRtsMinePayload payload) {
        if (player == null || payload == null || !payload.start() || usesRemoteSelectedTool(payload)) {
            return false;
        }

        BlockPos pos = payload.pos();
        Direction face = Direction.from3DDataValue(payload.face());
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)
                || !RtsClaimProtectionService.canBreakBlock(player, pos, face)) {
            rejectMining(player, pos);
            return true;
        }

        Vec3 hitLocation = validatedHitLocation(payload, pos);
        TemporaryContextSwitcher.RayContext rayContext = TemporaryContextSwitcher.parseRayContext(
                payload.rayOriginX(), payload.rayOriginY(), payload.rayOriginZ(),
                payload.rayDirX(), payload.rayDirY(), payload.rayDirZ());
        int toolSlot = Mth.clamp(payload.toolSlot(), 0, 8);

        boolean consumed = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                hitLocation,
                hitLocation,
                rayContext,
                Config.remotePovBlockReach(),
                () -> TemporaryContextSwitcher.withTemporarySelectedSlot(
                        player,
                        toolSlot,
                        () -> TemporaryContextSwitcher.withTemporaryShiftKey(
                                player,
                                payload.shiftDown(),
                                () -> postLeftClickAndCheckMining(player, pos, face))));

        if (consumed) {
            player.inventoryMenu.broadcastChanges();
            rejectMining(player, pos);
        }
        return consumed;
    }

    private static boolean postLeftClickAndCheckMining(ServerPlayer player, BlockPos pos, Direction face) {
        PlayerInteractEvent.LeftClickBlock event = CommonHooks.onLeftClickBlock(
                player,
                pos,
                face,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        if (event.isCanceled() || event.getUseItem().isFalse()) {
            return true;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        BlockState state = player.serverLevel().getBlockState(pos);
        return !stack.getItem().canAttackBlock(state, player.serverLevel(), pos, player);
    }

    private static boolean usesRemoteSelectedTool(C2SRtsMinePayload payload) {
        return (payload.toolItemId() != null && !payload.toolItemId().isBlank())
                || (payload.toolPrototype() != null && !payload.toolPrototype().isEmpty());
    }

    private static Vec3 validatedHitLocation(C2SRtsMinePayload payload, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (!Double.isFinite(payload.hitX())
                || !Double.isFinite(payload.hitY())
                || !Double.isFinite(payload.hitZ())) {
            return center;
        }
        Vec3 hit = new Vec3(payload.hitX(), payload.hitY(), payload.hitZ());
        return hit.x >= pos.getX() - 0.01D && hit.x <= pos.getX() + 1.01D
                && hit.y >= pos.getY() - 0.01D && hit.y <= pos.getY() + 1.01D
                && hit.z >= pos.getZ() - 0.01D && hit.z <= pos.getZ() + 1.01D
                ? hit
                : center;
    }

    private static void rejectMining(ServerPlayer player, BlockPos pos) {
        RtsMiningNetworkHelper.sendMineProgress(player, pos, -1);
    }
}

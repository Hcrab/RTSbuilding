package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMineTracePayload;
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
 * 在 RTS 单方块挖掘开始前桥接 NeoForge 原生左键事件。
 * 只处理真实快捷栏物品；远程借用工具仍走 RTS 采掘状态机。
 */
public final class RtsNativeLeftClickBridge {
    private RtsNativeLeftClickBridge() {
    }

    /** legacy 包没有命中点、射线和潜行位，使用方块中心与非潜行安全回退。 */
    public static boolean interceptMiningStart(
            ServerPlayer player, C2SRtsMinePayload payload) {
        if (player == null || payload == null || !payload.start()
                || usesRemoteSelectedTool(payload.toolItemId(), payload.toolPrototype())) {
            return false;
        }
        return interceptMiningStart(
                player,
                payload.pos(),
                payload.face(),
                payload.toolSlot(),
                false,
                Vec3.atCenterOf(payload.pos()),
                null);
    }

    public static boolean interceptMiningStart(
            ServerPlayer player, C2SRtsMineTracePayload payload) {
        if (player == null || payload == null || !payload.start()
                || usesRemoteSelectedTool(payload.toolItemId(), payload.toolPrototype())) {
            return false;
        }
        Vec3 hitLocation = validatedHitLocation(payload, payload.pos());
        TemporaryContextSwitcher.RayContext rayContext = TemporaryContextSwitcher.parseRayContext(
                payload.rayOriginX(), payload.rayOriginY(), payload.rayOriginZ(),
                payload.rayDirX(), payload.rayDirY(), payload.rayDirZ());
        return interceptMiningStart(
                player,
                payload.pos(),
                payload.face(),
                payload.toolSlot(),
                payload.shiftDown(),
                hitLocation,
                rayContext);
    }

    private static boolean interceptMiningStart(
            ServerPlayer player,
            BlockPos pos,
            byte faceValue,
            byte toolSlotValue,
            boolean shiftDown,
            Vec3 hitLocation,
            TemporaryContextSwitcher.RayContext rayContext) {
        Direction face = Direction.from3DDataValue(faceValue);
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)
                || !RtsClaimProtectionService.canBreakBlock(player, pos, face)) {
            rejectMining(player, pos);
            return true;
        }
        int toolSlot = Mth.clamp(toolSlotValue, 0, 8);
        boolean consumed = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player, hitLocation, hitLocation, rayContext, Config.remotePovBlockReach(),
                () -> TemporaryContextSwitcher.withTemporarySelectedSlot(player, toolSlot,
                        () -> TemporaryContextSwitcher.withTemporaryShiftKey(player, shiftDown,
                                () -> postLeftClickAndCheckMining(player, pos, face))));
        if (consumed) {
            player.inventoryMenu.broadcastChanges();
            rejectMining(player, pos);
        }
        return consumed;
    }

    private static boolean postLeftClickAndCheckMining(
            ServerPlayer player, BlockPos pos, Direction face) {
        PlayerInteractEvent.LeftClickBlock event = CommonHooks.onLeftClickBlock(
                player, pos, face, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        if (event.isCanceled() || event.getUseItem().isFalse()) {
            return true;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        BlockState state = player.level().getBlockState(pos);
        return !stack.getItem().canDestroyBlock(stack, state, player.level(), pos, player);
    }

    private static boolean usesRemoteSelectedTool(String toolItemId, ItemStack toolPrototype) {
        return (toolItemId != null && !toolItemId.isBlank())
                || (toolPrototype != null && !toolPrototype.isEmpty());
    }

    private static Vec3 validatedHitLocation(C2SRtsMineTracePayload payload, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        if (!Double.isFinite(payload.hitX()) || !Double.isFinite(payload.hitY())
                || !Double.isFinite(payload.hitZ())) {
            return center;
        }
        Vec3 hit = new Vec3(payload.hitX(), payload.hitY(), payload.hitZ());
        return hit.x >= pos.getX() - 0.01D && hit.x <= pos.getX() + 1.01D
                && hit.y >= pos.getY() - 0.01D && hit.y <= pos.getY() + 1.01D
                && hit.z >= pos.getZ() - 0.01D && hit.z <= pos.getZ() + 1.01D
                ? hit : center;
    }

    private static void rejectMining(ServerPlayer player, BlockPos pos) {
        RtsMiningNetworkHelper.sendMineProgress(player, pos, -1);
    }
}

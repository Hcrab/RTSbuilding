package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * 在 RTS 单方块挖掘开始前桥接 Forge 1.12.2 的原生左键方块事件。
 *
 * <p>只处理玩家真实快捷栏中的物品，每次开始包只触发一次；远程存储借出的工具仍由
 * RTS 工具租约处理。第三方模组未消费事件时，原有挖掘状态机保持唯一执行者。</p>
 */
public final class RtsNativeLeftClickBridge {
    private RtsNativeLeftClickBridge() {}

    /** @return true 表示第三方物品已消费本次左键，调用方不得再启动 RTS 挖掘。 */
    public static boolean interceptMiningStart(final EntityPlayerMP player,
            final C2SRtsMinePayload payload) {
        if (player == null || payload == null || !payload.start() || usesRemoteSelectedTool(payload)) {
            return false;
        }
        final BlockPos pos = payload.pos();
        final EnumFacing face = EnumFacing.byIndex(payload.face());
        final Vec3d hit = validatedHitLocation(payload, pos);
        TemporaryContextSwitcher.RayContext ray = TemporaryContextSwitcher.parseRayContext(
                payload.rayOriginX(), payload.rayOriginY(), payload.rayOriginZ(),
                payload.rayDirX(), payload.rayDirY(), payload.rayDirZ());
        Vec3i normal = face.getDirectionVec();
        Vec3d fallbackFeet = new Vec3d(
                hit.x + normal.getX() * 2.0D,
                hit.y + normal.getY() * 2.0D - player.getEyeHeight(),
                hit.z + normal.getZ() * 2.0D);
        final int slot = Math.max(0, Math.min(8, payload.toolSlot()));
        boolean consumed = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player, fallbackFeet, hit, ray, Config.remotePovBlockReach(),
                new java.util.function.Supplier<Boolean>() {
                    @Override public Boolean get() {
                        return TemporaryContextSwitcher.withTemporarySelectedSlot(player, slot,
                                new java.util.function.Supplier<Boolean>() {
                                    @Override public Boolean get() {
                                        return TemporaryContextSwitcher.withTemporaryShiftKey(
                                                player, payload.shiftDown(),
                                                new java.util.function.Supplier<Boolean>() {
                                                    @Override public Boolean get() {
                                                        return Boolean.valueOf(postLeftClick(player, pos, face, hit));
                                                    }
                                                });
                                    }
                                });
                    }
                }).booleanValue();
        if (consumed) {
            player.inventory.markDirty();
            RtsMiningNetworkHelper.sendMineProgress(player, pos, -1);
        }
        return consumed;
    }

    private static boolean postLeftClick(EntityPlayerMP player, BlockPos pos,
            EnumFacing face, Vec3d hit) {
        PlayerInteractEvent.LeftClickBlock event = ForgeHooks.onLeftClickBlock(player, pos, face, hit);
        if (event.isCanceled() || event.getUseItem() == Event.Result.DENY) return true;
        ItemStack stack = player.getHeldItemMainhand();
        return !stack.isEmpty() && stack.getItem().onBlockStartBreak(stack, pos, player);
    }

    private static boolean usesRemoteSelectedTool(C2SRtsMinePayload payload) {
        return (payload.toolItemId() != null && !payload.toolItemId().trim().isEmpty())
                || (payload.toolPrototype() != null && !payload.toolPrototype().isEmpty());
    }

    private static Vec3d validatedHitLocation(C2SRtsMinePayload payload, BlockPos pos) {
        Vec3d center = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (!Double.isFinite(payload.hitX()) || !Double.isFinite(payload.hitY())
                || !Double.isFinite(payload.hitZ())) return center;
        Vec3d hit = new Vec3d(payload.hitX(), payload.hitY(), payload.hitZ());
        return hit.x >= pos.getX() - 0.01D && hit.x <= pos.getX() + 1.01D
                && hit.y >= pos.getY() - 0.01D && hit.y <= pos.getY() + 1.01D
                && hit.z >= pos.getZ() - 0.01D && hit.z <= pos.getZ() + 1.01D
                ? hit : center;
    }
}

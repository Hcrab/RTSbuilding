package com.rtsbuilding.rtsbuilding.server.protection;

import com.rtsbuilding.rtsbuilding.compat.openpac.RtsOpenPacCompat;
import com.rtsbuilding.rtsbuilding.compat.sable.RtsSableSpatialCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * RTS 世界修改的统一区块保护入口。
 *
 * <p>射程、相机会话和世界边界判断仍由原来的 resolver 负责；本类只处理
 * “这个玩家能不能在此处执行某类动作”。使用动作级方法而不是一个泛用
 * canAccess，是为了让 OpenPAC 这类 claim mod 能区分放置、破坏和交互权限。
 */
public final class RtsClaimProtectionService {
    private RtsClaimProtectionService() {
    }

    public static boolean canBreakBlock(ServerPlayer player, BlockPos pos, Direction face) {
        BlockPos physicalPos = physicalBlockPos(player, pos);
        Direction physicalFace = physicalDirection(player, pos, face);
        return physicalPos != null && RtsOpenPacCompat.canBreakBlock(player, physicalPos, physicalFace);
    }

    public static boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
        BlockPos physicalPos = physicalBlockPos(player, pos);
        return physicalPos != null && RtsOpenPacCompat.canPlaceBlock(player, physicalPos);
    }

    public static boolean canInteractBlock(ServerPlayer player, BlockPos pos, Direction face,
            InteractionHand hand, ItemStack heldItem) {
        BlockPos physicalPos = physicalBlockPos(player, pos);
        Direction physicalFace = physicalDirection(player, pos, face);
        return physicalPos != null
                && RtsOpenPacCompat.canInteractBlock(player, physicalPos, physicalFace, hand, heldItem);
    }

    public static boolean canInteractEntity(ServerPlayer player, Entity target, InteractionHand hand,
            ItemStack heldItem, boolean attack) {
        return player != null && target != null
                && RtsOpenPacCompat.canInteractEntity(player, target, hand, heldItem, attack);
    }

    private static BlockPos physicalBlockPos(ServerPlayer player, BlockPos logicalPos) {
        return player == null || logicalPos == null
                ? null
                : RtsSableSpatialCompat.physicalBlockPos(player.serverLevel(), logicalPos);
    }

    private static Direction physicalDirection(ServerPlayer player, BlockPos logicalPos, Direction logicalDirection) {
        return player == null || logicalPos == null
                ? logicalDirection
                : RtsSableSpatialCompat.physicalDirection(
                        player.serverLevel(), logicalPos, logicalDirection);
    }
}

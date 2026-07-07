package com.rtsbuilding.rtsbuilding.server.protection;

import com.rtsbuilding.rtsbuilding.compat.openpac.RtsOpenPacCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * RTS 世界修改的统一区块保护入口。
 *
 * <p>射程、相机、家园半径这些判断仍由原来的 resolver 负责；本类只处理
 * “这个玩家能不能在此处执行某类动作”。使用动作级方法而不是一个泛用
 * canAccess，是为了让 OpenPAC 这类 claim mod 能区分放置、破坏和交互权限。
 */
public final class RtsClaimProtectionService {
    private RtsClaimProtectionService() {
    }

    public static boolean canBreakBlock(ServerPlayer player, BlockPos pos, Direction face) {
        return player != null && pos != null && RtsOpenPacCompat.canBreakBlock(player, pos, face);
    }

    public static boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
        return player != null && pos != null && RtsOpenPacCompat.canPlaceBlock(player, pos);
    }

    public static boolean canInteractBlock(ServerPlayer player, BlockPos pos, Direction face,
            InteractionHand hand, ItemStack heldItem) {
        return player != null && pos != null
                && RtsOpenPacCompat.canInteractBlock(player, pos, face, hand, heldItem);
    }

    public static boolean canInteractEntity(ServerPlayer player, Entity target, InteractionHand hand,
            ItemStack heldItem, boolean attack) {
        return player != null && target != null
                && RtsOpenPacCompat.canInteractEntity(player, target, hand, heldItem, attack);
    }
}

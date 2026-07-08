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
 * <p>射程、相机与家园半径仍由原有 resolver 处理。本类只回答动作级问题：
 * 玩家能否在指定位置放置、破坏、交互，或能否交互实体。这样 OpenPAC 这类
 * claim mod 可以正确区分不同权限，而不是被一个泛用 canAccess 判断绕过。
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

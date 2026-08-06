package com.rtsbuilding.rtsbuilding.server.protection;

import com.rtsbuilding.rtsbuilding.compat.openpac.RtsOpenPacCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

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

    public static boolean canBreakBlock(EntityPlayerMP player, BlockPos pos, EnumFacing face) {
        return player != null && pos != null && RtsOpenPacCompat.canBreakBlock(player, pos, face);
    }

    public static boolean canPlaceBlock(EntityPlayerMP player, BlockPos pos) {
        return player != null && pos != null && RtsOpenPacCompat.canPlaceBlock(player, pos);
    }

    public static boolean canInteractBlock(EntityPlayerMP player, BlockPos pos, EnumFacing face,
            EnumHand hand, ItemStack heldItem) {
        return player != null && pos != null
                && RtsOpenPacCompat.canInteractBlock(player, pos, face, hand, heldItem);
    }

    /** 在目标世界检查储存端点；跨维时直接查询目标维度的 claim，不改变玩家所在世界。 */
    public static boolean canInteractBlockInWorld(EntityPlayerMP player, WorldServer level, BlockPos pos,
            EnumFacing face, EnumHand hand, ItemStack heldItem) {
        if (player == null || level == null || pos == null || !level.isBlockModifiable(player, pos)) {
            return false;
        }
        if (player.getServerWorld() != level) {
            return RtsOpenPacCompat.canInteractBlockInWorld(player, level, pos);
        }
        return canInteractBlock(player, pos, face, hand, heldItem);
    }

    public static boolean canInteractEntity(EntityPlayerMP player, Entity target, EnumHand hand,
            ItemStack heldItem, boolean attack) {
        return player != null && target != null
                && RtsOpenPacCompat.canInteractEntity(player, target, hand, heldItem, attack);
    }
}

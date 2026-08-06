package com.rtsbuilding.rtsbuilding.compat.openpac;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;

/**
 * 1.12 claim 兼容聚合门面。
 *
 * <p>历史原因使服务层仍从这个类进入，但它不再代表“只检查 OpenPAC”：
 * 所有世界动作都会先叠加 FTB Utilities 5.4.1.x 的真实 claim 判定。
 * OpenPAC 官方没有 1.12.2 版本，所以正常环境下它只贡献空队伍和“不额外拒绝”；
 * 如果整合包出现同名的未知非官方 backport，本门面会保守拒绝动作，绝不假装现代
 * {@code OpenPACServerAPI} 能在 1.12 工作。</p>
 */
public final class RtsOpenPacCompat {
    private static final String MOD_ID = "openpartiesandclaims";
    private static final boolean OPENPAC_LOADED = Loader.isModLoaded(MOD_ID);

    static {
        if (OPENPAC_LOADED) {
            RtsbuildingMod.LOGGER.warn(
                    "{}; detected mod id '{}' will be fail-closed for RTS actions.",
                    RtsOpenPacCompatImpl.unavailableReason(), MOD_ID);
        }
    }

    private RtsOpenPacCompat() {
    }

    public static String progressionTeamKey(EntityPlayerMP player) {
        return "";
    }

    public static String progressionTeamLabel(EntityPlayerMP player) {
        return "";
    }

    public static boolean canBreakBlock(EntityPlayerMP player, BlockPos pos, EnumFacing face) {
        return player != null && pos != null
                && RtsFtbCompat.canEditBlock(player, pos)
                && !OPENPAC_LOADED;
    }

    public static boolean canPlaceBlock(EntityPlayerMP player, BlockPos pos) {
        return player != null && pos != null
                && RtsFtbCompat.canEditBlock(player, pos)
                && !OPENPAC_LOADED;
    }

    public static boolean canInteractBlock(EntityPlayerMP player, BlockPos pos, EnumFacing face,
            EnumHand hand, ItemStack heldItem) {
        return player != null && pos != null
                && RtsFtbCompat.canInteractBlock(player, pos, face, hand, heldItem)
                && !OPENPAC_LOADED;
    }

    /** 跨维目标不能复用玩家当前世界；FTB 走目标维度查询，未知 OpenPAC backport 仍保守拒绝。 */
    public static boolean canInteractBlockInWorld(EntityPlayerMP player, WorldServer level, BlockPos pos) {
        return player != null && level != null && pos != null
                && RtsFtbCompat.canInteractBlockInWorld(player, level, pos)
                && !OPENPAC_LOADED;
    }

    public static boolean canInteractEntity(EntityPlayerMP player, Entity target, EnumHand hand,
            ItemStack heldItem, boolean attack) {
        return player != null && target != null
                && RtsFtbCompat.canInteractEntity(player, target, hand, heldItem, attack)
                && !OPENPAC_LOADED;
    }
}

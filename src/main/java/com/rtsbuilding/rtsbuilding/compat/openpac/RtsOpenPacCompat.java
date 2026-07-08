package com.rtsbuilding.rtsbuilding.compat.openpac;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Open Parties and Claims 的可选兼容入口。
 *
 * <p>RTSBuilding 不直接依赖 OpenPAC jar，所有调用都通过反射完成。未安装
 * OpenPAC 时，队伍共享会继续回落到 FTB Teams / 原版队伍，区块保护检查则放行。
 */
public final class RtsOpenPacCompat {
    private static final String MOD_ID = "openpartiesandclaims";
    private static final boolean OPENPAC_LOADED = ModList.get().isLoaded(MOD_ID);
    private static final RtsOpenPacCompatImpl IMPL = createImpl();

    private RtsOpenPacCompat() {
    }

    public static String progressionTeamKey(ServerPlayer player) {
        if (IMPL == null || player == null) {
            return "";
        }
        RtsOpenPacCompatImpl.TeamInfo team = IMPL.teamInfo(player);
        return team == null ? "" : team.key();
    }

    public static String progressionTeamLabel(ServerPlayer player) {
        if (IMPL == null || player == null) {
            return "";
        }
        RtsOpenPacCompatImpl.TeamInfo team = IMPL.teamInfo(player);
        return team == null ? "" : team.label();
    }

    public static boolean canBreakBlock(ServerPlayer player, BlockPos pos, Direction face) {
        if (IMPL == null || player == null || pos == null) {
            return true;
        }
        return IMPL.canBreakBlock(player, pos, face == null ? Direction.DOWN : face);
    }

    public static boolean canPlaceBlock(ServerPlayer player, BlockPos pos) {
        if (IMPL == null || player == null || pos == null) {
            return true;
        }
        return IMPL.canPlaceBlock(player, pos);
    }

    public static boolean canInteractBlock(ServerPlayer player, BlockPos pos, Direction face,
            InteractionHand hand, ItemStack heldItem) {
        if (IMPL == null || player == null || pos == null) {
            return true;
        }
        return IMPL.canInteractBlock(player, pos, face == null ? Direction.UP : face, hand, heldItem);
    }

    public static boolean canInteractEntity(ServerPlayer player, Entity target, InteractionHand hand,
            ItemStack heldItem, boolean attack) {
        if (IMPL == null || player == null || target == null) {
            return true;
        }
        return IMPL.canInteractEntity(player, target, hand, heldItem, attack);
    }

    private static RtsOpenPacCompatImpl createImpl() {
        if (!OPENPAC_LOADED) {
            return null;
        }
        try {
            return new RtsOpenPacCompatImpl();
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn(
                    "OpenPAC compat init failed; RTSBuilding will not use OpenPAC parties or claim checks.",
                    throwable);
            return null;
        }
    }
}

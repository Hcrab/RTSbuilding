package com.rtsbuilding.rtsbuilding.compat.ftb;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;

/**
 * FTB Utilities 5.4.1.x 的 claim 权限桥。
 *
 * <p>该版本的三个 {@code block*} 方法返回“应阻止”，而
 * {@code canAttackEntity} 返回“可允许”。这里集中翻转语义，避免调用方把拒绝当成允许。
 * 反射调用一旦异常便拒绝当前动作，不能因为兼容层故障绕过领地保护。</p>
 */
final class RtsFtbClaimsCompatImpl {
    private final Method blockBlockEditingMethod;
    private final Method blockBlockInteractionsMethod;
    private final Method blockItemUseMethod;
    private final Method canAttackEntityMethod;
    private boolean warnedRuntimeFailure;

    RtsFtbClaimsCompatImpl() throws ReflectiveOperationException {
        Class<?> claimedChunksClass = Class.forName("com.feed_the_beast.ftbutilities.data.ClaimedChunks");
        this.blockBlockEditingMethod = claimedChunksClass.getMethod(
                "blockBlockEditing", EntityPlayer.class, BlockPos.class, IBlockState.class);
        this.blockBlockInteractionsMethod = claimedChunksClass.getMethod(
                "blockBlockInteractions", EntityPlayer.class, BlockPos.class, IBlockState.class);
        this.blockItemUseMethod = claimedChunksClass.getMethod(
                "blockItemUse", EntityPlayer.class, EnumHand.class, BlockPos.class);
        this.canAttackEntityMethod = claimedChunksClass.getMethod(
                "canAttackEntity", EntityPlayer.class, Entity.class);
    }

    boolean canEditBlock(EntityPlayerMP player, BlockPos pos) {
        try {
            return !invokeBoolean(this.blockBlockEditingMethod, null, player, pos, null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("FTB Utilities claim edit check failed; denying this RTS action.", exception);
            return false;
        }
    }

    boolean canInteractBlock(EntityPlayerMP player, BlockPos pos, EnumHand hand, ItemStack heldItem) {
        try {
            if (invokeBoolean(this.blockBlockInteractionsMethod, null, player, pos, null)) {
                return false;
            }
            EnumHand actualHand = hand == null ? EnumHand.MAIN_HAND : hand;
            return !invokeBoolean(this.blockItemUseMethod, null, player, actualHand, pos);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("FTB Utilities claim interaction check failed; denying this RTS action.", exception);
            return false;
        }
    }

    boolean canInteractEntity(EntityPlayerMP player, Entity target, boolean attack) {
        if (!attack) {
            // FTB Utilities 5.4.1.x 没有独立的实体右键 claim 门；不能伪造不存在的权限。
            return true;
        }
        try {
            return invokeBoolean(this.canAttackEntityMethod, null, player, target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("FTB Utilities claim entity attack check failed; denying this RTS action.", exception);
            return false;
        }
    }

    private static boolean invokeBoolean(Method method, Object owner, Object... arguments)
            throws ReflectiveOperationException {
        return Boolean.TRUE.equals(method.invoke(owner, arguments));
    }

    private void warnOnce(String message, Throwable throwable) {
        if (!this.warnedRuntimeFailure) {
            this.warnedRuntimeFailure = true;
            RtsbuildingMod.LOGGER.warn(message, throwable);
        }
    }
}

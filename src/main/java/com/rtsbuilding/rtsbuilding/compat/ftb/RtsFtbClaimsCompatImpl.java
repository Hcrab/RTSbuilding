package com.rtsbuilding.rtsbuilding.compat.ftb;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
    private final Method isActiveMethod;
    private final Field claimedChunksInstanceField;
    private final Field claimedChunksUniverseField;
    private final Constructor<?> chunkDimPosConstructor;
    private final Method getChunkMethod;
    private final Method getPlayerMethod;
    private final Method getChunkTeamMethod;
    private final Method getChunkDataMethod;
    private final Method getInteractWithBlocksStatusMethod;
    private final Method teamHasStatusMethod;
    private final Method hasBlockInteractionPermissionMethod;
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

        Class<?> chunkDimPosClass = Class.forName("com.feed_the_beast.ftblib.lib.math.ChunkDimPos");
        Class<?> universeClass = Class.forName("com.feed_the_beast.ftblib.lib.data.Universe");
        Class<?> forgePlayerClass = Class.forName("com.feed_the_beast.ftblib.lib.data.ForgePlayer");
        Class<?> claimedChunkClass = Class.forName("com.feed_the_beast.ftbutilities.data.ClaimedChunk");
        Class<?> teamDataClass = Class.forName("com.feed_the_beast.ftbutilities.data.FTBUtilitiesTeamData");
        Class<?> forgeTeamClass = Class.forName("com.feed_the_beast.ftblib.lib.data.ForgeTeam");
        Class<?> teamStatusClass = Class.forName("com.feed_the_beast.ftblib.lib.EnumTeamStatus");
        Class<?> permissionsClass = Class.forName("com.feed_the_beast.ftbutilities.FTBUtilitiesPermissions");

        this.isActiveMethod = claimedChunksClass.getMethod("isActive");
        this.claimedChunksInstanceField = claimedChunksClass.getField("instance");
        this.claimedChunksUniverseField = claimedChunksClass.getField("universe");
        this.chunkDimPosConstructor = chunkDimPosClass.getConstructor(BlockPos.class, int.class);
        this.getChunkMethod = claimedChunksClass.getMethod("getChunk", chunkDimPosClass);
        this.getPlayerMethod = universeClass.getMethod("getPlayer", net.minecraft.command.ICommandSender.class);
        this.getChunkTeamMethod = claimedChunkClass.getMethod("getTeam");
        this.getChunkDataMethod = claimedChunkClass.getMethod("getData");
        this.getInteractWithBlocksStatusMethod = teamDataClass.getMethod("getInteractWithBlocksStatus");
        this.teamHasStatusMethod = forgeTeamClass.getMethod("hasStatus", forgePlayerClass, teamStatusClass);
        this.hasBlockInteractionPermissionMethod = permissionsClass.getMethod(
                "hasBlockInteractionPermission", EntityPlayer.class, net.minecraft.block.Block.class);
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

    /**
     * 按目标维度直接读取 FTB Utilities 的 claim 数据。
     *
     * <p>原版公开入口会从 {@code player.dimension} 推导维度，不能用于 RTS 跨维储存。
     * 这里复用同一份 claim、队伍状态与方块白名单数据，只替换目标维度来源；不会临时搬动玩家，
     * 也不会因为玩家肉身不在目标世界就把合法的跨维储存整体禁用。</p>
     */
    boolean canInteractBlockInDimension(EntityPlayerMP player, BlockPos pos, int dimensionId,
            IBlockState targetState) {
        try {
            if (!invokeBoolean(this.isActiveMethod, null)) {
                return true;
            }
            Object claimedChunks = this.claimedChunksInstanceField.get(null);
            if (claimedChunks == null) {
                return true;
            }
            IBlockState actualState = targetState == null ? player.world.getBlockState(pos) : targetState;
            if (invokeBoolean(this.hasBlockInteractionPermissionMethod, null,
                    player, actualState.getBlock())) {
                return true;
            }
            Object chunkDimPos = this.chunkDimPosConstructor.newInstance(pos, dimensionId);
            Object claimedChunk = this.getChunkMethod.invoke(claimedChunks, chunkDimPos);
            if (claimedChunk == null) {
                return true;
            }
            Object universe = this.claimedChunksUniverseField.get(claimedChunks);
            Object forgePlayer = this.getPlayerMethod.invoke(universe, player);
            Object team = this.getChunkTeamMethod.invoke(claimedChunk);
            Object teamData = this.getChunkDataMethod.invoke(claimedChunk);
            Object status = this.getInteractWithBlocksStatusMethod.invoke(teamData);
            return invokeBoolean(this.teamHasStatusMethod, team, forgePlayer, status);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce("FTB Utilities cross-dimension claim interaction check failed; denying this RTS action.",
                    exception);
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

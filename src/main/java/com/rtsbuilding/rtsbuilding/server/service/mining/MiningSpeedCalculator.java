package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 挖掘速度计算工具集——纯函数式计算，无状态。
 *
 * <p>从 {@link RtsMiningStateMachine} 提取，包含远程挖掘的每 tick 破坏进度推算、
 * 水下速度惩罚消除、附魔效率计算等。所有方法均为静态且无副作用。</p>
 *
 * <p><b>核心入口：</b>{@link #computeRemoteDestroyStep} 被挖掘状态机、连锁挖掘处理器、
 * 区域破坏批处理等多个模块调用。</p>
 */
public final class MiningSpeedCalculator {

    private MiningSpeedCalculator() {
    }

    // ======================================================================
    //  核心入口
    // ======================================================================

    /**
     * 计算给定方块/工具组合的每 tick 破坏进度。
     *
     * @return (0.0, 1.0] 范围的每 tick 进度，≤ 0.0 表示无法挖掘
     */
    public static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos,
                                                  int toolSlot, ItemStack linkedTool, boolean selectedToolRequested) {
        if (linkedTool != null && !linkedTool.isEmpty()) {
            return TemporaryContextSwitcher.withTemporaryOnGround(player, true, () -> removeMiningSpeedPenalty(player,
                    computeDestroyStepForTool(player, state, pos, linkedTool)));
        }
        if (selectedToolRequested) {
            return 0.0F;
        }
        return TemporaryContextSwitcher.withTemporaryOnGround(player, true, () ->
                TemporaryContextSwitcher.withTemporarySelectedSlot(
                        player,
                        toolSlot,
                        () -> removeMiningSpeedPenalty(player,
                                state.getDestroyProgress(player, player.level(), pos))));
    }

    // ======================================================================
    //  工具挖掘速度
    // ======================================================================

    /**
     * 使用指定工具栈直接计算挖掘速度，无需实际切换玩家主手物品。
     *
     * <p>复制了 {@code BlockState.getDestroyProgress(Player, Level, BlockPos)}
     * 的逻辑但使用提供的工具栈而非玩家主手物品，避免触发
     * {@code ClientboundContainerSetSlotPacket} 同步风暴。</p>
     */
    public static float computeDestroyStepForTool(ServerPlayer player, BlockState state, BlockPos pos, ItemStack tool) {
        float destroySpeed = state.getDestroySpeed(player.level(), pos);
        if (destroySpeed == -1.0F) {
            return 0.0F;
        }
        float digSpeed = getToolDigSpeed(player, state, tool);
        int divisor = tool.isCorrectToolForDrops(state) ? 30 : 100;
        return digSpeed / destroySpeed / (float) divisor;
    }

    /**
     * 复制 {@code Player.getDigSpeed(BlockState, BlockPos)}，但使用指定工具栈。
     */
    private static float getToolDigSpeed(ServerPlayer player, BlockState state, ItemStack tool) {
        float f = tool.getDestroySpeed(state);
        if (f > 1.0F) {
            int efficiency = getEfficiencyLevel(tool);
            if (efficiency > 0 && !tool.isEmpty()) {
                f += (float) (efficiency * efficiency + 1);
            }
        }
        if (player.hasEffect(MobEffects.HASTE)) {
            f *= 1.0F + (float) (player.getEffect(MobEffects.HASTE).getAmplifier() + 1) * 0.2F;
        }
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            f *= 1.0F - (float) (player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier() + 1) * 0.2F;
        }
        return f;
    }

    /**
     * 获取给定工具栈的效率附魔等级。
     */
    private static int getEfficiencyLevel(ItemStack stack) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.EFFICIENCY)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    // ======================================================================
    //  水下速度惩罚
    // ======================================================================

    /**
     * 消除水下挖掘速度惩罚（{@code SUBMERGED_MINING_SPEED}），保留正面修饰。
     */
    static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        float adjusted = destroyStep;
        if (player.isEyeInFluid(FluidTags.WATER)) {
            double submergedMiningSpeed = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed > 0.0D && submergedMiningSpeed < 1.0D) {
                adjusted *= (float) (1.0D / submergedMiningSpeed);
            }
        }
        return adjusted;
    }
}

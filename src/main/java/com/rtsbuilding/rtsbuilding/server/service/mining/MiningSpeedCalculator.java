package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.block.material.Material;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.Potion;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

/** 1.12.2 远程采掘速度计算；临时上下文总在 finally 中恢复。 */
public final class MiningSpeedCalculator {
    private MiningSpeedCalculator() { }

    public static float computeRemoteDestroyStep(EntityPlayerMP player, BlockState state, BlockPos pos,
            int toolSlot, ItemStack linkedTool, boolean selectedToolRequested) {
        boolean oldGround = player.onGround;
        player.onGround = true;
        try {
            if (linkedTool != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(linkedTool)) {
                return removeMiningSpeedPenalty(player, computeDestroyStepForTool(player, state, pos, linkedTool));
            }
            if (selectedToolRequested) return 0.0F;
            int oldSlot = player.inventory.currentItem;
            player.inventory.currentItem = RtsMiningValidator.clampHotbarSlot(toolSlot);
            try {
                return removeMiningSpeedPenalty(player, state.getPlayerRelativeBlockHardness(player, player.worldObj, pos));
            } finally {
                player.inventory.currentItem = oldSlot;
            }
        } finally {
            player.onGround = oldGround;
        }
    }

    public static float computeDestroyStepForTool(EntityPlayerMP player, BlockState state,
            BlockPos pos, ItemStack tool) {
        float hardness = state.getBlockHardness(player.worldObj, pos);
        if (hardness < 0.0F) return 0.0F;
        float speed = getToolDigSpeed(player, state, tool);
        int divisor = net.minecraftforge.common.ForgeHooks.canToolHarvestBlock(
                state.getBlock(), state.getMetadata(), tool) ? 30 : 100;
        return speed / hardness / divisor;
    }

    private static float getToolDigSpeed(EntityPlayerMP player, BlockState state, ItemStack tool) {
        float speed = tool.getItem().getDigSpeed(tool, state.getBlock(), state.getMetadata());
        if (speed > 1.0F) {
            int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, tool);
            if (efficiency > 0) speed += efficiency * efficiency + 1;
        }
        PotionEffect haste = player.getActivePotionEffect(Potion.digSpeed);
        if (haste != null) speed *= 1.0F + (haste.getAmplifier() + 1) * 0.2F;
        PotionEffect fatigue = player.getActivePotionEffect(Potion.digSlowdown);
        if (fatigue != null) {
            int level = fatigue.getAmplifier();
            speed *= level == 0 ? 0.3F : level == 1 ? 0.09F : level == 2 ? 0.0027F : 0.00081F;
        }
        return speed;
    }

    /** RTS 取消水下五倍惩罚，但保留水下速掘附魔本身的效果。 */
    static float removeMiningSpeedPenalty(EntityPlayerMP player, float destroyStep) {
        if (destroyStep <= 0.0F) return destroyStep;
        if (player.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(player)) {
            return destroyStep * 5.0F;
        }
        return destroyStep;
    }
}

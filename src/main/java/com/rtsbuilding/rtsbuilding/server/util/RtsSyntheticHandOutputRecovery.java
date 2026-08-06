package com.rtsbuilding.rtsbuilding.server.util;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 保护“临时空手”交互期间由第三方方块直接放入玩家主手格的产物。
 *
 * <p>本类只服务于输入明确为 {@link ItemStack#EMPTY} 的代理交互，不处理工具、容器或链接存储
 * 物品的正常 remainder；后者必须由各自的真实来源事务负责归还。
 */
public final class RtsSyntheticHandOutputRecovery {
    private RtsSyntheticHandOutputRecovery() {
    }

    public static boolean hasOutput(TemporaryContextSwitcher.UseOnOutcome outcome) {
        return outcome != null && outcome.remainder() != null && !outcome.remainder().isEmpty();
    }

    /** 在临时位置与临时主手均已恢复后调用，必要时将产物交还玩家并把动作视为成功。 */
    public static EnumActionResult recoverToPlayer(
            EntityPlayerMP player, TemporaryContextSwitcher.UseOnOutcome outcome) {
        if (hasOutput(outcome)) {
            ItemHandlerHelper.giveItemToPlayer(player, outcome.remainder().copy());
            return EnumActionResult.SUCCESS;
        }
        return outcome == null ? EnumActionResult.PASS : outcome.result();
    }
}

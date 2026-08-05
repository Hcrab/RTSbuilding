package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.function.Predicate;

/**
 * 存储查询 API：在玩家的链接存储中计数、提取和查询物品。
 *
 * <p>附属模组可以使用此接口读取已链接存储的内容，
 * 而无需直接依赖 RTS 内部实现。
 */
public interface RtsStorageQueryAPI {

    /**
     * 计算玩家的链接存储中匹配谓词的物品总数。
     *
     * @param player    目标玩家
     * @param predicate 匹配条件
     * @return 匹配的物品总数，无存储时返回 0
     */
    long countItemsMatching(EntityPlayerMP player, Predicate<ItemStack> predicate);

    /**
     * 检查玩家是否可以访问指定坐标的方块目标。
     *
     * @param player 目标玩家
     * @param pos    目标坐标（使用 com.rtsbuilding.rtsbuilding.platform.math.BlockPos）
     * @return 如果在 RTS 相机范围内且可以交互则返回 true
     */
    boolean canAccessTarget(EntityPlayerMP player, BlockPos pos);
}

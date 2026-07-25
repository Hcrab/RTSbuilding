package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * 挖矿服务接口——管理 RTS 模式下的各种远程挖掘操作。
 *
 * <p>该接口定义了四种挖掘模式：
 * <ul>
 *   <li><b>单方块挖掘（{@link #mine}）</b>——逐个挖掘目标方块</li>
 *   <li><b>连锁挖掘（{@link #startUltimine}）</b>——挖掘相连的同类型方块</li>
 *   <li><b>范围挖掘（{@link #areaMine}）</b>——在指定区域内批量挖掘</li>
 *   <li><b>范围破坏（{@link #areaDestroy}）</b>——在指定范围内破坏所有方块</li>
 * </ul>
 * 所有挖掘操作均使用远程借用工具，支持工具保护模式。
 */
public interface MiningService {

    /**
     * 单方块挖掘——开始或结束挖掘指定的单个方块。
     *
     * @param player                 目标玩家
     * @param pos                    目标方块坐标
     * @param face                   点击的面
     * @param start                  是否为开始挖掘（{@code true}=开始, {@code false}=取消）
     * @param toolSlot               工具槽位编号
     * @param toolItemId             工具的物品 ID
     * @param toolPrototype          工具的原型物品栈
     * @param allowPlacedBlockRecovery 是否允许回收已放置的方块
     * @param toolProtectionEnabled  是否启用工具保护（防止工具损坏）
     */
    void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot,
              String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery,
              boolean toolProtectionEnabled);

    /**
     * 开始连锁挖掘（Ultimine）——挖掘与目标相连的所有同类型方块。
     *
     * @param player                目标玩家
     * @param pos                   起始方块坐标
     * @param face                  点击的面
     * @param toolSlot              工具槽位编号
     * @param toolItemId            工具的物品 ID
     * @param toolPrototype         工具的原型物品栈
     * @param requestedLimit        请求的最大挖掘数量
     * @param mode                  连锁模式（0=精确匹配, 1=模糊匹配等）
     * @param toolProtectionEnabled 是否启用工具保护
     */
    void startUltimine(ServerPlayer player, BlockPos pos, Direction face, byte toolSlot,
                       String toolItemId, ItemStack toolPrototype, int requestedLimit,
                       byte mode, boolean toolProtectionEnabled);

    /**
     * 范围挖掘（Area Mine）——在指定的三维区域范围内批量挖掘方块。
     *
     * @param player                目标玩家
     * @param minX,maxX,minY,maxY,minZ,maxZ 区域范围坐标
     * @param toolSlot              工具槽位编号
     * @param toolItemId            工具的物品 ID
     * @param toolPrototype         工具的原型物品栈
     * @param shapeType             区域形状类型
     * @param fillType              填充类型
     * @param toolProtectionEnabled 是否启用工具保护
     */
    void areaMine(ServerPlayer player, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                  byte toolSlot, String toolItemId, ItemStack toolPrototype,
                  byte shapeType, byte fillType, boolean toolProtectionEnabled);

    /**
     * 范围破坏（Area Destroy）——在指定的一组位置强制破坏所有方块。
     * 与范围挖掘不同，范围破坏不关心方块类型，破坏所有指定的方块。
     *
     * @param player                目标玩家
     * @param positions             要破坏的方块坐标列表
     * @param toolSlot              工具槽位编号
     * @param toolItemId            工具的物品 ID
     * @param toolPrototype         工具的原型物品栈
     * @param toolProtectionEnabled 是否启用工具保护
     */
    void areaDestroy(ServerPlayer player, List<BlockPos> positions, byte toolSlot,
                     String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled);

    /**
     * 获取当前范围破坏操作中的总方块数。
     *
     * @param player 目标玩家
     * @return 总方块数
     */
    int getAreaDestroyTotalBlocks(ServerPlayer player);

    /**
     * 获取当前范围破坏操作中已完成（已破坏）的方块数量。
     *
     * @param player 目标玩家
     * @return 已破坏方块数
     */
    int getAreaDestroyCompletedBlocks(ServerPlayer player);

    /**
     * 获取当前范围破坏操作中剩余的未破坏方块数。
     *
     * @param player 目标玩家
     * @return 未破坏方块数
     */
    int getAreaDestroyRemainingBlocks(ServerPlayer player);

    /**
     * 临时切换玩家的主手持物品为指定物品栈，执行操作后恢复原物品。
     * 用于在远程操作中临时借用工具或物品，操作完成后归还原位。
     *
     * @param player 目标玩家
     * @param stack  要临时切换到的物品栈
     * @param action 要执行的操作
     * @param <T>    操作返回值的类型
     * @return 操作执行后的返回结果
     */
    <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action);
}

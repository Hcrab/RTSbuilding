package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 远程挖掘 API??
 *
 * <p>管理 RTS 模式下的单方块挖掘、连锁挖掘（Ultimine）和区域挖掘??
 */
public interface RtsMiningAPI {

    /**
     * 开始或停止对单个方块的远程挖掘??
     *
     * @param player                    执行玩家
     * @param pos                       目标坐标（net.minecraft.core.BlockPos??
     * @param face                      挖掘方向
     * @param start                     true=开始挖掘，false=停止
     * @param toolSlot                  工具栏格索引
     * @param toolItemId                工具物品 ID
     * @param toolPrototype             工具原型
     * @param allowPlacedBlockRecovery  是否允许已放置方块恢??
     * @param toolProtectionEnabled     是否启用工具保护
     */
    void mine(ServerPlayer player, Object pos, Direction face, boolean start,
              byte toolSlot, String toolItemId, ItemStack toolPrototype,
              boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled);

    /**
     * 启动连锁挖掘??
     *
     * @param player               执行玩家
     * @param pos                  起始坐标（net.minecraft.core.BlockPos??
     * @param face                 挖掘方向
     * @param toolSlot             工具栏格索引
     * @param toolItemId           工具物品 ID
     * @param toolPrototype        工具原型
     * @param requestedLimit       请求挖掘数量上限
     * @param mode                 连锁模式
     * @param toolProtectionEnabled 是否启用工具保护
     */
    void startUltimine(ServerPlayer player, Object pos, Direction face,
                       byte toolSlot, String toolItemId, ItemStack toolPrototype,
                       int requestedLimit, byte mode, boolean toolProtectionEnabled);

    /**
     * 区域挖掘??
     */
    void areaMine(ServerPlayer player,
                  int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                  byte toolSlot, String toolItemId, ItemStack toolPrototype,
                  byte shapeType, byte fillType, boolean toolProtectionEnabled);

    /**
     * 区域破坏指定方块??
     */
    void areaDestroy(ServerPlayer player, List<Object> positions,
                     byte toolSlot, String toolItemId, ItemStack toolPrototype,
                     boolean toolProtectionEnabled);
}

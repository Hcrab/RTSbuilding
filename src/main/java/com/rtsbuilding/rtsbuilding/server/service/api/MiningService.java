package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * 挖矿服务接口——管理单方块挖掘、连锁挖掘、范围挖掘和范围破坏。
 */
public interface MiningService {

    /** 单方块挖掘。 */
    void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot,
              String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery,
              boolean toolProtectionEnabled);

    /** 连锁挖掘（Ultimine）。 */
    void startUltimine(ServerPlayer player, BlockPos pos, Direction face, byte toolSlot,
                       String toolItemId, ItemStack toolPrototype, int requestedLimit,
                       byte mode, boolean toolProtectionEnabled);

    /** 范围挖掘（Area Mine）。 */
    void areaMine(ServerPlayer player, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                  byte toolSlot, String toolItemId, ItemStack toolPrototype,
                  byte shapeType, byte fillType, boolean toolProtectionEnabled);

    /** 范围破坏（Area Destroy）。 */
    void areaDestroy(ServerPlayer player, List<BlockPos> positions, byte toolSlot,
                     String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled);

    /** 获取当前范围破坏的总方块数。 */
    int getAreaDestroyTotalBlocks(ServerPlayer player);

    /** 获取当前范围破坏的已破坏方块数量。 */
    int getAreaDestroyCompletedBlocks(ServerPlayer player);

    /** 获取当前范围破坏的未破坏方块数。 */
    int getAreaDestroyRemainingBlocks(ServerPlayer player);

    /** 临时切换主手持物品执行操作。 */
    <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action);
}

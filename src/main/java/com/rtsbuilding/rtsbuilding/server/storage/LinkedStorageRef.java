package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 已链接存储方块的<strong>稳定身份标识</strong>。
 *
 * <p>以 {@code (维度, 坐标)} 为复合键，确保不同维度相同坐标的方块身份独立。
 * 本 record 仅包含身份信息——权限检查、显示名和 Capability 查询属于外部服务职责。
 *
 * @param dimension 方块所在的维度键
 * @param pos       方块的世界坐标
 */
public record LinkedStorageRef(ResourceKey<Level> dimension, BlockPos pos) {
}

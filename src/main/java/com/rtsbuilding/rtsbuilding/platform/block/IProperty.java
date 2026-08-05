package com.rtsbuilding.rtsbuilding.platform.block;

import java.util.Collection;

import com.google.common.base.Optional;

import net.minecraft.block.Block;

/**
 * 共享层可见的方块属性契约。
 *
 * <p>1.7.10 没有原版 BlockState 属性系统；具体属性由 GTNH/模组适配器把名称和值映射到
 * metadata 或 TileEntity。没有适配器的属性不会猜测写入，避免损坏机器朝向。</p>
 */
public interface IProperty<T extends Comparable<T>> {
    String getName();

    Collection<T> getAllowedValues();

    Class<T> getValueClass();

    Optional<T> parseValue(String value);

    String getName(T value);

    T read(Block block, int metadata);

    int write(Block block, int metadata, T value);
}

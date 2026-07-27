package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * 已链接存储方块的<strong>稳定身份标识</strong>。
 *
 * <p>以 {@code (维度, 坐标)} 为复合键，确保不同维度相同坐标的方块身份独立。
 * 本 record 仅包含身份信息——权限检查、显示名和 Capability 查询属于外部服务职责。
 *
 * @param dimension 方块所在的维度键
 * @param pos       方块的世界坐标
 */
public final class LinkedStorageRef {
    private final int dimension;
    private final BlockPos pos;

    public LinkedStorageRef(int dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = Objects.requireNonNull(pos, "pos").toImmutable();
    }

    public int dimension() { return dimension; }
    public BlockPos pos() { return pos; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LinkedStorageRef)) return false;
        LinkedStorageRef that = (LinkedStorageRef) other;
        return dimension == that.dimension && pos.equals(that.pos);
    }
    @Override public int hashCode() { return 31 * dimension + pos.hashCode(); }
    @Override public String toString() { return "LinkedStorageRef[dimension=" + dimension + ", pos=" + pos + "]"; }
}

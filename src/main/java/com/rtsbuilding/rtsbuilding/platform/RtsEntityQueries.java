package com.rtsbuilding.rtsbuilding.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * 为 1.19.2 提供结果集合有硬上限的实体查询。
 *
 * <p>主线 API 可以让查询方法直接把结果写入带上限的集合；1.19.2 的便捷重载会先
 * 构造完整列表，密集掉落场景可能产生无界临时分配。本适配器改用实体访问器回调，
 * 只保留前 {@code limit} 个匹配结果。底层旧版 API 仍可能遍历相交区段，但不会把
 * 预算外实体收集进内存，也不负责排序、加载区块或改变实体生命周期。</p>
 */
public final class RtsEntityQueries {
    private RtsEntityQueries() {
    }

    public static <T extends Entity> java.util.List<T> getEntities(ServerLevel level,
            EntityTypeTest<Entity, T> type, AABB bounds, Predicate<? super T> predicate, int limit) {
        if (level == null || limit <= 0) {
            return java.util.List.of();
        }
        ArrayList<T> found = new ArrayList<>(Math.min(limit, 64));
        level.getEntities().get(type, bounds, entity -> {
            if (found.size() < limit && predicate.test(entity)) {
                found.add(entity);
            }
        });
        return found;
    }
}

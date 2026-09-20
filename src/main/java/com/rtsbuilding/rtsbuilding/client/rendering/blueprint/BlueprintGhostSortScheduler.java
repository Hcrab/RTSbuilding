package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * 透明批次的有限轮转调度器。
 *
 * <p>它只决定本帧检查哪些批次，不持有 GL 资源，也不改变批次内容。相机相对某批次
 * 移动达到平方距离 1 后才调用排序回调；游标跨帧递增，避免大蓝图永远只更新前几个
 * 批次。时钟可注入以便测试预算边界，生产使用 System.nanoTime。</p>
 */
final class BlueprintGhostSortScheduler<T> {
    static final double SORT_DISTANCE_SQR = 1.0D;
    static final long SORT_BUDGET_NANOS = 1_000_000L;

    private final LongSupplier clock;
    private int cursor;

    BlueprintGhostSortScheduler() {
        this(System::nanoTime);
    }

    BlueprintGhostSortScheduler(LongSupplier clock) {
        this.clock = clock;
    }

    void schedule(List<T> batches, Vec3 camera,
            ToDoubleFunction<T> distanceFromCamera,
            Predicate<T> eligible,
            Consumer<T> resort) {
        Objects.requireNonNull(camera, "camera");
        if (batches.isEmpty()) {
            cursor = 0;
            return;
        }
        cursor = Math.floorMod(cursor, batches.size());
        long started = clock.getAsLong();
        for (int checked = 0; checked < batches.size(); checked++) {
            T batch = batches.get(cursor);
            cursor = (cursor + 1) % batches.size();
            if (eligible.test(batch)
                    && distanceFromCamera.applyAsDouble(batch) >= SORT_DISTANCE_SQR) {
                resort.accept(batch);
            }
            if (clock.getAsLong() - started >= SORT_BUDGET_NANOS) {
                break;
            }
        }
    }

    int cursor() {
        return cursor;
    }

    void reset() {
        cursor = 0;
    }
}

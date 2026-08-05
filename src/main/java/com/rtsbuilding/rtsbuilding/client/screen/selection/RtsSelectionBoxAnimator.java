package com.rtsbuilding.rtsbuilding.client.screen.selection;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;

import java.util.function.LongSupplier;

/**
 * 世界空间盒子编辑器的视觉补间状态。
 *
 * <p>它只负责把整数方块盒子的旧 AABB 平滑过渡到新 AABB，不参与真实命中、剔除、蓝图保存或服务端同步。
 * 范围剔除和蓝图框选都通过这一层获得相同的拖拽手感，同时各自仍然保留自己的业务状态和颜色渲染。</p>
 */
public final class RtsSelectionBoxAnimator {
    private static final long DEFAULT_DURATION_MS = 90L;

    private final long durationMs;
    private final LongSupplier clock;
    private int animatedBoxId = -1;
    private AxisAlignedBB animatedStartAabb;
    private AxisAlignedBB animatedEndAabb;
    private long animatedStartMillis;

    public RtsSelectionBoxAnimator() {
        this(DEFAULT_DURATION_MS, System::currentTimeMillis);
    }

    RtsSelectionBoxAnimator(long durationMs) {
        this(durationMs, System::currentTimeMillis);
    }

    RtsSelectionBoxAnimator(long durationMs, LongSupplier clock) {
        this.durationMs = Math.max(1L, durationMs);
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public AxisAlignedBB renderAabb(RtsCullingBox box) {
        if (box == null) {
            return null;
        }
        long now = this.clock.getAsLong();
        AxisAlignedBB target = box.asAabb();
        if (box.id() != animatedBoxId || animatedStartAabb == null || animatedEndAabb == null) {
            animatedBoxId = box.id();
            animatedStartAabb = target;
            animatedEndAabb = target;
            animatedStartMillis = now;
            return target;
        }
        if (!animatedEndAabb.equals(target)) {
            animatedStartAabb = currentAnimatedAabb(now);
            animatedEndAabb = target;
            animatedStartMillis = now;
        }
        double raw = MathHelper.clamp((double) (now - animatedStartMillis) / (double) durationMs, 0.0D, 1.0D);
        if (raw >= 1.0D) {
            animatedStartAabb = target;
            animatedEndAabb = target;
            animatedStartMillis = now;
            return target;
        }
        return lerpAabb(animatedStartAabb, animatedEndAabb, easeOutCubic(raw));
    }

    public void animate(RtsCullingBox from, RtsCullingBox to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        long now = this.clock.getAsLong();
        AxisAlignedBB visualStart = from.id() == animatedBoxId && animatedStartAabb != null && animatedEndAabb != null
                ? currentAnimatedAabb(now)
                : from.asAabb();
        animatedBoxId = to.id();
        animatedStartAabb = visualStart;
        animatedEndAabb = to.asAabb();
        animatedStartMillis = now;
    }

    public void clearIfBox(int id) {
        if (animatedBoxId == id) {
            clear();
        }
    }

    public void clear() {
        animatedBoxId = -1;
        animatedStartAabb = null;
        animatedEndAabb = null;
        animatedStartMillis = 0L;
    }

    private AxisAlignedBB currentAnimatedAabb(long now) {
        double raw = MathHelper.clamp((double) (now - animatedStartMillis) / (double) durationMs, 0.0D, 1.0D);
        return lerpAabb(animatedStartAabb, animatedEndAabb, easeOutCubic(raw));
    }

    private static double easeOutCubic(double amount) {
        return 1.0D - Math.pow(1.0D - amount, 3.0D);
    }

    private static AxisAlignedBB lerpAabb(AxisAlignedBB from, AxisAlignedBB to, double amount) {
        return new AxisAlignedBB(
                lerp(from.minX, to.minX, amount),
                lerp(from.minY, to.minY, amount),
                lerp(from.minZ, to.minZ, amount),
                lerp(from.maxX, to.maxX, amount),
                lerp(from.maxY, to.maxY, amount),
                lerp(from.maxZ, to.maxZ, amount));
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
